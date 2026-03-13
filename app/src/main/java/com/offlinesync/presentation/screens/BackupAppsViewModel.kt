package com.offlinesync.presentation.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val installTime: Long,
    val updateTime: Long
)

@HiltViewModel
class BackupAppsViewModel @Inject constructor() : ViewModel() {

    private val _backupStatus = MutableStateFlow("")
    val backupStatus: StateFlow<String> = _backupStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    companion object {
        private const val TAG = "BackupAppsViewModel"
    }

    fun startBackup(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _backupStatus.value = "Scanning installed apps..."
                val result = exportApps(context)
                _backupStatus.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Error backing up apps: ${e.message}")
                _backupStatus.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun exportApps(context: Context): String = withContext(Dispatchers.IO) {
        val syncDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OfflineSync")
        if (!syncDir.exists()) {
            syncDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val appsFile = File(syncDir, "apps_$timestamp.json")

        val appsList = mutableListOf<Map<String, Any>>()

        val packages = context.packageManager.getInstalledPackages(PackageManager.GET_META_DATA)

        for (packageInfo in packages) {
            try {
                val appInfo = packageInfo.applicationInfo
                val isSystemApp = (appInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0

                val appName = try {
                    appInfo?.let { context.packageManager.getApplicationLabel(it).toString() } ?: packageInfo.packageName
                } catch (e: Exception) {
                    packageInfo.packageName
                }

                val app = mutableMapOf<String, Any>()
                app["package_name"] = packageInfo.packageName
                app["app_name"] = appName
                app["version_name"] = packageInfo.versionName ?: ""
                app["version_code"] = packageInfo.longVersionCode
                app["is_system_app"] = isSystemApp
                app["install_time"] = packageInfo.firstInstallTime
                app["update_time"] = packageInfo.lastUpdateTime
                appsList.add(app)
            } catch (e: Exception) {
                Log.w(TAG, "Could not get info for package: ${packageInfo.packageName}")
            }
        }

        val sortedApps = appsList.sortedBy { (it["app_name"] as String).lowercase() }

        val jsonContent = buildJsonApps(sortedApps)
        appsFile.writeText(jsonContent)

        val systemApps = sortedApps.count { it["is_system_app"] as Boolean }
        val userApps = sortedApps.size - systemApps

        "Exported ${sortedApps.size} apps ($userApps user, $systemApps system) to ${appsFile.name}"
    }

    private fun buildJsonApps(appsList: List<Map<String, Any>>): String {
        val sb = StringBuilder()
        sb.append("{\"apps_count\":${appsList.size},\"apps\":[")

        appsList.forEachIndexed { index, app ->
            sb.append("{")
            sb.append("\"package_name\":\"${escapeJson(app["package_name"].toString())}\",")
            sb.append("\"app_name\":\"${escapeJson(app["app_name"].toString())}\",")
            sb.append("\"version_name\":\"${escapeJson(app["version_name"].toString())}\",")
            sb.append("\"version_code\":${app["version_code"]},")
            sb.append("\"is_system_app\":${app["is_system_app"]},")
            sb.append("\"install_time\":${app["install_time"]},")
            sb.append("\"update_time\":${app["update_time"]}")
            sb.append("}")
            if (index < appsList.size - 1) sb.append(",")
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
}
