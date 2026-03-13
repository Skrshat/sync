package com.offlinesync.presentation.screens

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinesync.utils.SimCardInfo
import com.offlinesync.utils.SimCardManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BackupSmsViewModel @Inject constructor() : ViewModel() {

    private val _smsPermissionGranted = MutableStateFlow(false)
    val smsPermissionGranted: StateFlow<Boolean> = _smsPermissionGranted.asStateFlow()

    private val _backupStatus = MutableStateFlow("")
    val backupStatus: StateFlow<String> = _backupStatus.asStateFlow()

    private val _simCards = MutableStateFlow<List<SimCardInfo>>(emptyList())
    val simCards: StateFlow<List<SimCardInfo>> = _simCards.asStateFlow()

    private val _selectedSimFilter = MutableStateFlow<Int?>(null)
    val selectedSimFilter: StateFlow<Int?> = _selectedSimFilter.asStateFlow()

    companion object {
        private const val TAG = "BackupSmsViewModel"
    }

    fun loadSimCards(context: Context) {
        viewModelScope.launch {
            _simCards.value = SimCardManager.getSimCards(context)
        }
    }

    fun setSimFilter(subId: Int?) {
        _selectedSimFilter.value = subId
    }

    fun onPermissionResult(granted: Boolean) {
        _smsPermissionGranted.value = granted
    }

    fun startBackup(context: Context) {
        if (_smsPermissionGranted.value) {
            viewModelScope.launch {
                try {
                    _backupStatus.value = "Exporting SMS..."
                    val result = exportSms(context)
                    _backupStatus.value = result
                } catch (e: Exception) {
                    Log.e(TAG, "Error backing up SMS: ${e.message}")
                    _backupStatus.value = "Error: ${e.message}"
                }
            }
        }
    }

    private suspend fun exportSms(context: Context): String = withContext(Dispatchers.IO) {
        val syncDir = getSyncDirectory(context)
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val smsFile = File(syncDir, "sms_$timestamp.json")

        val smsList = mutableListOf<Map<String, Any>>()

        val projection = arrayOf(
            "_id", "address", "body", "date", "type", "thread_id",
            "service_center", "read", "status", "sub_id"
        )

        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/"),
                projection, null, null, "date DESC"
            )

            if (cursor != null) {
                val addressIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")
                val typeIdx = cursor.getColumnIndex("type")
                val threadIdx = cursor.getColumnIndex("thread_id")
                val scIdx = cursor.getColumnIndex("service_center")
                val readIdx = cursor.getColumnIndex("read")
                val statusIdx = cursor.getColumnIndex("status")
                val subIdIdx = cursor.getColumnIndex("sub_id")

                while (cursor.moveToNext()) {
                    val subId = if (subIdIdx >= 0) cursor.getInt(subIdIdx) else -1
                    
                    if (_selectedSimFilter.value != null && subId != _selectedSimFilter.value) {
                        continue
                    }

                    val sms = mutableMapOf<String, Any>()
                    sms["address"] = cursor.getString(addressIdx) ?: ""
                    sms["body"] = cursor.getString(bodyIdx) ?: ""
                    sms["date"] = cursor.getLong(dateIdx)
                    sms["type"] = cursor.getInt(typeIdx)
                    sms["thread_id"] = cursor.getLong(threadIdx)
                    sms["service_center"] = cursor.getString(scIdx) ?: ""
                    sms["read"] = cursor.getInt(readIdx)
                    sms["status"] = cursor.getInt(statusIdx)
                    sms["sub_id"] = subId
                    smsList.add(sms)
                }
                cursor.close()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SMS permission denied: ${e.message}")
            throw Exception("SMS permission denied")
        }

        val simCards = SimCardManager.getSimCards(context)
        val simInfoList = simCards.map { mapOf(
            "sub_id" to it.subId,
            "display_name" to it.displayName,
            "carrier_name" to it.carrierName,
            "slot_index" to it.slotIndex,
            "icc_id" to it.iccId
        )}
        
        val jsonContent = buildJsonSms(smsList, simInfoList)
        smsFile.writeText(jsonContent)

        "Exported ${smsList.size} SMS messages to ${smsFile.name}"
    }

    private fun buildJsonSms(smsList: List<Map<String, Any>>, simInfoList: List<Map<String, Any>>): String {
        val sb = StringBuilder()
        sb.append("{\"sms_count\":${smsList.size},\"sim_cards\":[")
        simInfoList.forEachIndexed { index, sim ->
            sb.append("{")
            sb.append("\"sub_id\":${sim["sub_id"]},")
            sb.append("\"display_name\":\"${escapeJson(sim["display_name"].toString())}\",")
            sb.append("\"carrier_name\":\"${escapeJson(sim["carrier_name"].toString())}\",")
            sb.append("\"slot_index\":${sim["slot_index"]},")
            sb.append("\"icc_id\":\"${escapeJson(sim["icc_id"].toString())}\"")
            sb.append("}")
            if (index < simInfoList.size - 1) sb.append(",")
        }
        sb.append("],\"sms\":[")
        smsList.forEachIndexed { index, sms ->
            sb.append("{")
            sb.append("\"address\":\"${escapeJson(sms["address"].toString())}\",")
            sb.append("\"body\":\"${escapeJson(sms["body"].toString())}\",")
            sb.append("\"date\":${sms["date"]},")
            sb.append("\"type\":${sms["type"]},")
            sb.append("\"thread_id\":${sms["thread_id"]},")
            sb.append("\"service_center\":\"${escapeJson(sms["service_center"].toString())}\",")
            sb.append("\"read\":${sms["read"]},")
            sb.append("\"status\":${sms["status"]},")
            sb.append("\"sub_id\":${sms["sub_id"]}")
            sb.append("}")
            if (index < smsList.size - 1) sb.append(",")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun getSyncDirectory(context: Context): File {
        val syncDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OfflineSync")
        if (!syncDir.exists()) {
            syncDir.mkdirs()
        }
        return syncDir
    }
}
