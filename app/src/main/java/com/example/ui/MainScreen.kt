package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ServerPickerSheet
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.RealtimeLatencyScreen
import com.example.ui.screens.SpeedTestScreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBg
import com.example.viewmodel.SpeedTestViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SpeedTestViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showServerPicker by remember { mutableStateOf(false) }
    var openAddCustomDialogDirectly by remember { mutableStateOf(false) }

    val speedTestState by viewModel.speedTestState.collectAsStateWithLifecycle()
    val realtimeLatencyState by viewModel.realtimeLatencyState.collectAsStateWithLifecycle()
    val serverList by viewModel.serverList.collectAsStateWithLifecycle()
    val historyResults by viewModel.historyResults.collectAsStateWithLifecycle()
    val isLoadingServers by viewModel.isLoadingServers.collectAsStateWithLifecycle()

    // Handle back button when not on main speedtest tab
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CyanAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = DarkBg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "LibreSpeed",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            openAddCustomDialogDirectly = false
                            showServerPicker = true
                        },
                        modifier = Modifier.testTag("top_bar_server_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Pilih Server",
                            tint = CyanAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speedtest"
                        )
                    },
                    label = { Text("Speedtest") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("tab_speedtest")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Latensi Real-time"
                        )
                    },
                    label = { Text("Latensi") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("tab_latency")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Riwayat"
                        )
                    },
                    label = { Text("Riwayat") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("tab_history")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> SpeedTestScreen(
                    state = speedTestState,
                    onStartTest = { viewModel.startSpeedTest() },
                    onStopTest = { viewModel.stopSpeedTest() },
                    onOpenServerPicker = { showServerPicker = true },
                    onToggleUnit = { viewModel.toggleSpeedUnit() }
                )
                1 -> RealtimeLatencyScreen(
                    state = realtimeLatencyState,
                    onStart = { viewModel.startRealtimeLatency() },
                    onStop = { viewModel.stopRealtimeLatency() },
                    onReset = { viewModel.resetRealtimeLatency() },
                    onIntervalChange = { viewModel.setLatencyInterval(it) },
                    onOpenServerPicker = { showServerPicker = true }
                )
                2 -> HistoryScreen(
                    results = historyResults,
                    onDeleteItem = { viewModel.deleteHistoryItem(it) },
                    onClearAll = { viewModel.clearAllHistory() }
                )
            }
        }
    }

    if (showServerPicker) {
        ServerPickerSheet(
            servers = serverList,
            selectedServer = speedTestState.selectedServer,
            isLoading = isLoadingServers,
            onSelectServer = { viewModel.selectServer(it) },
            onFindBestServer = { viewModel.findBestServer() },
            onRefreshServers = { viewModel.loadServers() },
            onAddCustomServer = { name, url, dl, ul, ping, ip, telemetry ->
                viewModel.addCustomServer(name, url, dl, ul, ping, ip, telemetry)
            },
            onDeleteCustomServer = { viewModel.removeCustomServer(it) },
            onDismiss = { showServerPicker = false }
        )
    }
}
