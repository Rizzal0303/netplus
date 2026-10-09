package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GreenAccent
import kotlin.math.max

@Composable
fun ThroughputLiveChart(
    history: List<Float>,
    isUpload: Boolean = false,
    modifier: Modifier = Modifier
) {
    val strokeColor = if (isUpload) GreenAccent else CyanAccent
    val fillColor = strokeColor.copy(alpha = 0.2f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (history.isEmpty()) return@Canvas

            val width = size.width
            val height = size.height

            val maxVal = max(history.maxOrNull() ?: 1f, 10f)
            val stepX = width / max(1, history.size - 1)

            val strokePath = Path()
            val fillPath = Path()

            fillPath.moveTo(0f, height)

            history.forEachIndexed { index, value ->
                val x = index * stepX
                val normalizedY = (1f - (value / maxVal).coerceIn(0f, 1f)) * (height - 8f) + 4f

                if (index == 0) {
                    strokePath.moveTo(x, normalizedY)
                    fillPath.lineTo(x, normalizedY)
                } else {
                    val prevX = (index - 1) * stepX
                    val prevVal = history[index - 1]
                    val prevY = (1f - (prevVal / maxVal).coerceIn(0f, 1f)) * (height - 8f) + 4f

                    val cx = (prevX + x) / 2f
                    strokePath.cubicTo(cx, prevY, cx, normalizedY, x, normalizedY)
                    fillPath.cubicTo(cx, prevY, cx, normalizedY, x, normalizedY)
                }
            }

            fillPath.lineTo((history.size - 1) * stepX, height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor, Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            drawPath(
                path = strokePath,
                color = strokeColor,
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}
