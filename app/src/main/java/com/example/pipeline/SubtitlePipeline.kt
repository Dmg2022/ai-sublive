package com.example.pipeline

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.capture.AudioCaptureSource
import com.example.capture.VadDetector
import com.example.data.AppSettings
import com.example.data.SrtExporter
import com.example.engine.SpeechRecognizerEngine
import com.example.model.PipelineStatus
import com.example.model.SubtitleState
import com.example.translation.TranslationManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.Q)
class SubtitlePipeline(
    private val context: Context,
    private val audioCaptureSource: AudioCaptureSource,
    private val recognizerEngine: SpeechRecognizerEngine,
    private val translationManager: TranslationManager,
    private val srtExporter: SrtExporter,
    private val coroutineScope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        private const val TAG = "SubtitlePipeline"
        private const val SILENCE_TIMEOUT_MS = 4000L // Clear subtitle after 4s silence
    }

    private val vadDetector = VadDetector()
    private val isPaused = AtomicBoolean(false)
    private var lastAudioActivityTimeMs = System.currentTimeMillis()

    private var captureJob: Job? = null
    private var sttCollectJob: Job? = null
    private var silenceCheckJob: Job? = null

    private var currentSettings = AppSettings()

    private val _subtitleState = MutableStateFlow(SubtitleState())
    val subtitleState: StateFlow<SubtitleState> = _subtitleState.asStateFlow()

    fun updateSettings(settings: AppSettings) {
        currentSettings = settings
        vadDetector.sensitivityThresholdRms = settings.vadSensitivityRms
    }

    fun start() {
        Log.i(TAG, "Starting Subtitle Pipeline")
        isPaused.set(false)
        lastAudioActivityTimeMs = System.currentTimeMillis()

        if (currentSettings.saveSrtHistory) {
            srtExporter.startSession()
        }

        _subtitleState.value = SubtitleState(
            status = PipelineStatus.LISTENING
        )

        // 1. Launch Audio Record capture loop
        captureJob = coroutineScope.launch(Dispatchers.IO) {
            audioCaptureSource.startCapture()
        }

        // 2. Consume Audio flow -> VAD -> STT
        coroutineScope.launch(defaultDispatcher) {
            audioCaptureSource.audioFlow.collect { pcm ->
                if (isPaused.get()) return@collect

                val (isVoice, rms) = vadDetector.isSpeech(pcm, pcm.size)

                // Update audio RMS for UI waveform visualizer
                _subtitleState.value = _subtitleState.value.copy(audioRms = rms)

                if (isVoice) {
                    lastAudioActivityTimeMs = System.currentTimeMillis()
                    if (_subtitleState.value.status == PipelineStatus.SILENCE) {
                        _subtitleState.value = _subtitleState.value.copy(status = PipelineStatus.LISTENING)
                    }
                    recognizerEngine.accept(pcm)
                }
            }
        }

        // 3. Consume STT Results -> Translation -> StateFlow
        sttCollectJob = coroutineScope.launch(defaultDispatcher) {
            recognizerEngine.results.collect { stt ->
                if (isPaused.get()) return@collect

                lastAudioActivityTimeMs = System.currentTimeMillis()

                if (stt.isFinal) {
                    _subtitleState.value = _subtitleState.value.copy(
                        originalText = stt.text,
                        isFinal = true,
                        status = PipelineStatus.TRANSLATING
                    )

                    val translated = translationManager.translate(
                        text = stt.text,
                        isFinal = true,
                        allowPartial = false
                    ) ?: stt.text

                    _subtitleState.value = _subtitleState.value.copy(
                        translatedText = translated,
                        isFinal = true,
                        status = PipelineStatus.LISTENING
                    )

                    if (currentSettings.saveSrtHistory) {
                        srtExporter.addSubtitle(stt.text, translated)
                    }
                } else {
                    // Partial result
                    val currentTrans = _subtitleState.value.translatedText
                    val newPartialTrans = if (currentSettings.translatePartial) {
                        translationManager.translate(
                            text = stt.text,
                            isFinal = false,
                            allowPartial = true
                        ) ?: currentTrans
                    } else {
                        currentTrans
                    }

                    _subtitleState.value = _subtitleState.value.copy(
                        originalText = stt.text,
                        translatedText = newPartialTrans,
                        isFinal = false,
                        status = PipelineStatus.LISTENING
                    )
                }
            }
        }

        // 4. Silence Watchdog: Auto-clear subtitles after 4 seconds of silence
        silenceCheckJob = coroutineScope.launch(defaultDispatcher) {
            while (isActive) {
                delay(1000)
                if (!isPaused.get()) {
                    val silenceDuration = System.currentTimeMillis() - lastAudioActivityTimeMs
                    if (silenceDuration > SILENCE_TIMEOUT_MS) {
                        val current = _subtitleState.value
                        if (current.originalText.isNotEmpty() || current.translatedText.isNotEmpty()) {
                            _subtitleState.value = current.copy(
                                originalText = "",
                                translatedText = "",
                                status = PipelineStatus.SILENCE,
                                audioRms = 0f
                            )
                        }
                    }
                }
            }
        }
    }

    fun pause() {
        isPaused.set(true)
        _subtitleState.value = _subtitleState.value.copy(
            status = PipelineStatus.PAUSED,
            audioRms = 0f
        )
        Log.i(TAG, "Subtitle Pipeline paused")
    }

    fun resume() {
        isPaused.set(false)
        lastAudioActivityTimeMs = System.currentTimeMillis()
        _subtitleState.value = _subtitleState.value.copy(
            status = PipelineStatus.LISTENING
        )
        Log.i(TAG, "Subtitle Pipeline resumed")
    }

    fun stop() {
        Log.i(TAG, "Stopping Subtitle Pipeline")
        captureJob?.cancel()
        sttCollectJob?.cancel()
        silenceCheckJob?.cancel()

        audioCaptureSource.stopCapture()
        recognizerEngine.stop()
        translationManager.close()

        if (currentSettings.saveSrtHistory) {
            srtExporter.finishAndExport()
        }

        _subtitleState.value = SubtitleState(
            status = PipelineStatus.IDLE,
            audioRms = 0f
        )
    }
}
