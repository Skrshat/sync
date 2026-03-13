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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult // Added for rememberLauncherForActivityResult


@Composable
fun RestoreContactsScreen(viewModel: RestoreContactsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

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


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Restore Contacts", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { filePickerLauncher.launch("application/json") }) {
            Text("Select Backup File")
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
                Text("Request Write Contacts Permission")
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else if (parsedContacts.isNotEmpty()) {            // Restore options
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
                        onClick = null // Param is null, relying on the Row's onClick
                    )
                    Text(
                        text = "Restore to Device",
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
                        text = "Restore to SIM",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            
            if (selectedRestoreTarget == RestoreTarget.SIM && simSubscriptions.isNotEmpty()) {
                Text("Select SIM Card:")
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
                Text("Start Restore")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = restoreStatus)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewRestoreContactsScreen() {
    RestoreContactsScreen()
}
