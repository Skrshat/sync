package com.offlinesync.presentation.screens

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateHome: () -> Unit = {}
) {
    val rootPath = remember {
        Environment.getExternalStorageDirectory()
    }
    
    var currentPath by remember { mutableStateOf(rootPath) }
    val files = remember(currentPath) {
        currentPath.listFiles()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        )?.toList() ?: emptyList()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = currentPath.absolutePath.takeLast(50),
                        maxLines = 1
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        onNavigateHome()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        onNavigateHome()
                    }) {
                        Icon(
                            Icons.Default.Home, 
                            contentDescription = "Home",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentPath.absolutePath != rootPath.absolutePath) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                currentPath = currentPath.parentFile ?: rootPath 
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Parent",
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = ".. (${currentPath.parentFile?.name ?: "root"})",
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
            
            items(files) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (file.isDirectory) {
                                currentPath = file
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            file.isDirectory -> Icons.Default.Folder
                            file.name.endsWith(".txt", ignoreCase = true) -> Icons.Default.TextSnippet
                            file.name.endsWith(".json", ignoreCase = true) -> Icons.Default.TextSnippet
                            else -> Icons.Default.FolderOpen
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(text = file.name)
                        if (file.isFile) {
                            Text(
                                text = formatFileSize(file.length()),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            if (files.isEmpty()) {
                item {
                    Text(
                        text = "Empty folder",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}
