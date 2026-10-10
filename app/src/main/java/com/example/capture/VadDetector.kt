package com.example.capture

import kotlin.math.sqrt

class VadDetector(var sensitivityThresholdRms: Float = 180f) {

    /**
     * Calculates the Root Mean Square (RMS) energy of a 16-bit PCM audio buffer.
     * Higher RMS indicates louder sound / speech activity.
     */
    fun calculateRms(buffer: ShortArray, readSize: Int): Float {
        if (readSize <= 0) return 0f
        var sumSquares = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
        }
        val meanSquare = sumSquares / readSize
        return sqrt(meanSquare).toFloat()
    }

    /**
     * Returns true if audio RMS exceeds the threshold (indicating speech/sound).
     */
    fun isSpeech(buffer: ShortArray, readSize: Int): Pair<Boolean, Float> {
        val rms = calculateRms(buffer, readSize)
        val isVoice = rms >= sensitivityThresholdRms
        return Pair(isVoice, rms)
    }
}
