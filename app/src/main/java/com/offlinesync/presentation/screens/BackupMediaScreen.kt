package com.offlinesync.presentation.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offlinesync.utils.LanguageManager
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupMediaScreen(
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: BackupMediaViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val strings = getStringsForLanguage(LanguageManager.getCurrentLanguage(context))

    val backupStatus by viewModel.backupStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val backupResult by viewModel.backupResult.collectAsState()

    var includeImages by remember { mutableStateOf(true) }
    var includeVideos by remember { mutableStateOf(true) }
    var includeAudio by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.backupMedia) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = strings.selectMediaTypes,
                style = MaterialTheme.typography.titleMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeImages,
                            onCheckedChange = { includeImages = it }
                        )
                        Text(text = strings.images)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeVideos,
                            onCheckedChange = { includeVideos = it }
                        )
                        Text(text = strings.videos)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeAudio,
                            onCheckedChange = { includeAudio = it }
                        )
                        Text(text = strings.audio)
                    }
                }
            }

            Text(
                text = strings.supportedMessengers,
                style = MaterialTheme.typography.titleMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Telegram, WhatsApp, Viber, Signal, Facebook Messenger, Snapchat, Instagram, Discord, Skype, Line, WeChat")
                }
            }

            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = backupStatus)
                }
            }

            Button(
                onClick = { 
                    viewModel.startBackup(
                        context, 
                        includeImages = includeImages, 
                        includeVideos = includeVideos, 
                        includeAudio = includeAudio
                    ) 
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && (includeImages || includeVideos || includeAudio)
            ) {
                Text(strings.startBackup)
            }

            backupResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.backupResult,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(text = "${strings.totalFiles}: ${result.totalFiles}")
                        Text(text = "${strings.backedUpFiles}: ${result.backedUpFiles}")
                        Text(text = "${strings.skippedFiles}: ${result.skippedFiles}")
                        Text(text = "${strings.backedUpSize}: ${formatFileSize(result.backedUpSize)}")
                        
                        if (result.sourceDetails.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.sourceDetails,
                                style = MaterialTheme.typography.titleSmall
                            )
                            result.sourceDetails.forEach { (source, count) ->
                                Text(text = "$source: $count files", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (!isLoading && backupStatus.isNotEmpty() && backupResult == null) {
                Text(text = backupStatus)
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        size >= 1_000_000_000 -> "${df.format(size / 1_000_000_000.0)} GB"
        size >= 1_000_000 -> "${df.format(size / 1_000_000.0)} MB"
        size >= 1_000 -> "${df.format(size / 1_000.0)} KB"
        else -> "$size B"
    }
}
