package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_test_results")
data class SpeedTestResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val serverName: String,
    val serverHost: String,
    val pingMs: Float,
    val jitterMs: Float,
    val downloadMbps: Float,
    val uploadMbps: Float,
    val clientIp: String = "",
    val clientIsp: String = "",
    val testType: String = "FULL_SPEEDTEST", // "FULL_SPEEDTEST" or "LATENCY_MONITOR"
    val telemetryId: String? = null,
    val telemetryUrl: String? = null
)
