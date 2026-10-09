package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LibreSpeedServer(
    @Json(name = "id") val id: Int = 0,
    @Json(name = "name") val name: String,
    @Json(name = "server") val server: String,
    @Json(name = "dlURL") val dlURL: String = "garbage.php",
    @Json(name = "ulURL") val ulURL: String = "empty.php",
    @Json(name = "pingURL") val pingURL: String = "empty.php",
    @Json(name = "getIpURL") val getIpURL: String = "getIP.php",
    @Json(name = "telemetryURL") val telemetryURL: String? = "results/telemetry.php",
    @Json(name = "sponsorName") val sponsorName: String? = null,
    @Json(name = "sponsorURL") val sponsorURL: String? = null,
    val candidateEndpoints: List<String> = emptyList(),
    val activeEndpoint: String? = null,
    val pingMs: Long? = null,
    val isCustom: Boolean = false,
    val isDefault: Boolean = false
) {
    /**
     * Effective base URL: uses activeEndpoint if resolved, otherwise candidateEndpoints.firstOrNull() ?: server
     */
    fun getEffectiveBaseUrl(): String {
        return activeEndpoint ?: candidateEndpoints.firstOrNull() ?: server
    }

    /**
     * Normalizes endpoint URL taking into account relative or absolute paths.
     */
    fun resolveUrl(endpoint: String): String {
        val base = getEffectiveBaseUrl().trim().trimEnd('/')
        val trimmedEndpoint = endpoint.trim().trimStart('/')
        return if (trimmedEndpoint.startsWith("http://") || trimmedEndpoint.startsWith("https://")) {
            trimmedEndpoint
        } else {
            "$base/$trimmedEndpoint"
        }
    }

    /**
     * Resolves candidate telemetry URLs.
     * When base is e.g. "https://speedtest.gonetplus.web.id/backend",
     * LibreSpeed places results in sibling directory "https://speedtest.gonetplus.web.id/results/telemetry.php".
     */
    fun resolveTelemetryCandidates(): List<String> {
        val custom = telemetryURL?.trim()?.trimStart('/')
        val base = getEffectiveBaseUrl().trim().trimEnd('/')

        if (custom != null && (custom.startsWith("http://") || custom.startsWith("https://"))) {
            return listOf(custom)
        }

        val candidates = mutableListOf<String>()
        val parentBase = if (base.endsWith("/backend")) base.removeSuffix("/backend") else base

        if (!custom.isNullOrBlank()) {
            candidates.add("$parentBase/$custom")
            candidates.add("$base/$custom")
        }

        // Standard LibreSpeed paths
        candidates.add("$parentBase/results/telemetry.php")
        candidates.add("$base/results/telemetry.php")
        candidates.add("$base/telemetry.php")
        candidates.add("$parentBase/telemetry.php")

        return candidates.distinct()
    }

    /**
     * Route label is suppressed per user requirements.
     */
    fun getActiveRouteLabel(): String? = null
}
