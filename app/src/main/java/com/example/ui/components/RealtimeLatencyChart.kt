package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.LatencySample
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PurpleAccent
import kotlin.math.max

@Composable
fun RealtimeLatencyChart(
    samples: List<LatencySample>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(12.dp)
        ) {
            val width = size.width
            val height = size.height

            // Grid lines
            val gridColor = Color.White.copy(alpha = 0.08f)
            for (i in 1..3) {
                val y = (height / 4f) * i
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (samples.isEmpty()) return@Canvas

            val validLatencies = samples.map { it.latencyMs }.filter { it > 0f }
            val maxLatency = max(validLatencies.maxOrNull() ?: 50f, 60f)
            val stepX = width / max(1, samples.size - 1)

            val path = Path()

            samples.forEachIndexed { index, sample ->
                val x = index * stepX
                val normalizedY = (1f - (sample.latencyMs / maxLatency).coerceIn(0f, 1f)) * (height - 12f) + 6f

                if (index == 0) {
                    path.moveTo(x, normalizedY)
                } else {
                    val prevX = (index - 1) * stepX
                    val prevSample = samples[index - 1]
                    val prevY = (1f - (prevSample.latencyMs / maxLatency).coerceIn(0f, 1f)) * (height - 12f) + 6f
                    val cx = (prevX + x) / 2f
                    path.cubicTo(cx, prevY, cx, normalizedY, x, normalizedY)
                }

                // Dot for current/last point
                if (index == samples.size - 1 && sample.latencyMs > 0f) {
                    drawCircle(
                        color = CyanAccent,
                        radius = 4.dp.toPx(),
                        center = Offset(x, normalizedY)
                    )
                }
            }

            drawPath(
                path = path,
                color = PurpleAccent,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
    }
}
