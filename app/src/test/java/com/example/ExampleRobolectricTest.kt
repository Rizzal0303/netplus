package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.LibreSpeedServer
import com.example.repository.ServerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LibreSpeed", appName)
    }

    @Test
    fun `test server url resolution`() {
        val server = LibreSpeedServer(
            id = 1,
            name = "Test Server",
            server = "https://speedtest.example.com/backend"
        )
        assertEquals("https://speedtest.example.com/backend/garbage.php", server.resolveUrl("garbage.php"))
        assertEquals("https://speedtest.example.com/backend/empty.php", server.resolveUrl("/empty.php"))
        assertEquals("https://other.com/custom.php", server.resolveUrl("https://other.com/custom.php"))
    }

    @Test
    fun `test netplus embedded default server configuration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ServerRepository(context)
        val defaultServer = repo.getSelectedServer()

        assertEquals("Data Buana Nusantara by Netplus", defaultServer.name)
        assertTrue(defaultServer.candidateEndpoints.contains("http://192.168.88.2:82/backend"))
        assertTrue(defaultServer.candidateEndpoints.contains("https://speedtest.gonetplus.web.id/backend"))
        assertTrue(defaultServer.isDefault)

        // When active endpoint is LAN
        val lanServer = defaultServer.copy(activeEndpoint = "http://192.168.88.2:82/backend")
        assertEquals("http://192.168.88.2:82/backend/empty.php", lanServer.resolveUrl("empty.php"))

        // When active endpoint is WAN
        val wanServer = defaultServer.copy(activeEndpoint = "https://speedtest.gonetplus.web.id/backend")
        assertEquals("https://speedtest.gonetplus.web.id/backend/empty.php", wanServer.resolveUrl("empty.php"))
    }
}
