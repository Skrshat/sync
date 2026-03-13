package com.offlinesync.presentation.screens

import android.Manifest
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinesync.utils.ContactManager
import com.offlinesync.utils.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BackupContactsViewModel @Inject constructor(
    private val contactManager: ContactManager
) : ViewModel() {

    private val _contactsPermissionGranted = MutableStateFlow(contactManager.hasContactsPermission())
    val contactsPermissionGranted: StateFlow<Boolean> = _contactsPermissionGranted

    private val _phoneStatePermissionGranted = MutableStateFlow(contactManager.hasPhoneStatePermission())
    val phoneStatePermissionGranted: StateFlow<Boolean> = _phoneStatePermissionGranted

    private val _backupStatus = MutableStateFlow("Ready to backup contacts.")
    val backupStatus: StateFlow<String> = _backupStatus

    init {
        viewModelScope.launch {
            contactManager.contactsPermissionGranted.collect { isGranted ->
                _contactsPermissionGranted.value = isGranted
            }
        }
        viewModelScope.launch {
            contactManager.phoneStatePermissionGranted.collect { isGranted ->
                _phoneStatePermissionGranted.value = isGranted
            }
        }
    }

    fun onPermissionResult(permission: String, isGranted: Boolean) {
        when (permission) {
            Manifest.permission.READ_CONTACTS -> _contactsPermissionGranted.value = isGranted
            Manifest.permission.READ_PHONE_STATE -> _phoneStatePermissionGranted.value = isGranted
        }
    }

    fun startBackup() {
        _backupStatus.value = "Backing up contacts..."
        viewModelScope.launch {
            val deviceContacts = contactManager.getDeviceContacts()
            val simContacts = contactManager.getSimContacts()
            val allContacts = deviceContacts + simContacts

            if (allContacts.isNotEmpty()) {
                try {
                    val jsonString = Json.encodeToString(allContacts)
                    val backupDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "OfflineSync/Backups"
                    )
                    if (!backupDir.exists()) {
                        backupDir.mkdirs()
                    }
                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                    val backupFile = File(backupDir, "contacts_$timestamp.json")
                    backupFile.writeText(jsonString)
                    _backupStatus.value = "Backup successful! Saved to ${backupFile.name}"
                } catch (e: Exception) {
                    _backupStatus.value = "Backup failed: ${e.message}"
                }
            } else {
                _backupStatus.value = "No device or SIM contacts found or read permission denied."
            }
        }
    }
}
