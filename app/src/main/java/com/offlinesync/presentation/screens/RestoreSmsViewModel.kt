package com.offlinesync.presentation.screens

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinesync.utils.SimCardInfo
import com.offlinesync.utils.SimCardManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

data class BackupSimInfo(
    val subId: Int,
    val displayName: String,
    val carrierName: String,
    val slotIndex: Int,
    val iccId: String
)

@HiltViewModel
class RestoreSmsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _smsPermissionGranted = MutableStateFlow(false)
    val smsPermissionGranted: StateFlow<Boolean> = _smsPermissionGranted.asStateFlow()

    private val _restoreStatus = MutableStateFlow("")
    val restoreStatus: StateFlow<String> = _restoreStatus.asStateFlow()

    private val _selectedFileUri = MutableStateFlow<Uri?>(null)
    val selectedFileUri: StateFlow<Uri?> = _selectedFileUri.asStateFlow()

    private val _parsedSms = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val parsedSms: StateFlow<List<Map<String, Any>>> = _parsedSms.asStateFlow()

    private val _simCards = MutableStateFlow<List<SimCardInfo>>(emptyList())
    val simCards: StateFlow<List<SimCardInfo>> = _simCards.asStateFlow()

    private val _backupSimInfo = MutableStateFlow<List<BackupSimInfo>>(emptyList())
    val backupSimInfo: StateFlow<List<BackupSimInfo>> = _backupSimInfo.asStateFlow()

    private val _selectedTargetSim = MutableStateFlow<SimCardInfo?>(null)
    val selectedTargetSim: StateFlow<SimCardInfo?> = _selectedTargetSim.asStateFlow()

    private val _selectedSourceSimFilter = MutableStateFlow<Int?>(null)
    val selectedSourceSimFilter: StateFlow<Int?> = _selectedSourceSimFilter.asStateFlow()

    companion object {
        private const val TAG = "RestoreSmsViewModel"
    }

    init {
        loadSimCards()
    }

    private fun loadSimCards() {
        viewModelScope.launch {
            _simCards.value = SimCardManager.getSimCards(context)
            if (_simCards.value.isNotEmpty()) {
                _selectedTargetSim.value = _simCards.value.firstOrNull()
            }
        }
    }

    fun setTargetSim(simCardInfo: SimCardInfo?) {
        _selectedTargetSim.value = simCardInfo
    }

    fun setSourceSimFilter(subId: Int?) {
        _selectedSourceSimFilter.value = subId
        updateFilteredSms()
    }

    private fun updateFilteredSms() {
        viewModelScope.launch {
            val allSms = _parsedSms.value
            val filter = _selectedSourceSimFilter.value
            if (filter != null) {
                _restoreStatus.value = "Filtered: ${allSms.count { it["sub_id"] == filter }} SMS from selected SIM"
            } else {
                _restoreStatus.value = "Found ${allSms.size} SMS messages"
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _smsPermissionGranted.value = granted
    }

    fun selectBackupFile(context: Context, uri: Uri) {
        _selectedFileUri.value = uri
        viewModelScope.launch {
            try {
                val (smsList, simInfoList) = parseSmsFile(context, uri)
                _parsedSms.value = smsList
                _backupSimInfo.value = simInfoList
                _restoreStatus.value = "Found ${smsList.size} SMS messages from ${simInfoList.size} SIM(s)"
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing SMS file: ${e.message}")
                _restoreStatus.value = "Error: ${e.message}"
            }
        }
    }

    private suspend fun parseSmsFile(context: Context, uri: Uri): Pair<List<Map<String, Any>>, List<BackupSimInfo>> = withContext(Dispatchers.IO) {
        val smsList = mutableListOf<Map<String, Any>>()
        val simInfoList = mutableListOf<BackupSimInfo>()

        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonContent = reader.readText()
        reader.close()

        val jsonObject = JSONObject(jsonContent)
        
        if (jsonObject.has("sim_cards")) {
            val simArray = jsonObject.getJSONArray("sim_cards")
            for (i in 0 until simArray.length()) {
                val simObj = simArray.getJSONObject(i)
                simInfoList.add(
                    BackupSimInfo(
                        subId = simObj.getInt("sub_id"),
                        displayName = simObj.getString("display_name"),
                        carrierName = simObj.getString("carrier_name"),
                        slotIndex = simObj.getInt("slot_index"),
                        iccId = simObj.optString("icc_id", "")
                    )
                )
            }
        }
        
        val smsArray = jsonObject.getJSONArray("sms")

        for (i in 0 until smsArray.length()) {
            val smsObj = smsArray.getJSONObject(i)
            val sms = mapOf(
                "address" to smsObj.getString("address"),
                "body" to smsObj.getString("body"),
                "date" to smsObj.getLong("date"),
                "type" to smsObj.getInt("type"),
                "thread_id" to smsObj.getLong("thread_id"),
                "service_center" to smsObj.optString("service_center", ""),
                "read" to smsObj.optInt("read", 1),
                "status" to smsObj.optInt("status", -1),
                "sub_id" to smsObj.optInt("sub_id", -1)
            )
            smsList.add(sms)
        }

        Pair(smsList, simInfoList)
    }

    fun getFilteredSms(): List<Map<String, Any>> {
        val filter = _selectedSourceSimFilter.value
        return if (filter != null) {
            _parsedSms.value.filter { it["sub_id"] == filter }
        } else {
            _parsedSms.value
        }
    }

    fun startRestore(mode: RestoreSmsMode) {
        if (_smsPermissionGranted.value && _parsedSms.value.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    val smsToRestore = getFilteredSms()
                    val result = restoreSms(smsToRestore, mode)
                    _restoreStatus.value = result
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring SMS: ${e.message}")
                    _restoreStatus.value = "Error: ${e.message}"
                }
            }
        }
    }

    private suspend fun restoreSms(smsList: List<Map<String, Any>>, mode: RestoreSmsMode): String = withContext(Dispatchers.IO) {
        var inserted = 0

        if (mode == RestoreSmsMode.REPLACE) {
            try {
                context.contentResolver.delete(Uri.parse("content://sms/"), null, null)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not delete existing SMS: ${e.message}")
            }
        }

        val targetSubId = _selectedTargetSim.value?.subId ?: -1

        for (sms in smsList) {
            try {
                val values = ContentValues().apply {
                    put("address", sms["address"].toString())
                    put("body", sms["body"].toString())
                    put("date", sms["date"] as Long)
                    put("type", sms["type"] as Int)
                    put("thread_id", sms["thread_id"] as Long)
                    put("service_center", sms["service_center"].toString())
                    put("read", sms["read"] as Int)
                    put("status", sms["status"] as Int)
                    if (targetSubId > 0) {
                        put("sub_id", targetSubId)
                    }
                }
                context.contentResolver.insert(Uri.parse("content://sms/"), values)
                inserted++
            } catch (e: Exception) {
                Log.w(TAG, "Could not insert SMS: ${e.message}")
            }
        }

        "Restored $inserted SMS messages"
    }
}
