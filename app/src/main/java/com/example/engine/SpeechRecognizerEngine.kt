package com.example.engine

import com.example.model.SttResult
import kotlinx.coroutines.flow.Flow

/**
 * Pluggable Speech-to-Text Recognizer Engine Interface.
 * Allows switching between offline Vosk, on-device Whisper.cpp, or cloud engines
 * without altering downstream pipeline or UI components.
 */
interface SpeechRecognizerEngine {
    val results: Flow<SttResult>
    suspend fun start(language: String, modelPath: String)
    fun accept(pcm: ShortArray)
    fun stop()
}
