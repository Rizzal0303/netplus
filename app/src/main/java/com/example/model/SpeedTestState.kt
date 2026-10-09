package com.example.model

enum class SpeedTestPhase {
    IDLE,
    PREPARING,
    PING,
    DOWNLOAD,
    UPLOAD,
    COMPLETED,
    ERROR
}

data class SpeedTestState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val currentSpeedMbps: Float = 0f,
    val progressFraction: Float = 0f, // 0.0f to 1.0f in current phase
    val pingMs: Float = 0f,
    val jitterMs: Float = 0f,
    val downloadSpeedMbps: Float = 0f,
    val uploadSpeedMbps: Float = 0f,
    val clientIp: String = "",
    val clientIsp: String = "",
    val selectedServer: LibreSpeedServer? = null,
    val errorMessage: String? = null,
    val throughputHistory: List<Float> = emptyList(), // live throughput graph during dl/ul
    val useMegabytes: Boolean = false, // toggle between Mbps and MB/s
    val telemetryId: String? = null,
    val telemetryUrl: String? = null,
    val isSavedToDatabase: Boolean = false
)

data class LatencySample(
    val timestamp: Long = System.currentTimeMillis(),
    val latencyMs: Float,
    val isSuccess: Boolean = true
)

data class RealtimeLatencyState(
    val isRunning: Boolean = false,
    val currentLatencyMs: Float = 0f,
    val minLatencyMs: Float = 0f,
    val maxLatencyMs: Float = 0f,
    val avgLatencyMs: Float = 0f,
    val jitterMs: Float = 0f,
    val packetLossPercent: Float = 0f,
    val totalPings: Int = 0,
    val successfulPings: Int = 0,
    val history: List<LatencySample> = emptyList(),
    val intervalMs: Long = 500L,
    val selectedServer: LibreSpeedServer? = null
)
