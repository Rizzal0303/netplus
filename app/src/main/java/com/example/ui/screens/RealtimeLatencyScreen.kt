package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RealtimeLatencyState
import com.example.ui.components.RealtimeLatencyChart
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.PurpleAccent
import java.util.Locale

@Composable
fun RealtimeLatencyScreen(
    state: RealtimeLatencyState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onIntervalChange: (Long) -> Unit,
    onOpenServerPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Server Info Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (!state.isRunning) onOpenServerPicker() }
                .testTag("latency_server_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PurpleAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = PurpleAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "TARGET SERVER PING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = state.selectedServer?.name ?: "Memuat Server...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!state.isRunning) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Ganti",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Real-time Latency Hero Display
        val latencyColor by animateColorAsState(
            targetValue = when {
                state.currentLatencyMs <= 0f -> MaterialTheme.colorScheme.onSurfaceVariant
                state.currentLatencyMs < 25f -> GreenAccent
                state.currentLatencyMs < 60f -> CyanAccent
                state.currentLatencyMs < 120f -> AmberAccent
                else -> CoralRed
            },
            label = "latency_color"
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, latencyColor.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (state.isRunning) "LATENSI REAL-TIME" else "MONITOR LATENSI DIHENTIKAN",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.isRunning) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val formattedLatency = if (state.currentLatencyMs > 0f) {
                        String.format(Locale.US, "%.0f", state.currentLatencyMs)
                    } else "--"

                    Text(
                        text = formattedLatency,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Black,
                        color = latencyColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ms",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = latencyColor,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quality Assessment Badge
                val (qualityLabel, qualityIcon, qualityColor) = when {
                    state.currentLatencyMs <= 0f -> Triple("Tekan Mulai untuk menguji latensi", Icons.Default.Public, MaterialTheme.colorScheme.onSurfaceVariant)
                    state.currentLatencyMs < 30f -> Triple("Sangat Baik • Ideal untuk Gaming & Cloud", Icons.Default.SportsEsports, GreenAccent)
                    state.currentLatencyMs < 75f -> Triple("Bagus • Ideal untuk Video Call & Streaming", Icons.Default.Videocam, CyanAccent)
                    state.currentLatencyMs < 150f -> Triple("Cukup • Standar Browsing Web", Icons.Default.Web, AmberAccent)
                    else -> Triple("Tinggi • Terjadi Kelambatan / Lag", Icons.Default.Public, CoralRed)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = qualityColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = qualityIcon,
                            contentDescription = null,
                            tint = qualityColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = qualityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = qualityColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Realtime Latency Oscilloscope Waveform
        RealtimeLatencyChart(samples = state.history)

        Spacer(modifier = Modifier.height(16.dp))

        // Frequency selector chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Interval Pengujian:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(200L to "200ms", 500L to "500ms", 1000L to "1s").forEach { (interval, label) ->
                    val isSelected = state.intervalMs == interval
                    FilterChip(
                        selected = isSelected,
                        onClick = { onIntervalChange(interval) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = Color(0xFF0B111E)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Statistics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatPill(
                label = "TERENDAH",
                value = if (state.minLatencyMs > 0) String.format(Locale.US, "%.0f ms", state.minLatencyMs) else "--",
                color = GreenAccent,
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = "RATA-RATA",
                value = if (state.avgLatencyMs > 0) String.format(Locale.US, "%.0f ms", state.avgLatencyMs) else "--",
                color = CyanAccent,
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = "TERTINGGI",
                value = if (state.maxLatencyMs > 0) String.format(Locale.US, "%.0f ms", state.maxLatencyMs) else "--",
                color = AmberAccent,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatPill(
                label = "JITTER",
                value = if (state.jitterMs > 0) String.format(Locale.US, "%.1f ms", state.jitterMs) else "--",
                color = PurpleAccent,
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = "PACKET LOSS",
                value = String.format(Locale.US, "%.1f%%", state.packetLossPercent),
                color = if (state.packetLossPercent > 0) CoralRed else GreenAccent,
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = "TOTAL PING",
                value = "${state.successfulPings}/${state.totalPings}",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("reset_latency_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("RESET")
            }

            Button(
                onClick = {
                    if (state.isRunning) onStop() else onStart()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRunning) AmberAccent else CyanAccent,
                    contentColor = Color(0xFF0B111E)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(2f)
                    .height(52.dp)
                    .testTag("toggle_latency_button")
            ) {
                Icon(
                    imageVector = if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isRunning) "JEDA MONITOR" else "MULAI MONITOR REAL-TIME",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
