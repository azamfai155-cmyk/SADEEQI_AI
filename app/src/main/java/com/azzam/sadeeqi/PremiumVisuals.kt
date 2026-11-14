package com.azzam.sadeeqi

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private val CoreGold = Color(0xFFE8C98A)
private val CoreSilver = Color(0xFFC5CDD5)

@Composable
fun PremiumCore(
    modifier: Modifier = Modifier,
    stateLabel: String = "مستعد لمساعدتك"
) {
    val transition = rememberInfiniteTransition(label = "sadeeqi-core")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier.size(248.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(248.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val base = size.minDimension * 0.34f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x443D3020), Color(0x22201810), Color.Transparent),
                        center = c,
                        radius = size.minDimension * 0.49f
                    ),
                    radius = size.minDimension * 0.49f,
                    center = c
                )
                drawCircle(
                    color = CoreGold.copy(alpha = 0.22f),
                    radius = base * pulse,
                    center = c,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = CoreSilver.copy(alpha = 0.36f),
                    radius = base * 1.22f,
                    center = c,
                    style = Stroke(width = 0.8.dp.toPx())
                )
                drawCircle(
                    color = CoreGold.copy(alpha = 0.45f),
                    radius = base * 1.43f,
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
                for (i in 0 until 3) {
                    val angle = Math.toRadians((rotation + i * 120.0))
                    val radius = base * (1.16f + i * 0.12f)
                    val point = Offset(
                        c.x + cos(angle).toFloat() * radius,
                        c.y + sin(angle).toFloat() * radius * 0.56f
                    )
                    drawCircle(
                        color = if (i % 2 == 0) CoreGold else CoreSilver,
                        radius = 3.5.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = CoreGold.copy(alpha = 0.18f),
                        radius = 9.dp.toPx(),
                        center = point
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFAE6B7), Color(0xFFB88743), Color(0xFF43311C), Color(0xFF11151B)),
                        center = Offset(c.x - base * 0.24f, c.y - base * 0.28f),
                        radius = base * 1.05f
                    ),
                    radius = base * pulse,
                    center = c
                )
                drawCircle(
                    color = Color(0x99FFF0CF),
                    radius = base * 0.73f,
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = Color(0x66FFFFFF),
                    radius = base * 0.52f,
                    center = Offset(c.x - base * 0.12f, c.y - base * 0.12f),
                    style = Stroke(width = 1.dp.toPx())
                )
                for (i in 0 until 22) {
                    val a = Math.toRadians((i * 137.5 + rotation * 0.22))
                    val r = base * (1.55f + (i % 4) * 0.13f)
                    val p = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
                    drawCircle(
                        color = if (i % 3 == 0) CoreGold.copy(alpha = 0.65f) else CoreSilver.copy(alpha = 0.35f),
                        radius = (1 + (i % 3) * 0.35f).dp.toPx(),
                        center = p
                    )
                }
            }
            Text(
                text = "S",
                color = Color(0xFFFFE9BE),
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
        Text("صَديقك الذكي", color = Color(0xFFF5F1E8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(stateLabel, color = CoreGold, fontSize = 12.sp, letterSpacing = 0.5.sp)
    }
}
