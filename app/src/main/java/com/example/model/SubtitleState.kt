package com.example.model

enum class PipelineStatus {
    IDLE,
    INITIALIZING,
    LISTENING,
    TRANSLATING,
    SILENCE,
    PAUSED,
    ERROR
}

data class SubtitleState(
    val originalText: String = "",
    val translatedText: String = "",
    val isFinal: Boolean = true,
    val status: PipelineStatus = PipelineStatus.IDLE,
    val audioRms: Float = 0f,
    val errorMessage: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
