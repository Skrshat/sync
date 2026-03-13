package com.offlinesync.presentation.screens

import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import com.offlinesync.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreContactsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    viewModel: RestoreContactsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val strings = getStringsForLanguage(LanguageManager.getCurrentLanguage(context))

    val writeContactsPermissionGranted by viewModel.writeContactsPermissionGranted.collectAsState()
    val restoreStatus by viewModel.restoreStatus.collectAsState()
    val selectedFileUri by viewModel.selectedFileUri.collectAsState()
    val parsedContacts by viewModel.parsedContacts.collectAsState()
    val simSubscriptions by viewModel.simSubscriptions.collectAsState()

    val requestWriteContactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.onPermissionResult(Manifest.permission.WRITE_CONTACTS, isGranted)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.selectBackupFile(uri)
    }

    var selectedRestoreTarget by remember { mutableStateOf(RestoreTarget.DEVICE) }
    var selectedSimSubId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.restoreContacts) },
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { filePickerLauncher.launch("application/json") }) {
                Text(strings.selectBackupFile)
            }
            selectedFileUri?.let { uri ->
                Text("Selected: ${uri.lastPathSegment ?: uri.path}", style = MaterialTheme.typography.bodySmall)
                Text("Contacts found: ${parsedContacts.size}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (!writeContactsPermissionGranted) {
                Button(onClick = {
                    requestWriteContactsPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                }) {
                    Text(strings.requestWriteContactsPermission)
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (parsedContacts.isNotEmpty()) {
                Column(Modifier.selectableGroup()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (selectedRestoreTarget == RestoreTarget.DEVICE),
                                onClick = { selectedRestoreTarget = RestoreTarget.DEVICE },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedRestoreTarget == RestoreTarget.DEVICE),
                            onClick = null
                        )
                        Text(
                            text = strings.restoreToDevice,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (selectedRestoreTarget == RestoreTarget.SIM),
                                onClick = { selectedRestoreTarget = RestoreTarget.SIM },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedRestoreTarget == RestoreTarget.SIM),
                            onClick = null
                        )
                        Text(
                            text = strings.restoreToSim,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                
                if (selectedRestoreTarget == RestoreTarget.SIM && simSubscriptions.isNotEmpty()) {
                    Text(strings.selectSimCard)
                    simSubscriptions.forEach { (subId, displayName) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .selectable(
                                    selected = (selectedSimSubId == subId),
                                    onClick = { selectedSimSubId = subId },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedSimSubId == subId),
                                onClick = null
                            )
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    viewModel.startRestore(selectedRestoreTarget, selectedSimSubId)
                }) {
                    Text(strings.startRestore)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = restoreStatus)
        }
    }
}
