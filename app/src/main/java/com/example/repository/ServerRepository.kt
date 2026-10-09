package com.example.repository

import android.content.Context
import com.example.model.LibreSpeedServer
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ServerRepository(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("librespeed_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Primary embedded default server with dual endpoints (LAN & WAN auto-selection)
    val netplusDefaultServer = LibreSpeedServer(
        id = 1,
        name = "Data Buana Nusantara by Netplus",
        server = "https://speedtest.gonetplus.web.id/backend",
        candidateEndpoints = listOf(
            "http://192.168.88.2:82/backend",
            "https://speedtest.gonetplus.web.id/backend"
        ),
        dlURL = "garbage.php",
        ulURL = "empty.php",
        pingURL = "empty.php",
        getIpURL = "getIP.php",
        telemetryURL = "results/telemetry.php",
        sponsorName = "Netplus",
        sponsorURL = "https://gonetplus.web.id",
        isDefault = true
    )

    // Default global LibreSpeed servers
    val defaultServers = listOf(
        netplusDefaultServer,
        LibreSpeedServer(
            id = 82,
            name = "Tokyo, Japan (A573)",
            server = "https://librespeed.a573.net",
            dlURL = "backend/garbage.php",
            ulURL = "backend/empty.php",
            pingURL = "backend/empty.php",
            getIpURL = "backend/getIP.php",
            sponsorName = "A573"
        ),
        LibreSpeedServer(
            id = 51,
            name = "Amsterdam, Netherlands (Clouvider)",
            server = "https://ams.speedtest.clouvider.net/backend",
            dlURL = "garbage.php",
            ulURL = "empty.php",
            pingURL = "empty.php",
            getIpURL = "getIP.php",
            sponsorName = "Clouvider"
        ),
        LibreSpeedServer(
            id = 50,
            name = "Frankfurt, Germany (Clouvider)",
            server = "https://fra.speedtest.clouvider.net/backend",
            dlURL = "garbage.php",
            ulURL = "empty.php",
            pingURL = "empty.php",
            getIpURL = "getIP.php",
            sponsorName = "Clouvider"
        ),
        LibreSpeedServer(
            id = 54,
            name = "Los Angeles, USA (Clouvider)",
            server = "https://la.speedtest.clouvider.net/backend",
            dlURL = "garbage.php",
            ulURL = "empty.php",
            pingURL = "empty.php",
            getIpURL = "getIP.php",
            sponsorName = "Clouvider"
        ),
        LibreSpeedServer(
            id = 52,
            name = "New York, USA (Clouvider)",
            server = "https://nyc.speedtest.clouvider.net/backend",
            dlURL = "garbage.php",
            ulURL = "empty.php",
            pingURL = "empty.php",
            getIpURL = "getIP.php",
            sponsorName = "Clouvider"
        ),
        LibreSpeedServer(
            id = 49,
            name = "London, England (Clouvider)",
            server = "https://lon.speedtest.clouvider.net/backend",
            dlURL = "garbage.php",
            ulURL = "empty.php",
            pingURL = "empty.php",
            getIpURL = "getIP.php",
            sponsorName = "Clouvider"
        )
    )

    suspend fun fetchServers(): List<LibreSpeedServer> = withContext(Dispatchers.IO) {
        val customServers = loadCustomServers()
        try {
            val request = Request.Builder()
                .url("https://librespeed.org/backend-servers/servers.php")
                .header("User-Agent", "LibreSpeed-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string()
                if (!json.isNullOrBlank()) {
                    val listType = Types.newParameterizedType(List::class.java, LibreSpeedServer::class.java)
                    val adapter = moshi.adapter<List<LibreSpeedServer>>(listType)
                    val fetched = adapter.fromJson(json)
                    if (!fetched.isNullOrEmpty()) {
                        // Always keep Netplus as the top server
                        return@withContext listOf(netplusDefaultServer) + customServers + fetched.filter {
                            it.server != netplusDefaultServer.server
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // fallback gracefully
        }
        listOf(netplusDefaultServer) + customServers + defaultServers.filter { it.id != netplusDefaultServer.id }
    }

    fun loadCustomServers(): List<LibreSpeedServer> {
        val json = sharedPrefs.getString("custom_servers", null) ?: return emptyList()
        return try {
            val listType = Types.newParameterizedType(List::class.java, LibreSpeedServer::class.java)
            val adapter = moshi.adapter<List<LibreSpeedServer>>(listType)
            adapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomServer(server: LibreSpeedServer) {
        val current = loadCustomServers().toMutableList()
        current.removeAll { it.server == server.server }
        current.add(0, server.copy(isCustom = true))
        val listType = Types.newParameterizedType(List::class.java, LibreSpeedServer::class.java)
        val adapter = moshi.adapter<List<LibreSpeedServer>>(listType)
        sharedPrefs.edit().putString("custom_servers", adapter.toJson(current)).apply()
    }

    fun removeCustomServer(serverUrl: String) {
        val current = loadCustomServers().toMutableList()
        current.removeAll { it.server == serverUrl }
        val listType = Types.newParameterizedType(List::class.java, LibreSpeedServer::class.java)
        val adapter = moshi.adapter<List<LibreSpeedServer>>(listType)
        sharedPrefs.edit().putString("custom_servers", adapter.toJson(current)).apply()
    }

    fun getSelectedServer(): LibreSpeedServer {
        val savedServerUrl = sharedPrefs.getString("selected_server_url", null)
        if (savedServerUrl != null) {
            val all = listOf(netplusDefaultServer) + loadCustomServers() + defaultServers
            val found = all.find { it.server == savedServerUrl || it.candidateEndpoints.contains(savedServerUrl) }
            if (found != null) return found
        }
        return netplusDefaultServer
    }

    fun saveSelectedServer(server: LibreSpeedServer) {
        sharedPrefs.edit().putString("selected_server_url", server.server).apply()
    }
}
