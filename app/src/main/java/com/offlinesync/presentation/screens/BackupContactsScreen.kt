package com.offlinesync.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.offlinesync.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupContactsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    viewModel: BackupContactsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val strings = getStringsForLanguage(LanguageManager.getCurrentLanguage(context))
    
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.backupContacts) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (allPermissionsGranted) {
                Button(onClick = { viewModel.startBackup() }) {
                    Text(strings.startBackup)
                }
            } else {
                if (!contactsPermissionGranted) {
                    Button(
                        onClick = { requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.requestContactsPermission)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (!phoneStatePermissionGranted) {
                    Button(
                        onClick = { requestPhoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.requestPhoneStatePermission)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = backupStatus)
        }
    }
}
