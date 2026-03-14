package com.offlinesync.presentation

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.offlinesync.presentation.screens.FoldersScreen
import com.offlinesync.presentation.screens.HomeScreen
import com.offlinesync.presentation.screens.BackupContactsScreen
import com.offlinesync.presentation.screens.RestoreContactsScreen
import com.offlinesync.presentation.screens.BackupSmsScreen
import com.offlinesync.presentation.screens.RestoreSmsScreen
import com.offlinesync.presentation.screens.BackupAppsScreen
import com.offlinesync.presentation.screens.BackupMediaScreen

import com.offlinesync.presentation.screens.SettingsScreen
import com.offlinesync.presentation.theme.OfflineSyncTheme
import com.offlinesync.utils.LanguageManager
import dagger.hilt.android.AndroidEntryPoint
import com.offlinesync.service.MDNSService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var mdnsService: MDNSService

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.setLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mdnsService = MDNSService(this)
        mdnsService.startDiscovery()

        lifecycleScope.launch {
            mdnsService.discoveredServices.collect { serviceInfo ->
                Log.d("MDNSService", "Discovered: $serviceInfo")
            }
        }

        enableEdgeToEdge()
        setContent {
            OfflineSyncTheme {
                val navController = rememberNavController()
                
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize()
                ) {
                        composable("home") {
                            HomeScreen(
                                onNavigateToFolders = { navController.navigate("folders") },
                                onNavigateToBackupContacts = { navController.navigate("backupContacts") },
                                onNavigateToRestoreContacts = { navController.navigate("restoreContacts") },
                                onNavigateToBackupSms = { navController.navigate("backupSms") },
                                onNavigateToRestoreSms = { navController.navigate("restoreSms") },
                                onNavigateToBackupApps = { navController.navigate("backupApps") },
                                onNavigateToBackupMedia = { navController.navigate("backupMedia") },

                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("folders") {
                            FoldersScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateHome = { navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                } }
                            )
                        }
                        composable("backupContacts") {
                            BackupContactsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateHome = { navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                } }
                            )
                        }
                        composable("restoreContacts") {
                            RestoreContactsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateHome = { navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                } }
                            )
                        }
                        composable("backupSms") {
                            BackupSmsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateHome = { navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                } }
                            )
                        }
                        composable("restoreSms") {
                            RestoreSmsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateHome = { navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                } }
                            )
                        }
                        composable("backupApps") {
                            BackupAppsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateHome = { navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                } }
                            )
                        }
                        composable("backupMedia") {
                            BackupMediaScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateHome = { navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                } }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateHome = { navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                } }
                            )
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
