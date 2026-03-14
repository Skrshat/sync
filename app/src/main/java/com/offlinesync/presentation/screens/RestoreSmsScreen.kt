package com.offlinesync.presentation.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.offlinesync.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreSmsScreen(
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: RestoreSmsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val strings = getStringsForLanguage(LanguageManager.getCurrentLanguage(context))

    val smsPermissionGranted by viewModel.smsPermissionGranted.collectAsState()
    val restoreStatus by viewModel.restoreStatus.collectAsState()
    val selectedFileUri by viewModel.selectedFileUri.collectAsState()
    val parsedSms by viewModel.parsedSms.collectAsState()
    val simCards by viewModel.simCards.collectAsState()
    val backupSimInfo by viewModel.backupSimInfo.collectAsState()
    val selectedTargetSim by viewModel.selectedTargetSim.collectAsState()
    val selectedSourceSimFilter by viewModel.selectedSourceSimFilter.collectAsState()

    val requestSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.selectBackupFile(context, it) }
    }

    var selectedRestoreMode by remember { mutableStateOf(RestoreSmsMode.APPEND) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.restoreSms) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(48.dp))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { filePickerLauncher.launch("application/json") }) {
                Text(strings.selectBackupFile)
            }

            selectedFileUri?.let { uri ->
                Text("Selected: ${uri.lastPathSegment ?: uri.path}", style = MaterialTheme.typography.bodySmall)
                Text("SMS found: ${parsedSms.size}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!smsPermissionGranted) {
                Button(onClick = { requestSmsPermissionLauncher.launch(Manifest.permission.READ_SMS) }) {
                    Text(strings.requestSmsPermission)
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (parsedSms.isNotEmpty()) {
                
                if (backupSimInfo.size > 1) {
                    Text(
                        text = strings.filterBySourceSim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.selectableGroup().padding(8.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .selectable(
                                        selected = (selectedSourceSimFilter == null),
                                        onClick = { viewModel.setSourceSimFilter(null) },
                                        role = Role.RadioButton
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedSourceSimFilter == null),
                                    onClick = null
                                )
                                Text(
                                    text = strings.allSourceSims,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            backupSimInfo.forEach { sim ->
                                val count = parsedSms.count { it["sub_id"] == sim.subId }
                                if (count > 0) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .selectable(
                                                selected = (selectedSourceSimFilter == sim.subId),
                                                onClick = { viewModel.setSourceSimFilter(sim.subId) },
                                                role = Role.RadioButton
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = (selectedSourceSimFilter == sim.subId),
                                            onClick = null
                                        )
                                        Column(modifier = Modifier.padding(start = 8.dp)) {
                                            Text(
                                                text = sim.displayName,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = "$count SMS",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (simCards.size > 1) {
                    Text(
                        text = strings.selectTargetSim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.selectableGroup().padding(8.dp)) {
                            simCards.forEach { sim ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .selectable(
                                            selected = (selectedTargetSim?.subId == sim.subId),
                                            onClick = { viewModel.setTargetSim(sim) },
                                            role = Role.RadioButton
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (selectedTargetSim?.subId == sim.subId),
                                        onClick = null
                                    )
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        Text(
                                            text = sim.displayName,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = sim.carrierName,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Column(Modifier.selectableGroup()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (selectedRestoreMode == RestoreSmsMode.APPEND),
                                onClick = { selectedRestoreMode = RestoreSmsMode.APPEND },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedRestoreMode == RestoreSmsMode.APPEND),
                            onClick = null
                        )
                        Text(
                            text = strings.appendSms,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (selectedRestoreMode == RestoreSmsMode.REPLACE),
                                onClick = { selectedRestoreMode = RestoreSmsMode.REPLACE },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedRestoreMode == RestoreSmsMode.REPLACE),
                            onClick = null
                        )
                        Text(
                            text = strings.replaceSms,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.startRestore(selectedRestoreMode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.startRestore)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = restoreStatus)
        }
    }
}

enum class RestoreSmsMode {
    APPEND,
    REPLACE
}
