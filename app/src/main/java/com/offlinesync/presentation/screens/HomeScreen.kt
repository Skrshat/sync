package com.offlinesync.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.offlinesync.R

@Composable
fun HomeScreen(
    onNavigateToFolders: () -> Unit,
    onNavigateToBackupContacts: () -> Unit,
    onNavigateToRestoreContacts: () -> Unit,
    onNavigateToBackupSms: () -> Unit,
    onNavigateToRestoreSms: () -> Unit,
    onNavigateToBackupApps: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val strings = getStringsForLanguage(com.offlinesync.utils.LanguageManager.getCurrentLanguage(context))
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MenuButton(
            text = stringResource(R.string.browse_files),
            icon = Icons.Default.FolderOpen,
            onClick = onNavigateToFolders
        )
        MenuButton(
            text = stringResource(R.string.backup_contacts),
            icon = Icons.Default.Folder,
            onClick = onNavigateToBackupContacts
        )
        MenuButton(
            text = stringResource(R.string.restore_contacts),
            icon = Icons.Default.Folder,
            onClick = onNavigateToRestoreContacts
        )
        
        MenuButton(
            text = strings.backupSms,
            icon = Icons.Default.Message,
            onClick = onNavigateToBackupSms
        )
        MenuButton(
            text = strings.restoreSms,
            icon = Icons.Default.Message,
            onClick = onNavigateToRestoreSms
        )

        MenuButton(
            text = strings.backupApps,
            icon = Icons.Default.Apps,
            onClick = onNavigateToBackupApps
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        MenuButton(
            text = stringResource(R.string.settings),
            icon = Icons.Default.Settings,
            onClick = onNavigateToSettings
        )
    }
}

@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = text,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}
