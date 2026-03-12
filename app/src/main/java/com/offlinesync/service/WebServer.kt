package com.offlinesync.service

import android.content.Context
import android.os.Environment
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

class WebServer(private val context: Context, private val serverPort: Int = 8080) {

    val port: Int get() = serverPort

    private var server: NettyApplicationEngine? = null

    private fun getSyncDirectory(): File {
        val syncDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OfflineSync")
        if (!syncDir.exists()) {
            syncDir.mkdirs()
            Log.d(TAG, "Created sync directory: ${syncDir.absolutePath}")
        }
        Log.d(TAG, "Sync directory: ${syncDir.absolutePath}, exists: ${syncDir.exists()}, writable: ${syncDir.canWrite()}")
        return syncDir
    }

    fun start(address: InetAddress) {
        if (server != null) {
            Log.d(TAG, "Server already running, stopping before restarting.")
            stop()
        }

        server = embeddedServer(Netty, port = serverPort, host = address.hostAddress) {
            routing {
                get("/") {
                    call.respondText("OfflineSync Android Server", ContentType.Text.Plain)
                }

                post("/upload") {
                    val multipart = call.receiveMultipart()
                    val syncDir = getSyncDirectory()
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                val fileName = part.originalFileName ?: "unknown_file"
                                val file = File(syncDir, fileName)
                                Log.d(TAG, "Saving file to: ${file.absolutePath}")
                                withContext(Dispatchers.IO) {
                                    part.streamProvider().use { input ->
                                        FileOutputStream(file).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                                Log.d(TAG, "Received file: ${file.absolutePath}, exists: ${file.exists()}, size: ${file.length()}")
                                call.respondText("File received: ${file.name}")
                            }
                            else -> {}
                        }
                        part.dispose()
                    }
                }

                get("/download/{filename}") {
                    val filename = call.parameters["filename"]
                    if (filename != null) {
                        val file = File(getSyncDirectory(), filename)
                        if (file.exists()) {
                            call.response.header("Content-Disposition", "attachment; filename=\"$filename\"")
                            call.respondFile(file)
                        } else {
                            call.respondText("File not found", status = HttpStatusCode.NotFound)
                        }
                    } else {
                        call.respondText("Filename missing", status = HttpStatusCode.BadRequest)
                    }
                }

                get("/files") {
                    val syncDir = getSyncDirectory()
                    val files = syncDir.listFiles()?.map { file ->
                        "{\"name\":\"${file.name}\",\"size\":${file.length()},\"modified\":${file.lastModified()}}"
                    } ?: emptyList()
                    val json = "{\"files\":[${files.joinToString(",")}]}"
                    call.respondText(json, ContentType.Application.Json)
                }

                get("/ping") {
                    call.respondText("OK", ContentType.Text.Plain)
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
