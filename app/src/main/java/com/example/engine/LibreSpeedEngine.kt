package com.example.engine

import com.example.model.LibreSpeedServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

class LibreSpeedEngine {

    private val connectionPool = okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES)

    private val httpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Automatically tests candidate endpoints (e.g. local LAN vs public WAN) and resolves the fastest.
     */
    suspend fun resolveFastestEndpoint(server: LibreSpeedServer): LibreSpeedServer = withContext(Dispatchers.IO) {
        if (server.candidateEndpoints.isEmpty()) {
            return@withContext server.copy(activeEndpoint = server.server)
        }
        if (server.candidateEndpoints.size == 1) {
            return@withContext server.copy(activeEndpoint = server.candidateEndpoints.first())
        }

        val results = coroutineScope {
            server.candidateEndpoints.map { endpoint ->
                async {
                    val trimmed = endpoint.trim().trimEnd('/')
                    val pingEndpoint = server.pingURL.trim().trimStart('/')
                    val separator = if (pingEndpoint.contains("?")) "&" else "?"
                    val target = "$trimmed/$pingEndpoint${separator}cors=true&r=${System.currentTimeMillis()}"

                    val startNano = System.nanoTime()
                    try {
                        val fastClient = httpClient.newBuilder()
                            .connectTimeout(1200, TimeUnit.MILLISECONDS)
                            .readTimeout(1200, TimeUnit.MILLISECONDS)
                            .build()

                        val request = Request.Builder()
                            .url(target)
                            .build()

                        fastClient.newCall(request).execute().use { response ->
                            response.body?.bytes()
                            if (response.isSuccessful) {
                                val elapsedMs = (System.nanoTime() - startNano) / 1_000_000f
                                return@async Pair(endpoint, elapsedMs)
                            }
                        }
                    } catch (_: Exception) {
                    }
                    Pair(endpoint, Float.MAX_VALUE)
                }
            }.awaitAll()
        }

        val reachable = results.filter { it.second < Float.MAX_VALUE }
        val winner = if (reachable.isNotEmpty()) {
            reachable.minByOrNull { it.second }?.first ?: server.candidateEndpoints.first()
        } else {
            server.candidateEndpoints.last()
        }

        server.copy(activeEndpoint = winner)
    }

    /**
     * Measure ping and jitter by performing sequential HTTP requests over an established persistent keep-alive socket.
     */
    suspend fun measurePingAndJitter(
        server: LibreSpeedServer,
        pingCount: Int = 10,
        onProgress: (currentPingMs: Float, progress: Float) -> Unit
    ): Pair<Float, Float> = withContext(Dispatchers.IO) {
        val pings = mutableListOf<Float>()
        val pingUrl = server.resolveUrl(server.pingURL)
        val separator = if (pingUrl.contains("?")) "&" else "?"

        // 1. Initial warm-up ping to establish TCP/TLS connection without penalty to metrics
        try {
            val warmupTarget = "$pingUrl${separator}cors=true&r=warmup_${System.currentTimeMillis()}"
            val warmupReq = Request.Builder()
                .url(warmupTarget)
                .header("Connection", "keep-alive")
                .build()
            httpClient.newCall(warmupReq).execute().use { res ->
                res.body?.bytes()
            }
        } catch (_: Exception) {
        }

        // 2. Measure pings over the warm, persistent keep-alive socket
        for (i in 0 until pingCount) {
            if (!isActive) break
            val target = "$pingUrl${separator}cors=true&r=${System.currentTimeMillis()}_$i"

            val startNano = System.nanoTime()
            try {
                val request = Request.Builder()
                    .url(target)
                    .header("Connection", "keep-alive")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.body?.bytes() // Fully consume body to return socket to pool
                    val elapsedMs = (System.nanoTime() - startNano) / 1_000_000f
                    if (response.isSuccessful && elapsedMs > 0f) {
                        pings.add(elapsedMs)
                        onProgress(elapsedMs, (i + 1).toFloat() / pingCount)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            delay(30)
        }

        if (pings.isEmpty()) {
            return@withContext Pair(0f, 0f)
        }

        pings.sort()
        val bestPing = pings.first()

        var jitterSum = 0f
        var jitterCount = 0
        for (i in 1 until pings.size) {
            jitterSum += abs(pings[i] - pings[i - 1])
            jitterCount++
        }
        val jitter = if (jitterCount > 0) jitterSum / jitterCount else 0f

        Pair(bestPing, jitter)
    }

    /**
     * Measure single ping for real-time latency testing.
     */
    suspend fun measureSinglePing(server: LibreSpeedServer): Float? = withContext(Dispatchers.IO) {
        val pingUrl = server.resolveUrl(server.pingURL)
        val separator = if (pingUrl.contains("?")) "&" else "?"
        val target = "$pingUrl${separator}cors=true&r=${System.currentTimeMillis()}"

        val startNano = System.nanoTime()
        try {
            val request = Request.Builder()
                .url(target)
                .header("Connection", "keep-alive")
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.body?.bytes() // Keep socket in pool
                val elapsedMs = (System.nanoTime() - startNano) / 1_000_000f
                if (response.isSuccessful) {
                    return@withContext elapsedMs
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
        null
    }

    /**
     * Perform download speed test using parallel streams.
     */
    suspend fun measureDownloadSpeed(
        server: LibreSpeedServer,
        durationMs: Long = 9000L,
        streamsCount: Int = 3,
        onProgress: (currentSpeedMbps: Float, fraction: Float) -> Unit
    ): Float = withContext(Dispatchers.IO) {
        val totalBytes = AtomicLong(0L)
        val isRunning = AtomicBoolean(true)
        val startTime = System.currentTimeMillis()
        val speedSamples = mutableListOf<Float>()

        val dlUrl = server.resolveUrl(server.dlURL)
        val separator = if (dlUrl.contains("?")) "&" else "?"

        coroutineScope {
            // Reporter job with rolling window and EMA smoothing
            val reporterJob = async {
                val graceTimeMs = 1000L
                var smoothedSpeed = 0f
                val windowSamples = ArrayDeque<Pair<Long, Long>>() // (timeMs, bytes)

                while (isRunning.get() && isActive) {
                    delay(100)
                    val now = System.currentTimeMillis()
                    val currentBytes = totalBytes.get()
                    val totalElapsed = (now - startTime).coerceAtLeast(1)
                    val fraction = (totalElapsed.toFloat() / durationMs).coerceIn(0f, 1f)

                    windowSamples.addLast(Pair(now, currentBytes))
                    while (windowSamples.size > 1 && (now - windowSamples.first().first > 800L)) {
                        windowSamples.removeFirst()
                    }

                    if (windowSamples.size >= 2) {
                        val oldest = windowSamples.first()
                        val timeDeltaSec = (now - oldest.first) / 1000f
                        val bytesDelta = currentBytes - oldest.second

                        if (timeDeltaSec > 0.15f && bytesDelta >= 0) {
                            val instantRate = (bytesDelta * 8f) / (timeDeltaSec * 1_000_000f)
                            smoothedSpeed = if (smoothedSpeed <= 0.1f) {
                                instantRate
                            } else {
                                smoothedSpeed * 0.70f + instantRate * 0.30f
                            }

                            if (totalElapsed > graceTimeMs) {
                                speedSamples.add(smoothedSpeed)
                            }
                            onProgress(smoothedSpeed, fraction)
                        }
                    }

                    if (now - startTime >= durationMs) {
                        isRunning.set(false)
                        break
                    }
                }
            }

            // Downloader stream workers with staggered starts
            val workers = (0 until streamsCount).map { streamIndex ->
                async {
                    delay(streamIndex * 150L) // stagger stream start like LibreSpeed
                    val buffer = ByteArray(32768)
                    while (isRunning.get() && isActive) {
                        val target = "$dlUrl${separator}ckSize=20&cors=true&r=${System.currentTimeMillis()}_$streamIndex"
                        try {
                            val request = Request.Builder()
                                .url(target)
                                .header("Cache-Control", "no-cache, no-store")
                                .build()

                            httpClient.newCall(request).execute().use { response ->
                                val stream: InputStream? = response.body?.byteStream()
                                if (stream != null) {
                                    while (isRunning.get() && isActive) {
                                        val read = stream.read(buffer)
                                        if (read == -1) break
                                        totalBytes.addAndGet(read.toLong())
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            delay(100)
                        }
                    }
                }
            }

            reporterJob.await()
            workers.forEach { it.cancel() }
        }

        // Calculate final speed discarding initial warmup
        val validSamples = if (speedSamples.size > 5) {
            speedSamples.drop((speedSamples.size * 0.2).toInt())
        } else {
            speedSamples
        }

        if (validSamples.isNotEmpty()) {
            validSamples.average().toFloat()
        } else {
            0f
        }
    }

    /**
     * Perform upload speed test using parallel streams with smooth rate limiting & EMA.
     */
    suspend fun measureUploadSpeed(
        server: LibreSpeedServer,
        durationMs: Long = 8000L,
        streamsCount: Int = 2,
        onProgress: (currentSpeedMbps: Float, fraction: Float) -> Unit
    ): Float = withContext(Dispatchers.IO) {
        val totalBytes = AtomicLong(0L)
        val isRunning = AtomicBoolean(true)
        val startTime = System.currentTimeMillis()
        val speedSamples = mutableListOf<Float>()

        val ulUrl = server.resolveUrl(server.ulURL)
        val separator = if (ulUrl.contains("?")) "&" else "?"

        coroutineScope {
            // Reporter job with rolling window and EMA smoothing
            val reporterJob = async {
                val graceTimeMs = 1200L // 1.2s grace time matching LibreSpeed
                var smoothedSpeed = 0f
                val windowSamples = ArrayDeque<Pair<Long, Long>>() // (timeMs, bytes)

                while (isRunning.get() && isActive) {
                    delay(100) // 100ms updates
                    val now = System.currentTimeMillis()
                    val currentBytes = totalBytes.get()
                    val totalElapsed = (now - startTime).coerceAtLeast(1)
                    val fraction = (totalElapsed.toFloat() / durationMs).coerceIn(0f, 1f)

                    windowSamples.addLast(Pair(now, currentBytes))
                    while (windowSamples.size > 1 && (now - windowSamples.first().first > 800L)) {
                        windowSamples.removeFirst()
                    }

                    if (windowSamples.size >= 2) {
                        val oldest = windowSamples.first()
                        val timeDeltaSec = (now - oldest.first) / 1000f
                        val bytesDelta = currentBytes - oldest.second

                        if (timeDeltaSec > 0.15f && bytesDelta >= 0) {
                            val instantRate = (bytesDelta * 8f) / (timeDeltaSec * 1_000_000f)
                            smoothedSpeed = if (smoothedSpeed <= 0.1f) {
                                instantRate
                            } else {
                                smoothedSpeed * 0.70f + instantRate * 0.30f
                            }

                            if (totalElapsed > graceTimeMs) {
                                speedSamples.add(smoothedSpeed)
                            }
                            onProgress(smoothedSpeed, fraction)
                        }
                    }

                    if (now - startTime >= durationMs) {
                        isRunning.set(false)
                        break
                    }
                }
            }

            // Uploader stream workers with staggered start
            val workers = (0 until streamsCount).map { streamIndex ->
                async {
                    delay(streamIndex * 200L) // Stagger streams to avoid synchronized chunk boundaries
                    val chunkSize = 1024 * 1024L // 1 MB chunk (more granular than 2MB)
                    while (isRunning.get() && isActive) {
                        val target = "$ulUrl${separator}cors=true&r=${System.currentTimeMillis()}_$streamIndex"
                        try {
                            val requestBody = CountingRequestBody(chunkSize) { writtenInChunk, _ ->
                                if (isRunning.get()) {
                                    totalBytes.addAndGet(writtenInChunk)
                                }
                            }

                            val request = Request.Builder()
                                .url(target)
                                .post(requestBody)
                                .header("Cache-Control", "no-cache, no-store")
                                .build()

                            httpClient.newCall(request).execute().close()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            delay(100)
                        }
                    }
                }
            }

            reporterJob.await()
            workers.forEach { it.cancel() }
        }

        val validSamples = if (speedSamples.size > 5) {
            speedSamples.drop((speedSamples.size * 0.2).toInt())
        } else {
            speedSamples
        }

        if (validSamples.isNotEmpty()) {
            validSamples.average().toFloat()
        } else {
            0f
        }
    }

    /**
     * Fetch client IP address and ISP name from the LibreSpeed server with fallback.
     */
    suspend fun fetchClientIp(server: LibreSpeedServer): Pair<String, String> = withContext(Dispatchers.IO) {
        var ip = ""
        var isp = ""

        // 1. Try LibreSpeed endpoint first
        try {
            val ipUrl = server.resolveUrl(server.getIpURL)
            val separator = if (ipUrl.contains("?")) "&" else "?"
            val target = "$ipUrl${separator}cors=true&isp=true"

            val request = Request.Builder()
                .url(target)
                .header("Cache-Control", "no-cache")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val processed = json.optString("processedString", "")
                        if (processed.contains(" - ")) {
                            ip = processed.substringBefore(" - ").trim()
                            isp = processed.substringAfter(" - ").trim()
                        } else if (processed.isNotBlank()) {
                            ip = processed.trim()
                        }

                        // Try extracting from rawIspInfo (often a JSONObject or String)
                        val rawIspObj = json.optJSONObject("rawIspInfo")
                        if (rawIspObj != null) {
                            val asName = rawIspObj.optString("as_name", "").trim()
                            val rawIsp = rawIspObj.optString("isp", "").trim()
                            val org = rawIspObj.optString("org", "").trim()
                            when {
                                asName.isNotBlank() -> isp = asName
                                rawIsp.isNotBlank() -> isp = rawIsp
                                org.isNotBlank() -> isp = org
                            }
                        } else {
                            val rawIspStr = json.optString("rawIspInfo", "").trim()
                            if (rawIspStr.isNotBlank() && !rawIspStr.startsWith("{")) {
                                isp = rawIspStr
                            }
                        }
                    } else if (body.isNotBlank()) {
                        val trimmed = body.trim()
                        if (trimmed.contains(" - ")) {
                            ip = trimmed.substringBefore(" - ").trim()
                            isp = trimmed.substringAfter(" - ").trim()
                        } else {
                            ip = trimmed
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. If ISP or public IP is missing or local private IP (192.168.x.x, 10.x.x.x, 127.x.x.x), fallback to ip-api
        if (isp.isBlank() || ip.isBlank() || ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("127.")) {
            try {
                val fallbackReq = Request.Builder()
                    .url("http://ip-api.com/json")
                    .header("Cache-Control", "no-cache")
                    .build()

                httpClient.newCall(fallbackReq).execute().use { fbRes ->
                    if (fbRes.isSuccessful) {
                        val fbBody = fbRes.body?.string() ?: ""
                        if (fbBody.startsWith("{")) {
                            val fbJson = JSONObject(fbBody)
                            if (ip.isBlank() || ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("127.")) {
                                val queryIp = fbJson.optString("query", "").trim()
                                if (queryIp.isNotBlank()) ip = queryIp
                            }
                            if (isp.isBlank()) {
                                val fbIsp = fbJson.optString("isp", "").trim()
                                val fbOrg = fbJson.optString("org", "").trim()
                                val asInfo = fbJson.optString("as", "").trim()
                                isp = when {
                                    fbIsp.isNotBlank() -> fbIsp
                                    fbOrg.isNotBlank() -> fbOrg
                                    asInfo.isNotBlank() -> asInfo.replace(Regex("^AS\\d+\\s*"), "")
                                    else -> ""
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        Pair(ip, isp)
    }

    data class TelemetryResult(
        val id: String,
        val resultUrl: String,
        val endpointUsed: String
    )

    /**
     * Submits speed test results to the server's telemetry database (e.g. results/telemetry.php).
     * LibreSpeed server saves it into MySQL / SQLite / PostgreSQL and returns "id <testId>".
     */
    suspend fun submitTelemetry(
        server: LibreSpeedServer,
        downloadSpeedMbps: Float,
        uploadSpeedMbps: Float,
        pingMs: Float,
        jitterMs: Float,
        clientIp: String,
        clientIsp: String
    ): TelemetryResult? = withContext(Dispatchers.IO) {
        val candidateUrls = server.resolveTelemetryCandidates()
        val ispInfoStr = if (clientIsp.isNotBlank()) {
            if (clientIp.isNotBlank()) "$clientIp - $clientIsp" else clientIsp
        } else {
            clientIp
        }

        val dlStr = String.format(java.util.Locale.US, "%.2f", downloadSpeedMbps)
        val ulStr = String.format(java.util.Locale.US, "%.2f", uploadSpeedMbps)
        val pingStr = String.format(java.util.Locale.US, "%.2f", pingMs)
        val jitterStr = String.format(java.util.Locale.US, "%.2f", jitterMs)

        val formBody = FormBody.Builder()
            .add("dl", dlStr)
            .add("ul", ulStr)
            .add("ping", pingStr)
            .add("jitter", jitterStr)
            .add("ispinfo", ispInfoStr)
            .add("extra", "")
            .add("log", "")
            .build()

        for (url in candidateUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(formBody)
                    .header("Cache-Control", "no-cache")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()?.trim() ?: ""
                        var id: String? = null
                        if (body.startsWith("id ")) {
                            id = body.substringAfter("id ").trim()
                        } else if (body.startsWith("id:")) {
                            id = body.substringAfter("id:").trim()
                        } else if (body.startsWith("{")) {
                            try {
                                val json = JSONObject(body)
                                id = json.optString("id", "").ifBlank {
                                    json.optString("testId", "")
                                }
                            } catch (_: Exception) {
                            }
                        } else if (body.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                            id = body
                        }

                        if (!id.isNullOrBlank()) {
                            val baseDir = url.substringBeforeLast("/")
                            val resultUrl = "$baseDir/?id=$id"
                            return@withContext TelemetryResult(
                                id = id,
                                resultUrl = resultUrl,
                                endpointUsed = url
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        null
    }
}
