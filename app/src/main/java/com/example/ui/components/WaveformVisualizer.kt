package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.IndigoPrimary
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    rms: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 18
) {
    val normalizedRms = (rms / 600f).coerceIn(0.08f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val animatedHeight = remember { Animatable(4.dp.value) }

            LaunchedEffect(rms, isListening) {
                if (isListening && rms > 50f) {
                    val factor = 0.4f + 0.6f * kotlin.math.sin((index.toDouble() / barCount) * Math.PI).toFloat()
                    val target = (normalizedRms * 32f * factor + Random.nextFloat() * 6f).coerceIn(4f, 34f)
                    animatedHeight.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = 100)
                    )
                } else {
                    animatedHeight.animateTo(
                        targetValue = 4f,
                        animationSpec = tween(durationMillis = 200)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight.value.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(CyanAccent, IndigoPrimary)
                        )
                    )
            )
        }
    }
}
