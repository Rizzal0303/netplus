package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LibreSpeedTrackDark
import com.example.ui.theme.LibreSpeedTrackLight
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Authentic LibreSpeed circular arc gauge matching example-singleServer-gauges.html
 * uses: amount = 1 - (1 / (1.3 ^ sqrt(speed)))
 */
@Composable
fun LibreSpeedClassicGauge(
    title: String,
    speedMbps: Float,
    unit: String = "Mbit/s",
    progressFraction: Float = 0f,
    activeColor: Color,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Exact LibreSpeed amount formula
    val targetAmount = if (speedMbps <= 0f) 0f else {
        (1.0 - (1.0 / 1.3.pow(sqrt(speedMbps.toDouble())))).toFloat().coerceIn(0f, 1f)
    }

    val animatedAmount by animateFloatAsState(
        targetValue = targetAmount,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "classic_gauge_amount"
    )

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val trackColor = if (isDark) LibreSpeedTrackDark else LibreSpeedTrackLight

    Box(
        modifier = modifier
            .size(width = 175.dp, height = 170.dp)
            .testTag("classic_gauge_${title.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 10.dp.toPx()
            val canvasW = size.width
            val canvasH = size.height

            val arcSize = canvasW - strokeWidth * 2
            val topLeft = Offset(strokeWidth, strokeWidth + 6.dp.toPx())

            // Start angle in degrees: 198° (-1.1 * PI radians)
            // Sweep angle in degrees: 216° (1.2 * PI radians)
            val startAngle = 162f
            val totalSweep = 216f

            // Background track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active arc
            if (animatedAmount > 0.005f) {
                drawArc(
                    color = activeColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep * animatedAmount,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Progress bar at the bottom if testing
            if (isActive && progressFraction > 0f) {
                val barW = canvasW * 0.45f
                val barH = 3.dp.toPx()
                val barX = (canvasW - barW) / 2f
                val barY = canvasH - 10.dp.toPx()

                // Progress track
                drawRect(
                    color = trackColor,
                    topLeft = Offset(barX, barY),
                    size = Size(barW, barH)
                )
                // Active progress fill
                drawRect(
                    color = activeColor,
                    topLeft = Offset(barX, barY),
                    size = Size(barW * progressFraction.coerceIn(0f, 1f), barH)
                )
            }
        }

        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            // Title (Download / Upload)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Speed Readout matching LibreSpeed format(d)
            val formatted = when {
                speedMbps <= 0f -> "0.00"
                speedMbps < 10f -> String.format(Locale.US, "%.2f", speedMbps)
                speedMbps < 100f -> String.format(Locale.US, "%.1f", speedMbps)
                else -> String.format(Locale.US, "%.0f", speedMbps)
            }

            Text(
                text = formatted,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = activeColor
            )

            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
