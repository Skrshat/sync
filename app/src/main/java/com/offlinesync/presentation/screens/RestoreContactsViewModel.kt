package com.offlinesync.presentation.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinesync.utils.Contact
import com.offlinesync.utils.ContactManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

enum class RestoreTarget {
    DEVICE, SIM
}

@HiltViewModel
class RestoreContactsViewModel @Inject constructor(
    private val contactManager: ContactManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _writeContactsPermissionGranted = MutableStateFlow(contactManager.hasWriteContactsPermission())
    val writeContactsPermissionGranted: StateFlow<Boolean> = _writeContactsPermissionGranted

    private val _restoreStatus = MutableStateFlow("Ready to restore contacts.")
    val restoreStatus: StateFlow<String> = _restoreStatus

    private val _selectedFileUri = MutableStateFlow<Uri?>(null)
    val selectedFileUri: StateFlow<Uri?> = _selectedFileUri

    private val _parsedContacts = MutableStateFlow<List<Contact>>(emptyList())
    val parsedContacts: StateFlow<List<Contact>> = _parsedContacts

    private val _simSubscriptions = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val simSubscriptions: StateFlow<List<Pair<Int, String>>> = _simSubscriptions

    init {
        viewModelScope.launch {
            contactManager.writeContactsPermissionGranted.collect { isGranted ->
                _writeContactsPermissionGranted.value = isGranted
            }
        }
        // Load SIM subscriptions if phone state permission is granted
        viewModelScope.launch {
            contactManager.phoneStatePermissionGranted.collect { isGranted ->
                if (isGranted) {
                    _simSubscriptions.value = contactManager.getSimSubscriptions()
                } else {
                    _simSubscriptions.value = emptyList()
                }
            }
        }
    }

    fun onPermissionResult(permission: String, isGranted: Boolean) {
        contactManager.updatePermissionStatus(permission, isGranted)
    }
    fun selectBackupFile(uri: Uri?) {
        _selectedFileUri.value = uri
        if (uri != null) {
            parseJsonFile(uri)
        } else {
            _parsedContacts.value = emptyList()
            _restoreStatus.value = "No file selected."
        }
    }

    private fun parseJsonFile(uri: Uri) {
        _restoreStatus.value = "Parsing file..."
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.use { it.readText() }
                val contacts = Json.decodeFromString<List<Contact>>(jsonString)
                _parsedContacts.value = contacts
                _restoreStatus.value = "Found ${contacts.size} contacts in backup file."
            } catch (e: Exception) {
                _restoreStatus.value = "Failed to parse JSON file: ${e.message}"
                _parsedContacts.value = emptyList()
                e.printStackTrace()
            }
        }
    }

    fun startRestore(target: RestoreTarget, subId: Int? = null) {
        if (!writeContactsPermissionGranted.value) {
            _restoreStatus.value = "WRITE_CONTACTS permission not granted."
            return
        }
        if (parsedContacts.value.isEmpty()) {
            _restoreStatus.value = "No contacts to restore."
            return
        }

        _restoreStatus.value = "Restoring contacts..."
        viewModelScope.launch {
            val contactsToRestore = _parsedContacts.value
            val restoredCount = when (target) {
                RestoreTarget.DEVICE -> contactManager.writeContactsToDevice(contactsToRestore)
                RestoreTarget.SIM -> {
                    if (subId == null && simSubscriptions.value.size > 1) {
                        _restoreStatus.value = "Please select a SIM card for restore."
                        0
                    } else {
                        var count = 0
                        val targetSubId = subId ?: simSubscriptions.value.firstOrNull()?.first
                        if (targetSubId != null) {
                            for (contact in contactsToRestore) {
                                if (contactManager.writeContactToSim(contact, targetSubId)) {
                                    count++
                                }
                            }
                        }
                        count
                    }
                }
            }
            _restoreStatus.value = "Restored $restoredCount contacts to $target."
        }
    }
}
