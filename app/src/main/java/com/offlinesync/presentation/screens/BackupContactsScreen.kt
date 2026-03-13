package com.offlinesync.presentation.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.Manifest // Added for permissions
import androidx.activity.result.contract.ActivityResultContracts // Added for rememberLauncherForActivityResult
import androidx.compose.runtime.remember // Added for rememberLauncherForActivityResult
import androidx.activity.compose.rememberLauncherForActivityResult // Added for rememberLauncherForActivityResult
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun BackupContactsScreen(viewModel: BackupContactsViewModel = hiltViewModel()) {
    val context = LocalContext.current

    val contactsPermissionGranted by viewModel.contactsPermissionGranted.collectAsState()
    val phoneStatePermissionGranted by viewModel.phoneStatePermissionGranted.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()

    val allPermissionsGranted = contactsPermissionGranted && phoneStatePermissionGranted

    val requestContactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.onPermissionResult(Manifest.permission.READ_CONTACTS, isGranted)
    }

    val requestPhoneStatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.onPermissionResult(Manifest.permission.READ_PHONE_STATE, isGranted)
    }


    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Contact Backup Feature")
        Spacer(modifier = Modifier.height(16.dp))

        if (allPermissionsGranted) {
            Button(onClick = { viewModel.startBackup() }) {
                Text("Start Backup")
            }
        } else {
            // Request missing permissions
            if (!contactsPermissionGranted) {
                Button(onClick = {
                    requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }) {
                    Text("Request Contacts Permission")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (!phoneStatePermissionGranted) {
                Button(onClick = {
                    requestPhoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                }) {
                    Text("Request Phone State Permission")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = backupStatus)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBackupContactsScreen() {
    BackupContactsScreen()
}
