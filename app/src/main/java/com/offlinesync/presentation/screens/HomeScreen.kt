package com.offlinesync.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding // Added for Modifier.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues // Added for paddingValues

@Composable
fun HomeScreen(
    paddingValues: PaddingValues, // Added parameter
    onNavigateToDevices: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToBackupContacts: () -> Unit,
    onNavigateToRestoreContacts: () -> Unit // Added for restore
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues), // Applied padding
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Home Screen")
        Button(onClick = onNavigateToDevices) {
            Text("Go to Devices")
        }
        Button(onClick = onNavigateToFolders) {
            Text("Go to Folders")
        }
        Button(onClick = onNavigateToBackupContacts) {
            Text("Backup Contacts")
        }
        Button(onClick = onNavigateToRestoreContacts) {
            Text("Restore Contacts")
        }
        }
        }
