package com.offlinesync.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.offlinesync.presentation.screens.DevicesScreen // Will create this later
import com.offlinesync.presentation.screens.FoldersScreen // Will create this later
import com.offlinesync.presentation.screens.HomeScreen // Will create this later
import com.offlinesync.presentation.screens.BackupContactsScreen
import com.offlinesync.presentation.screens.RestoreContactsScreen // Added for restore
import com.offlinesync.presentation.theme.OfflineSyncTheme
import dagger.hilt.android.AndroidEntryPoint
import com.offlinesync.service.MDNSService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.ui.unit.dp


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var mdnsService: MDNSService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mdnsService = MDNSService(this)
        mdnsService.startDiscovery()

        // Observe discovered services (for prototyping, just log)
        lifecycleScope.launch {
            mdnsService.discoveredServices.collect { serviceInfo ->
                Log.d("MDNSService", "Discovered: $serviceInfo")
            }
        }

        enableEdgeToEdge()
        setContent {
            OfflineSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    Scaffold(
                        topBar = {
                            Text(text = "OfflineSync", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            composable("home") {
                                HomeScreen(
                                    paddingValues = paddingValues, // Pass paddingValues
                                    onNavigateToDevices = { navController.navigate("devices") },
                                    onNavigateToFolders = { navController.navigate("folders") },
                                    onNavigateToBackupContacts = { navController.navigate("backupContacts") },
                                    onNavigateToRestoreContacts = { navController.navigate("restoreContacts") }
                                )
                            }
                            composable("devices") {
                                // Placeholder for DevicesScreen
                                Text("Devices Screen", modifier = Modifier.fillMaxSize())
                                // DevicesScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("folders") {
                                // Placeholder for FoldersScreen
                                Text("Folders Screen", modifier = Modifier.fillMaxSize())
                                // FoldersScreen(onNavigateBack = { navController.popBackStack() })
                                }
                                composable("backupContacts") {
                                    BackupContactsScreen()
                                }
                                composable("restoreContacts") {
                                    RestoreContactsScreen()
                                }
                                }
                                }
                                }
                                }
                                }
                                }

    override fun onDestroy() {
        super.onDestroy()
        mdnsService.stopDiscovery()
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    OfflineSyncTheme {
        Text("Hello Android!")
    }
}
