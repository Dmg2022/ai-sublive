package com.example.model

data class SttResult(
    val text: String,
    val isFinal: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)
