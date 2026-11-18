package com.azzam.sadeeqi.avatar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Sadeeqi voice identity: a softly animated golden orb, inspired by the
 * circular voice indicator. The orb is a visual state indicator, not a 3D
 * avatar or a speech waveform.
 */
@Composable
fun Avatar3DScreen(isSpeaking: Boolean = false, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sadeeqi-golden-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = if (isSpeaking) 1.12f else 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSpeaking) 360 else 2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-pulse"
    )
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSpeaking) 1800 else 9000),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb-shimmer"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(292.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.39f * pulse

                // Soft golden aura around the orb.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x66FFD76A),
                            Color(0x22D99A24),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 1.75f
                    ),
                    radius = radius * 1.75f,
                    center = center
                )

                // Pearlescent gold sphere with a bright upper-left highlight.
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFFFFF4C2),
                            0.20f to Color(0xFFFFD978),
                            0.52f to Color(0xFFE5A92F),
                            0.82f to Color(0xFF9B5C12),
                            1.0f to Color(0xFF4B2B0B)
                        ),
                        center = Offset(center.x - radius * 0.34f, center.y - radius * 0.42f),
                        radius = radius * 1.75f
                    ),
                    radius = radius,
                    center = center
                )

                // Slow luminous sweep, giving the orb a living voice-assistant feel.
                drawArc(
                    color = Color(0x99FFF0B0),
                    startAngle = shimmer,
                    sweepAngle = 92f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.91f, center.y - radius * 0.91f),
                    size = androidx.compose.ui.geometry.Size(radius * 1.82f, radius * 1.82f),
                    style = Stroke(width = size.minDimension * 0.012f, cap = StrokeCap.Round)
                )
                drawCircle(
                    color = Color(0x55FFF2C4),
                    radius = radius * 0.88f,
                    center = center,
                    style = Stroke(width = size.minDimension * 0.004f)
                )
            }
        }
        Text(
            text = if (isSpeaking) "صديقي يتحدث…" else "صديقي",
            color = Color(0xFFE8BD62),
            style = MaterialTheme.typography.titleLarge
        )
    }
}
