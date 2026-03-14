package com.offlinesync.presentation.screens

import android.Manifest
import android.content.ContentValues
import android.provider.Telephony
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offlinesync.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSmsScreen(
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: BackupSmsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val strings = getStringsForLanguage(LanguageManager.getCurrentLanguage(context))

    val smsPermissionGranted by viewModel.smsPermissionGranted.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val simCards by viewModel.simCards.collectAsState()
    val selectedSimFilter by viewModel.selectedSimFilter.collectAsState()

    val requestSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    LaunchedEffect(smsPermissionGranted) {
        if (smsPermissionGranted) {
            viewModel.loadSimCards(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.backupSms) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!smsPermissionGranted) {
                Button(
                    onClick = { requestSmsPermissionLauncher.launch(Manifest.permission.READ_SMS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.requestSmsPermission)
                }
            } else {
                if (simCards.size > 1) {
                    Text(
                        text = strings.selectSimToBackup,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(Modifier.selectableGroup()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (selectedSimFilter == null),
                                    onClick = { viewModel.setSimFilter(null) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedSimFilter == null),
                                onClick = null
                            )
                            Text(
                                text = strings.allSimCards,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }

                        simCards.forEach { sim ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .selectable(
                                        selected = (selectedSimFilter == sim.subId),
                                        onClick = { viewModel.setSimFilter(sim.subId) },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedSimFilter == sim.subId),
                                    onClick = null
                                )
                                Column(modifier = Modifier.padding(start = 16.dp)) {
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

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = { viewModel.startBackup(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.startBackup)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = backupStatus)
        }
    }
}
