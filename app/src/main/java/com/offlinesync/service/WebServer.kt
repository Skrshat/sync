package com.offlinesync.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.telephony.SmsManager
import android.util.Log
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.ContentType
import io.ktor.server.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebServer(private val context: Context, private val serverPort: Int = 8080) {

    val port: Int get() = serverPort

    private var server: EmbeddedServer<*, *>? = null

    private fun getSyncDirectory(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val syncDir = File(downloadsDir, "OfflineSync")
        if (!syncDir.exists()) {
            syncDir.mkdirs()
        }
        Log.d(TAG, "Sync directory: ${syncDir.absolutePath}")
        return syncDir
    }

    private fun getRootDirectory(): File {
        return Environment.getExternalStorageDirectory()
    }

    fun start(address: InetAddress) {
        if (server != null) {
            Log.d(TAG, "Server already running, stopping before restarting.")
            stop()
        }

        server = embeddedServer(Netty, port = serverPort, host = address.hostAddress ?: "0.0.0.0") {
            routing {
                get("/") {
                    call.respondText("OfflineSync Android Server", ContentType.Text.Plain)
                }

                get("/ping") {
                    call.respondText("OK", ContentType.Text.Plain)
                }

                // Get list of installed apps (user-installed only)
                get("/apps") {
                    try {
                        val pm = context.packageManager
                        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        val myPackage = context.packageName
                        
                        val appsList = apps
                            .filter { app ->
                                app.packageName != myPackage &&
                                ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                                 (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                            }
                            .filter { app ->
                                pm.getLaunchIntentForPackage(app.packageName) != null
                            }
                            .map { app ->
                                val appName = pm.getApplicationLabel(app).toString()
                                val packageName = app.packageName
                                "{\"name\":\"$appName\",\"package\":\"$packageName\"}"
                            }
                            .distinctBy { it }
                            .sortedBy { it }
                        
                        val json = "{\"apps\":[${appsList.joinToString(",")}]}"
                        Log.d(TAG, "=== Apps: ${appsList.size} ===")
                        call.respondText(json, ContentType.Application.Json)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting apps: ${e.message}")
                        call.respondText("Error: ${e.message}", status = HttpStatusCode.InternalServerError)
                    }
                }

                // Export all SMS messages
                get("/sms") {
                    try {
                        val syncDir = getSyncDirectory()
                        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                        val smsFile = File(syncDir, "sms_$timestamp.txt")
                        
                        val smsContent = StringBuilder()
                        smsContent.append("SMS Messages Export\n")
                        smsContent.append("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                        smsContent.append("=".repeat(50) + "\n\n")
                        
                        try {
                            val projection = arrayOf("_id", "address", "body", "date", "type")
                            val cursor = context.contentResolver.query(
                                android.net.Uri.parse("content://sms/"),
                                projection, null, null, "date DESC"
                            )
                            
                            if (cursor != null) {
                                val addressIdx = cursor.getColumnIndex("address")
                                val bodyIdx = cursor.getColumnIndex("body")
                                val dateIdx = cursor.getColumnIndex("date")
                                val typeIdx = cursor.getColumnIndex("type")
                                
                                while (cursor.moveToNext()) {
                                    val address = cursor.getString(addressIdx) ?: "Unknown"
                                    val body = cursor.getString(bodyIdx) ?: ""
                                    val date = cursor.getLong(dateIdx)
                                    val type = cursor.getInt(typeIdx)
                                    val typeStr = when (type) {
                                        1 -> "INBOX"
                                        2 -> "SENT"
                                        else -> "OTHER"
                                    }
                                    smsContent.append("[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(date))}] $typeStr\n")
                                    smsContent.append("From: $address\n")
                                    smsContent.append("$body\n\n")
                                }
                                cursor.close()
                            }
                        } catch (e: SecurityException) {
                            smsContent.append("SMS permission not granted.\n")
                            Log.e(TAG, "SMS permission denied: ${e.message}")
                        }
                        
                        smsFile.writeText(smsContent.toString())
                        Log.d(TAG, "SMS exported to: ${smsFile.absolutePath}")
                        
                        call.respondText("{\"status\":\"ok\",\"file\":\"${smsFile.name}\",\"path\":\"${smsFile.absolutePath}\"}", ContentType.Application.Json)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error exporting SMS: ${e.message}")
                        call.respondText("{\"status\":\"error\",\"message\":\"${e.message}\"}", ContentType.Application.Json)
                    }
                }

                post("/upload") {
                    val multipart = call.receiveMultipart()
                    val syncDir = getSyncDirectory()
                    
                    // Get action parameter (overwrite or rename)
                    val action = call.parameters["action"] ?: "rename"
                    
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                var fileName = part.originalFileName ?: "unknown_file"
                                val file = File(syncDir, fileName)
                                
                                // Handle existing file
                                if (file.exists()) {
                                    when (action) {
                                        "overwrite" -> {
                                            // Just proceed to overwrite
                                        }
                                        "rename" -> {
                                            // Find new name
                                            var counter = 1
                                            val baseName = fileName.substringBeforeLast(".")
                                            val ext = fileName.substringAfterLast(".", "")
                                            while (file.exists()) {
                                                fileName = if (ext.isNotEmpty()) {
                                                    "${baseName}_$counter.$ext"
                                                } else {
                                                    "${baseName}_$counter"
                                                }
                                                counter++
                                            }
                                        }
                                        else -> {
                                            call.respondText("File exists: $fileName", status = HttpStatusCode.Conflict)
                                            return@forEachPart
                                        }
                                    }
                                }
                                
                                val finalFile = File(syncDir, fileName)
                                finalFile.parentFile?.mkdirs()
                                Log.d(TAG, "Saving file to: ${finalFile.absolutePath}")
                                
                                withContext(Dispatchers.IO) {
                                    part.streamProvider().use { input ->
                                        FileOutputStream(finalFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                                Log.d(TAG, "Received file: ${finalFile.absolutePath}")
                                call.respondText("{\"status\":\"ok\",\"filename\":\"$fileName\"}", ContentType.Application.Json)
                            }
                            else -> {}
                        }
                        part.dispose()
                    }
                }

                // Check if file exists
                get("/exists") {
                    val path = call.parameters["path"] ?: ""
                    if (path.isEmpty()) {
                        call.respondText("{\"exists\":false}", ContentType.Application.Json)
                        return@get
                    }
                    
                    val syncDir = getSyncDirectory()
                    val file = File(syncDir, path)
                    val exists = file.exists()
                    
                    Log.d(TAG, "Check exists: $path -> $exists")
                    call.respondText("{\"exists\":$exists,\"path\":\"$path\"}", ContentType.Application.Json)
                }

                // Download with query parameter
                get("/download") {
                    val uri = call.request.uri
                    Log.d(TAG, "=== DOWNLOAD REQUEST URI: $uri ===")
                    
                    var pathInfo = ""
                    
                    val queryParams = call.request.queryParameters
                    if (queryParams.contains("path")) {
                        pathInfo = queryParams["path"] ?: ""
                    } else {
                        pathInfo = uri.removePrefix("/download")
                        if (pathInfo.startsWith("/")) {
                            pathInfo = pathInfo.removePrefix("/")
                        }
                    }
                    
                    Log.d(TAG, "=== PATH INFO: $pathInfo ===")
                    
                    if (pathInfo.isEmpty()) {
                        call.respondText("Missing file path", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    
                    val decodedPath = try {
                        URLDecoder.decode(pathInfo, "UTF-8")
                    } catch (e: Exception) {
                        pathInfo
                    }
                    Log.d(TAG, "=== DECODED: $decodedPath ===")
                    
                    val rootDir = getRootDirectory()
                    val targetFile = File(rootDir, decodedPath)
                    
                    if (targetFile != null && targetFile.exists()) {
                        Log.d(TAG, "=== FOUND: ${targetFile.absolutePath} ===")
                        call.response.header("Content-Disposition", "attachment; filename=\"${targetFile.name}\"")
                        call.respondFile(targetFile)
                    } else {
                        Log.d(TAG, "=== NOT FOUND ===")
                        call.respondText("File not found: $decodedPath", status = HttpStatusCode.NotFound)
                    }
                }

                // List all files recursively (including folders)
                get("/files") {
                    val syncDir = getSyncDirectory()
                    val allItems = mutableListOf<Pair<String, Boolean>>() // path, isDirectory

                    fun listRecursive(dir: File, basePath: String = "") {
                        dir.listFiles()?.forEach { file ->
                            val relativePath = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
                            if (file.isDirectory) {
                                allItems.add(relativePath to true)
                                listRecursive(file, relativePath)
                            } else {
                                allItems.add(relativePath to false)
                            }
                        }
                    }
                    listRecursive(syncDir)

                    val itemsJson = allItems.map { (name, isDir) ->
                        "{\"name\":\"$name\",\"isDirectory\":$isDir}"
                    }
                    val json = "{\"items\":[${itemsJson.joinToString(",")}],\"total\":${allItems.size}}"
                    Log.d(TAG, "=== FILES: ${allItems.size} items ===")
                    call.respondText(json, ContentType.Application.Json)
                }

                // List directory contents (for file browser)
                get("/browse") {
                    val rootDir = getRootDirectory()
                    val pathParam = call.parameters["path"] ?: ""
                    
                    val currentDir = if (pathParam.isEmpty()) {
                        rootDir
                    } else {
                        File(rootDir, pathParam)
                    }
                    
                    if (!currentDir.exists() || !currentDir.isDirectory) {
                        call.respondText("{\"error\":\"Invalid directory\",\"items\":[]}", ContentType.Application.Json)
                        return@get
                    }
                    
                    val items = currentDir.listFiles()?.map { file ->
                        val size = if (file.isFile) file.length() else 0L
                        "{\"name\":\"${file.name}\",\"isDirectory\":${file.isDirectory},\"size\":$size}"
                    } ?: emptyList()
                    
                    val parentPath = if (pathParam.contains("/")) {
                        pathParam.substringBeforeLast("/", "")
                    } else {
                        ""
                    }
                    
                    val json = "{\"path\":\"$pathParam\",\"parent\":\"$parentPath\",\"items\":[${items.joinToString(",")}],\"total\":${items.size}}"
                    Log.d(TAG, "=== BROWSE: $pathParam - ${items.size} items ===")
                    call.respondText(json, ContentType.Application.Json)
                }

                // Delete file or folder
                delete("/delete") {
                    val pathParam = call.parameters["path"] ?: ""
                    if (pathParam.isEmpty()) {
                        call.respondText("{\"error\":\"Missing path\",\"success\":false}", ContentType.Application.Json)
                        return@delete
                    }
                    
                    val rootDir = getRootDirectory()
                    val targetFile = File(rootDir, pathParam)
                    
                    if (!targetFile.exists()) {
                        call.respondText("{\"error\":\"File not found\",\"success\":false}", ContentType.Application.Json)
                        return@delete
                    }
                    
                    val success = try {
                        if (targetFile.isDirectory) {
                            targetFile.deleteRecursively()
                        } else {
                            targetFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Delete error: ${e.message}")
                        false
                    }
                    
                    if (success) {
                        Log.d(TAG, "Deleted: $pathParam")
                        call.respondText("{\"success\":true,\"path\":\"$pathParam\"}", ContentType.Application.Json)
                    } else {
                        call.respondText("{\"error\":\"Delete failed\",\"success\":false}", ContentType.Application.Json)
                    }
                }
            }
        }.start(wait = false)
        Log.d(TAG, "Ktor server started at ${address.hostAddress}:$serverPort")
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        Log.d(TAG, "Ktor server stopped.")
    }

    companion object {
        private const val TAG = "WebServer"
    }
}
