package com.example.engine

import android.util.Log
import com.example.model.SttResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Offline Vosk Speech Recognition Engine implementation.
 * Processes 16 kHz 16-bit PCM streaming audio and emits real-time partial and final results.
 */
class VoskEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SpeechRecognizerEngine {

    companion object {
        private const val TAG = "VoskEngine"
        private const val SAMPLE_RATE = 16000.0f
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var lastEmittedPartial: String = ""

    private val _results = MutableSharedFlow<SttResult>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val results: Flow<SttResult> = _results.asSharedFlow()

    init {
        try {
            org.vosk.LibVosk.setLogLevel(org.vosk.LogLevel.WARNINGS)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to configure LibVosk log level: ${e.message}")
        }
    }

    override suspend fun start(language: String, modelPath: String): Unit = withContext(ioDispatcher) {
        stop() // Ensure clean previous state
        try {
            Log.i(TAG, "Loading Vosk Model from path: $modelPath")
            val loadedModel = Model(modelPath)
            val loadedRecognizer = Recognizer(loadedModel, SAMPLE_RATE).apply {
                setWords(true)
            }
            model = loadedModel
            recognizer = loadedRecognizer
            lastEmittedPartial = ""
            Log.i(TAG, "Vosk Recognizer successfully started for language: $language")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk model: ${e.message}", e)
            throw e
        }
        Unit
    }

    override fun accept(pcm: ShortArray) {
        val rec = recognizer ?: return
        try {
            val isFinal = rec.acceptWaveForm(pcm, pcm.size)
            if (isFinal) {
                val jsonStr = rec.result
                val text = parseTextFromJson(jsonStr, "text")
                if (text.isNotBlank()) {
                    _results.tryEmit(SttResult(text = text, isFinal = true))
                    lastEmittedPartial = ""
                }
            } else {
                val jsonStr = rec.partialResult
                val partial = parseTextFromJson(jsonStr, "partial")
                if (partial.isNotBlank() && partial != lastEmittedPartial) {
                    lastEmittedPartial = partial
                    _results.tryEmit(SttResult(text = partial, isFinal = false))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing PCM audio buffer in Vosk: ${e.message}", e)
        }
    }

    override fun stop() {
        try {
            recognizer?.let { rec ->
                val finalJson = rec.finalResult
                val text = parseTextFromJson(finalJson, "text")
                if (text.isNotBlank()) {
                    _results.tryEmit(SttResult(text = text, isFinal = true))
                }
                rec.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Vosk Recognizer: ${e.message}")
        } finally {
            recognizer = null
        }

        try {
            model?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Vosk Model: ${e.message}")
        } finally {
            model = null
        }
    }

    private fun parseTextFromJson(json: String, key: String): String {
        return try {
            JSONObject(json).optString(key, "").trim()
        } catch (e: Exception) {
            ""
        }
    }
}
