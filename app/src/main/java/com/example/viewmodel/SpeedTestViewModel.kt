package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.AppDatabase
import com.example.db.SpeedTestResultEntity
import com.example.engine.LibreSpeedEngine
import com.example.model.LatencySample
import com.example.model.LibreSpeedServer
import com.example.model.RealtimeLatencyState
import com.example.model.SpeedTestPhase
import com.example.model.SpeedTestState
import com.example.repository.ServerRepository
import com.example.repository.SpeedTestRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SpeedTestViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = SpeedTestRepository(db.speedTestDao())
    private val serverRepo = ServerRepository(application)
    private val engine = LibreSpeedEngine()

    private val _speedTestState = MutableStateFlow(SpeedTestState())
    val speedTestState: StateFlow<SpeedTestState> = _speedTestState.asStateFlow()

    private val _realtimeLatencyState = MutableStateFlow(RealtimeLatencyState())
    val realtimeLatencyState: StateFlow<RealtimeLatencyState> = _realtimeLatencyState.asStateFlow()

    private val _serverList = MutableStateFlow<List<LibreSpeedServer>>(emptyList())
    val serverList: StateFlow<List<LibreSpeedServer>> = _serverList.asStateFlow()

    private val _isLoadingServers = MutableStateFlow(false)
    val isLoadingServers: StateFlow<Boolean> = _isLoadingServers.asStateFlow()

    val historyResults: StateFlow<List<SpeedTestResultEntity>> = repository.allResults
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var speedTestJob: Job? = null
    private var latencyJob: Job? = null

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            _isLoadingServers.value = true
            val initialSelected = serverRepo.getSelectedServer()
            _speedTestState.update { it.copy(selectedServer = initialSelected) }
            _realtimeLatencyState.update { it.copy(selectedServer = initialSelected) }

            val servers = serverRepo.fetchServers()
            _serverList.value = servers
            var selected = servers.find { it.server == initialSelected.server } ?: servers.firstOrNull() ?: initialSelected
            if (selected.candidateEndpoints.isNotEmpty()) {
                selected = engine.resolveFastestEndpoint(selected)
            }
            _speedTestState.update { it.copy(selectedServer = selected) }
            _realtimeLatencyState.update { it.copy(selectedServer = selected) }
            _isLoadingServers.value = false

            // Fetch IP info in background
            try {
                val (ip, isp) = engine.fetchClientIp(selected)
                _speedTestState.update { it.copy(clientIp = ip, clientIsp = isp) }
            } catch (_: Exception) {
            }
        }
    }

    fun selectServer(server: LibreSpeedServer) {
        serverRepo.saveSelectedServer(server)
        viewModelScope.launch {
            val resolvedServer = if (server.candidateEndpoints.isNotEmpty()) {
                engine.resolveFastestEndpoint(server)
            } else {
                server
            }
            _speedTestState.update { it.copy(selectedServer = resolvedServer) }
            _realtimeLatencyState.update { it.copy(selectedServer = resolvedServer) }

            // Refresh IP info for selected server
            try {
                val (ip, isp) = engine.fetchClientIp(resolvedServer)
                _speedTestState.update { it.copy(clientIp = ip, clientIsp = isp) }
            } catch (_: Exception) {
            }
        }
    }

    fun addCustomServer(
        name: String,
        url: String,
        dlUrl: String,
        ulUrl: String,
        pingUrl: String,
        getIpUrl: String,
        telemetryUrl: String = "results/telemetry.php"
    ) {
        val custom = LibreSpeedServer(
            id = (System.currentTimeMillis() % 100000).toInt(),
            name = name.ifBlank { "Custom Server" },
            server = url.trim(),
            dlURL = dlUrl.ifBlank { "garbage.php" },
            ulURL = ulUrl.ifBlank { "empty.php" },
            pingURL = pingUrl.ifBlank { "empty.php" },
            getIpURL = getIpUrl.ifBlank { "getIP.php" },
            telemetryURL = telemetryUrl.ifBlank { "results/telemetry.php" },
            isCustom = true
        )
        serverRepo.saveCustomServer(custom)
        val updated = serverRepo.loadCustomServers() + (_serverList.value.filter { !it.isCustom })
        _serverList.value = updated
        selectServer(custom)
    }

    fun removeCustomServer(serverUrl: String) {
        serverRepo.removeCustomServer(serverUrl)
        val updated = _serverList.value.filterNot { it.server == serverUrl }
        _serverList.value = updated
        if (_speedTestState.value.selectedServer?.server == serverUrl) {
            updated.firstOrNull()?.let { selectServer(it) }
        }
    }

    fun toggleSpeedUnit() {
        _speedTestState.update { it.copy(useMegabytes = !it.useMegabytes) }
    }

    fun startSpeedTest() {
        if (_speedTestState.value.phase != SpeedTestPhase.IDLE &&
            _speedTestState.value.phase != SpeedTestPhase.COMPLETED &&
            _speedTestState.value.phase != SpeedTestPhase.ERROR
        ) return

        val baseServer = _speedTestState.value.selectedServer ?: serverRepo.getSelectedServer()

        speedTestJob?.cancel()
        speedTestJob = viewModelScope.launch {
            _speedTestState.update {
                it.copy(
                    phase = SpeedTestPhase.PREPARING,
                    currentSpeedMbps = 0f,
                    progressFraction = 0f,
                    pingMs = 0f,
                    jitterMs = 0f,
                    downloadSpeedMbps = 0f,
                    uploadSpeedMbps = 0f,
                    errorMessage = null,
                    throughputHistory = emptyList()
                )
            }

            try {
                // Auto-resolve fastest endpoint (e.g. LAN 192.168.88.2 vs WAN domain)
                val server = if (baseServer.candidateEndpoints.isNotEmpty()) {
                    engine.resolveFastestEndpoint(baseServer).also { resolved ->
                        _speedTestState.update { it.copy(selectedServer = resolved) }
                    }
                } else baseServer

                // Fetch IP
                val (ip, isp) = engine.fetchClientIp(server)
                _speedTestState.update { it.copy(clientIp = ip, clientIsp = isp) }

                // Phase 1: Ping & Jitter
                _speedTestState.update { it.copy(phase = SpeedTestPhase.PING, progressFraction = 0f) }
                val (ping, jitter) = engine.measurePingAndJitter(
                    server = server,
                    pingCount = 10,
                    onProgress = { samplePing, progress ->
                        _speedTestState.update {
                            it.copy(
                                pingMs = samplePing,
                                progressFraction = progress
                            )
                        }
                    }
                )
                _speedTestState.update {
                    it.copy(
                        pingMs = ping,
                        jitterMs = jitter,
                        progressFraction = 1f
                    )
                }

                delay(300)

                // Phase 2: Download
                _speedTestState.update {
                    it.copy(
                        phase = SpeedTestPhase.DOWNLOAD,
                        progressFraction = 0f,
                        throughputHistory = emptyList()
                    )
                }
                val downloadSpeed = engine.measureDownloadSpeed(
                    server = server,
                    durationMs = 9000L,
                    streamsCount = 3,
                    onProgress = { instantSpeed, fraction ->
                        _speedTestState.update { state ->
                            val history = (state.throughputHistory + instantSpeed).takeLast(40)
                            state.copy(
                                currentSpeedMbps = instantSpeed,
                                progressFraction = fraction,
                                throughputHistory = history
                            )
                        }
                    }
                )
                _speedTestState.update {
                    it.copy(
                        downloadSpeedMbps = downloadSpeed,
                        currentSpeedMbps = downloadSpeed,
                        progressFraction = 1f
                    )
                }

                delay(400)

                // Phase 3: Upload
                _speedTestState.update {
                    it.copy(
                        phase = SpeedTestPhase.UPLOAD,
                        progressFraction = 0f,
                        currentSpeedMbps = 0f,
                        throughputHistory = emptyList()
                    )
                }
                val uploadSpeed = engine.measureUploadSpeed(
                    server = server,
                    durationMs = 8000L,
                    streamsCount = 2,
                    onProgress = { instantSpeed, fraction ->
                        _speedTestState.update { state ->
                            val history = (state.throughputHistory + instantSpeed).takeLast(40)
                            state.copy(
                                currentSpeedMbps = instantSpeed,
                                progressFraction = fraction,
                                throughputHistory = history
                            )
                        }
                    }
                )
                // Step 4: Submit results to server database (LibreSpeed telemetry endpoint)
                var telemetryId: String? = null
                var telemetryUrl: String? = null
                try {
                    val telemetryResult = engine.submitTelemetry(
                        server = server,
                        downloadSpeedMbps = downloadSpeed,
                        uploadSpeedMbps = uploadSpeed,
                        pingMs = ping,
                        jitterMs = jitter,
                        clientIp = _speedTestState.value.clientIp,
                        clientIsp = _speedTestState.value.clientIsp
                    )
                    if (telemetryResult != null) {
                        telemetryId = telemetryResult.id
                        telemetryUrl = telemetryResult.resultUrl
                    }
                } catch (_: Exception) {
                }

                _speedTestState.update {
                    it.copy(
                        uploadSpeedMbps = uploadSpeed,
                        currentSpeedMbps = 0f,
                        progressFraction = 1f,
                        phase = SpeedTestPhase.COMPLETED,
                        telemetryId = telemetryId,
                        telemetryUrl = telemetryUrl,
                        isSavedToDatabase = true
                    )
                }

                // Save to local Room Database with server telemetry ID
                repository.saveResult(
                    SpeedTestResultEntity(
                        serverName = server.name,
                        serverHost = server.server,
                        pingMs = ping,
                        jitterMs = jitter,
                        downloadMbps = downloadSpeed,
                        uploadMbps = uploadSpeed,
                        clientIp = _speedTestState.value.clientIp,
                        clientIsp = _speedTestState.value.clientIsp,
                        testType = "FULL_SPEEDTEST",
                        telemetryId = telemetryId,
                        telemetryUrl = telemetryUrl
                    )
                )

            } catch (e: Exception) {
                if (isActive) {
                    _speedTestState.update {
                        it.copy(
                            phase = SpeedTestPhase.ERROR,
                            errorMessage = e.localizedMessage ?: "Terjadi kesalahan saat pengujian"
                        )
                    }
                }
            }
        }
    }

    fun stopSpeedTest() {
        speedTestJob?.cancel()
        _speedTestState.update {
            it.copy(
                phase = SpeedTestPhase.IDLE,
                currentSpeedMbps = 0f,
                progressFraction = 0f
            )
        }
    }

    // Real-Time Latency Testing
    fun startRealtimeLatency() {
        if (_realtimeLatencyState.value.isRunning) return

        val baseServer = _realtimeLatencyState.value.selectedServer
            ?: _speedTestState.value.selectedServer
            ?: serverRepo.getSelectedServer()

        latencyJob?.cancel()
        latencyJob = viewModelScope.launch {
            val server = if (baseServer.candidateEndpoints.isNotEmpty()) {
                engine.resolveFastestEndpoint(baseServer).also { resolved ->
                    _realtimeLatencyState.update { it.copy(selectedServer = resolved) }
                }
            } else baseServer

            _realtimeLatencyState.update {
                it.copy(
                    isRunning = true,
                    selectedServer = server
                )
            }

            val pingList = mutableListOf<Float>()
            var totalCount = 0
            var successCount = 0

            while (isActive) {
                totalCount++
                val samplePing = engine.measureSinglePing(server)
                val isSuccess = samplePing != null && samplePing > 0f

                if (isSuccess && samplePing != null) {
                    successCount++
                    pingList.add(samplePing)
                }

                val currentPing = samplePing ?: 0f
                val minPing = if (pingList.isNotEmpty()) pingList.minOrNull() ?: 0f else 0f
                val maxPing = if (pingList.isNotEmpty()) pingList.maxOrNull() ?: 0f else 0f
                val avgPing = if (pingList.isNotEmpty()) pingList.average().toFloat() else 0f

                // Jitter calculation
                var jitterSum = 0f
                for (i in 1 until pingList.size) {
                    jitterSum += abs(pingList[i] - pingList[i - 1])
                }
                val jitter = if (pingList.size > 1) jitterSum / (pingList.size - 1) else 0f

                val packetLoss = if (totalCount > 0) {
                    ((totalCount - successCount).toFloat() / totalCount) * 100f
                } else 0f

                val newSample = LatencySample(
                    timestamp = System.currentTimeMillis(),
                    latencyMs = currentPing,
                    isSuccess = isSuccess
                )

                _realtimeLatencyState.update { state ->
                    val history = (state.history + newSample).takeLast(50)
                    state.copy(
                        currentLatencyMs = currentPing,
                        minLatencyMs = minPing,
                        maxLatencyMs = maxPing,
                        avgLatencyMs = avgPing,
                        jitterMs = jitter,
                        packetLossPercent = packetLoss,
                        totalPings = totalCount,
                        successfulPings = successCount,
                        history = history
                    )
                }

                delay(_realtimeLatencyState.value.intervalMs)
            }
        }
    }

    fun stopRealtimeLatency() {
        latencyJob?.cancel()
        _realtimeLatencyState.update { it.copy(isRunning = false) }

        // Save session summary to history if there were pings
        val state = _realtimeLatencyState.value
        if (state.totalPings > 0 && state.successfulPings > 0) {
            viewModelScope.launch {
                val server = state.selectedServer ?: serverRepo.getSelectedServer()
                repository.saveResult(
                    SpeedTestResultEntity(
                        serverName = server.name,
                        serverHost = server.server,
                        pingMs = state.avgLatencyMs,
                        jitterMs = state.jitterMs,
                        downloadMbps = 0f,
                        uploadMbps = 0f,
                        clientIp = _speedTestState.value.clientIp,
                        clientIsp = "Latency: ${state.minLatencyMs.toInt()}-${state.maxLatencyMs.toInt()}ms (Loss: ${state.packetLossPercent.toInt()}%)",
                        testType = "LATENCY_MONITOR"
                    )
                )
            }
        }
    }

    fun resetRealtimeLatency() {
        val isRunning = _realtimeLatencyState.value.isRunning
        stopRealtimeLatency()
        _realtimeLatencyState.update {
            RealtimeLatencyState(
                isRunning = false,
                intervalMs = it.intervalMs,
                selectedServer = it.selectedServer
            )
        }
        if (isRunning) {
            startRealtimeLatency()
        }
    }

    fun setLatencyInterval(intervalMs: Long) {
        _realtimeLatencyState.update { it.copy(intervalMs = intervalMs) }
    }

    fun findBestServer() {
        viewModelScope.launch {
            _isLoadingServers.value = true
            val candidates = _serverList.value.take(6)
            var fastestServer: LibreSpeedServer? = null
            var lowestPing = Float.MAX_VALUE

            for (server in candidates) {
                val ping = engine.measureSinglePing(server)
                if (ping != null && ping > 0 && ping < lowestPing) {
                    lowestPing = ping
                    fastestServer = server
                }
            }

            fastestServer?.let { selectServer(it) }
            _isLoadingServers.value = false
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteResult(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speedTestJob?.cancel()
        latencyJob?.cancel()
    }
}
