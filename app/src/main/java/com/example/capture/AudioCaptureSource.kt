package com.example.capture

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioCaptureSource captures system audio playback using Android 10+ AudioPlaybackCapture API.
 * Preferred format: 16000 Hz, 16-bit PCM, Mono.
 * If 16 kHz mono is unsupported by device hardware, captures at native rate (48 kHz/44.1 kHz stereo)
 * and resamples/downmixes to 16 kHz mono.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AudioCaptureSource(
    private val mediaProjection: MediaProjection,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "AudioCaptureSource"
        const val TARGET_SAMPLE_RATE = 16000
        const val CHUNK_DURATION_MS = 100 // ~100ms per audio chunk
        const val SAMPLES_PER_CHUNK = TARGET_SAMPLE_RATE * CHUNK_DURATION_MS / 1000 // 1600 samples
    }

    private var audioRecord: AudioRecord? = null
    private val isCapturing = AtomicBoolean(false)
    private var actualSampleRate = TARGET_SAMPLE_RATE
    private var isStereo = false

    // Bounded shared flow dropping oldest buffer if consumer lags, preventing OOM
    private val _audioFlow = MutableSharedFlow<ShortArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioFlow: Flow<ShortArray> = _audioFlow.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun initialize(): Boolean {
        try {
            val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            // 1. Try preferred configuration: 16000 Hz, MONO, 16-bit PCM
            if (tryInitRecord(playbackConfig, TARGET_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO)) {
                actualSampleRate = TARGET_SAMPLE_RATE
                isStereo = false
                Log.i(TAG, "AudioRecord initialized directly with 16000Hz Mono")
                return true
            }

            // 2. Fallback to 48000 Hz Stereo (common hardware rate)
            if (tryInitRecord(playbackConfig, 48000, AudioFormat.CHANNEL_IN_STEREO)) {
                actualSampleRate = 48000
                isStereo = true
                Log.i(TAG, "AudioRecord fallback initialized with 48000Hz Stereo (resampling enabled)")
                return true
            }

            // 3. Fallback to 44100 Hz Stereo
            if (tryInitRecord(playbackConfig, 44100, AudioFormat.CHANNEL_IN_STEREO)) {
                actualSampleRate = 44100
                isStereo = true
                Log.i(TAG, "AudioRecord fallback initialized with 44100Hz Stereo (resampling enabled)")
                return true
            }

            Log.e(TAG, "Failed to initialize AudioRecord with any supported configuration")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioPlaybackCapture: ${e.message}", e)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryInitRecord(
        config: AudioPlaybackCaptureConfiguration,
        sampleRate: Int,
        channelMask: Int
    ): Boolean {
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferSize <= 0) return false

            val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build()

            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (record.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord = record
                return true
            } else {
                record.release()
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryInitRecord failed for $sampleRate Hz: ${e.message}")
            return false
        }
    }

    suspend fun startCapture() = withContext(ioDispatcher) {
        val record = audioRecord ?: run {
            Log.e(TAG, "Cannot start capture: AudioRecord is null")
            return@withContext
        }

        try {
            record.startRecording()
            isCapturing.set(true)
            Log.i(TAG, "Audio capture loop started")

            if (actualSampleRate == TARGET_SAMPLE_RATE && !isStereo) {
                captureNative16k(record)
            } else {
                captureWithResample(record)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio capture loop: ${e.message}", e)
        } finally {
            stopCapture()
        }
    }

    private suspend fun captureNative16k(record: AudioRecord) {
        val buffer = ShortArray(SAMPLES_PER_CHUNK)
        while (isCapturing.get()) {
            val readSamples = record.read(buffer, 0, buffer.size)
            if (readSamples > 0) {
                val chunk = if (readSamples == buffer.size) {
                    buffer.copyOf()
                } else {
                    buffer.copyOfRange(0, readSamples)
                }
                _audioFlow.emit(chunk)
            } else if (readSamples < 0) {
                Log.w(TAG, "AudioRecord read error: $readSamples")
                break
            }
        }
    }

    private suspend fun captureWithResample(record: AudioRecord) {
        val channels = if (isStereo) 2 else 1
        val rawChunkSize = (actualSampleRate * CHUNK_DURATION_MS / 1000) * channels
        val rawBuffer = ShortArray(rawChunkSize)

        while (isCapturing.get()) {
            val read = record.read(rawBuffer, 0, rawBuffer.size)
            if (read > 0) {
                // Step 1: Downmix to mono if stereo
                val monoSamples = if (isStereo) {
                    val frameCount = read / 2
                    ShortArray(frameCount) { i ->
                        val left = rawBuffer[i * 2].toInt()
                        val right = rawBuffer[i * 2 + 1].toInt()
                        ((left + right) / 2).toShort()
                    }
                } else {
                    rawBuffer.copyOfRange(0, read)
                }

                // Step 2: Resample from actualSampleRate to 16000 Hz using linear interpolation
                val resampled = resampleLinear(monoSamples, actualSampleRate, TARGET_SAMPLE_RATE)
                _audioFlow.emit(resampled)
            } else if (read < 0) {
                Log.w(TAG, "AudioRecord read error during resample: $read")
                break
            }
        }
    }

    /**
     * Resamples 16-bit PCM mono audio using linear interpolation.
     */
    private fun resampleLinear(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate || input.isEmpty()) return input

        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLength = (input.size / ratio).toInt()
        val output = ShortArray(outLength)

        for (i in 0 until outLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val fraction = (srcPos - srcIndex).toFloat()

            if (srcIndex + 1 < input.size) {
                val s0 = input[srcIndex].toFloat()
                val s1 = input[srcIndex + 1].toFloat()
                output[i] = (s0 + fraction * (s1 - s0)).toInt().coerceIn(-32768, 32767).toShort()
            } else if (srcIndex < input.size) {
                output[i] = input[srcIndex]
            }
        }
        return output
    }

    fun stopCapture() {
        if (isCapturing.compareAndSet(true, false)) {
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                Log.i(TAG, "AudioRecord stopped and released")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioRecord: ${e.message}", e)
            }
        }
    }
}
