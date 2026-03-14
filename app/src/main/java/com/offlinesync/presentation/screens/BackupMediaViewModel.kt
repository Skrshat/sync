package com.offlinesync.presentation.screens

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MediaBackupResult(
    val totalFiles: Int,
    val backedUpFiles: Int,
    val skippedFiles: Int,
    val totalSize: Long,
    val backedUpSize: Long,
    val sourceDetails: Map<String, Int>
)

@HiltViewModel
class BackupMediaViewModel @Inject constructor() : ViewModel() {

    private val _backupStatus = MutableStateFlow("")
    val backupStatus: StateFlow<String> = _backupStatus.asStateFlow()

    private val _backupProgress = MutableStateFlow(0)
    val backupProgress: StateFlow<Int> = _backupProgress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _backupResult = MutableStateFlow<MediaBackupResult?>(null)
    val backupResult: StateFlow<MediaBackupResult?> = _backupResult.asStateFlow()

    companion object {
        private const val TAG = "BackupMediaViewModel"

        private val STANDARD_IMAGE_FOLDERS = listOf(
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_DOWNLOADS
        )

        private val STANDARD_VIDEO_FOLDERS = listOf(
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_DCIM,
            "Video",
            Environment.DIRECTORY_DOWNLOADS
        )

        private val STANDARD_AUDIO_FOLDERS = listOf(
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_PODCASTS,
            Environment.DIRECTORY_RINGTONES,
            Environment.DIRECTORY_NOTIFICATIONS,
            Environment.DIRECTORY_ALARMS,
            Environment.DIRECTORY_DOWNLOADS
        )

        private val MESSENGER_CONFIGS = listOf(
            MessengerConfig("Telegram", "org.telegram.messenger", listOf(
                "Telegram/Telegram Images",
                "Telegram/Telegram Video",
                "Telegram/Telegram Audio",
                "Telegram/Telegram VoiceNotes",
                "Telegram/Telegram Documents"
            )),
            MessengerConfig("WhatsApp", "com.whatsapp", listOf(
                "WhatsApp/Media/WhatsApp Images",
                "WhatsApp/Media/WhatsApp Video",
                "WhatsApp/Media/WhatsApp Audio",
                "WhatsApp/Media/WhatsApp Voice Notes",
                "WhatsApp/Media/WhatsApp Documents"
            )),
            MessengerConfig("Viber", "com.viber.voip", listOf(
                "Viber/Viber Images",
                "Viber/Viber Videos",
                "Viber/Viber Audio",
                "Viber/Viber Documents"
            )),
            MessengerConfig("Signal", "org.signal", listOf(
                "Signal/Media"
            )),
            MessengerConfig("Facebook Messenger", "com.facebook.orca", listOf(
                "Messenger/Media"
            )),
            MessengerConfig("Snapchat", "com.snapchat.android", listOf(
                "Snapchat/Media"
            )),
            MessengerConfig("Instagram", "com.instagram.android", listOf(
                "Instagram"
            )),
            MessengerConfig("Discord", "com.discord", listOf(
                "Discord/media"
            )),
            MessengerConfig("Skype", "com.skype.raider", listOf(
                "Skype/Media"
            )),
            MessengerConfig("Line", "jp.naver.line.android", listOf(
                "Line/Media"
            )),
            MessengerConfig("WeChat", "com.tencent.mm", listOf(
                "WeChat/Media"
            ))
        )
    }

    fun startBackup(context: Context, includeImages: Boolean = true, includeVideos: Boolean = true, includeAudio: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            _backupResult.value = null
            try {
                _backupStatus.value = "Starting media backup..."
                val result = exportMedia(context, includeImages, includeVideos, includeAudio)
                _backupResult.value = result
                _backupStatus.value = "Backup complete: ${result.backedUpFiles} files backed up (${formatSize(result.backedUpSize)})"
            } catch (e: Exception) {
                Log.e(TAG, "Error backing up media: ${e.message}")
                _backupStatus.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun exportMedia(
        context: Context,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeAudio: Boolean
    ): MediaBackupResult = withContext(Dispatchers.IO) {
        val syncDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OfflineSync/Media")
        if (!syncDir.exists()) {
            syncDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val backupDir = File(syncDir, "backup_$timestamp")
        backupDir.mkdirs()

        val sourceDetails = mutableMapOf<String, Int>()
        var totalFiles = 0
        var backedUpFiles = 0
        var skippedFiles = 0
        var totalSize = 0L
        var backedUpSize = 0L

        val extensions = mutableListOf<String>()
        if (includeImages) {
            extensions.addAll(listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg"))
        }
        if (includeVideos) {
            extensions.addAll(listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v"))
        }
        if (includeAudio) {
            extensions.addAll(listOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "wma", "opus"))
        }

        val externalStorage = Environment.getExternalStorageDirectory()
        
        _backupStatus.value = "Scanning standard folders..."
        
        if (includeImages) {
            for (folder in STANDARD_IMAGE_FOLDERS) {
                val mediaDir = Environment.getExternalStoragePublicDirectory(folder)
                val (count, backed, skipped, size, backedSize) = scanAndBackupDirectory(
                    mediaDir, File(backupDir, folder), extensions, "Images"
                )
                totalFiles += count
                backedUpFiles += backed
                skippedFiles += skipped
                totalSize += size
                backedUpSize += backedSize
                if (count > 0) sourceDetails[folder] = count
            }
        }

        if (includeVideos) {
            for (folder in STANDARD_VIDEO_FOLDERS) {
                val mediaDir = Environment.getExternalStoragePublicDirectory(folder)
                val (count, backed, skipped, size, backedSize) = scanAndBackupDirectory(
                    mediaDir, File(backupDir, folder), extensions, "Videos"
                )
                totalFiles += count
                backedUpFiles += backed
                skippedFiles += skipped
                totalSize += size
                backedUpSize += backedSize
                if (count > 0) sourceDetails[folder] = count
            }
        }

        if (includeAudio) {
            for (folder in STANDARD_AUDIO_FOLDERS) {
                val mediaDir = Environment.getExternalStoragePublicDirectory(folder)
                val (count, backed, skipped, size, backedSize) = scanAndBackupDirectory(
                    mediaDir, File(backupDir, folder), extensions, "Audio"
                )
                totalFiles += count
                backedUpFiles += backed
                skippedFiles += skipped
                totalSize += size
                backedUpSize += backedSize
                if (count > 0) sourceDetails[folder] = count
            }
        }

        _backupStatus.value = "Scanning messenger apps..."

        for (messenger in MESSENGER_CONFIGS) {
            val messengerBackupDir = File(backupDir, messenger.name)
            
            for (relativePath in messenger.folders) {
                val sourceDir = File(externalStorage, relativePath)
                if (sourceDir.exists() && sourceDir.isDirectory) {
                    val subDirName = relativePath.substringAfterLast("/")
                    val (count, backed, skipped, size, backedSize) = scanAndBackupDirectory(
                        sourceDir, File(messengerBackupDir, subDirName), extensions, messenger.name
                    )
                    totalFiles += count
                    backedUpFiles += backed
                    skippedFiles += skipped
                    totalSize += size
                    backedUpSize += backedSize
                    if (count > 0) sourceDetails["${messenger.name}/$subDirName"] = count
                }
            }

            val androidDataDir = File(externalStorage, "Android/data/${messenger.packageName}/files")
            if (androidDataDir.exists() && androidDataDir.isDirectory) {
                val (count, backed, skipped, size, backedSize) = scanAndBackupDirectory(
                    androidDataDir, File(messengerBackupDir, "AndroidData"), extensions, messenger.name
                )
                totalFiles += count
                backedUpFiles += backed
                skippedFiles += skipped
                totalSize += size
                backedUpSize += backedSize
                if (count > 0) sourceDetails["${messenger.name}/AndroidData"] = count
            }
        }

        MediaBackupResult(
            totalFiles = totalFiles,
            backedUpFiles = backedUpFiles,
            skippedFiles = skippedFiles,
            totalSize = totalSize,
            backedUpSize = backedUpSize,
            sourceDetails = sourceDetails
        )
    }

    private fun scanAndBackupDirectory(
        sourceDir: File,
        destDir: File,
        extensions: List<String>,
        category: String
    ): BackupStats {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            return BackupStats(0, 0, 0, 0, 0)
        }

        var totalCount = 0
        var backedUpCount = 0
        var skippedCount = 0
        var totalSize = 0L
        var backedUpSize = 0L

        try {
            val files = sourceDir.walkTopDown()
                .filter { it.isFile && extensions.any { ext -> it.extension.equals(ext, ignoreCase = true) } }
                .toList()

            totalCount = files.size

            for (file in files) {
                try {
                    val relativePath = file.relativeTo(sourceDir)
                    val destFile = File(destDir, relativePath.path)
                    
                    destFile.parentFile?.mkdirs()

                    totalSize += file.length()

                    if (destFile.exists() && destFile.length() == file.length()) {
                        skippedCount++
                        continue
                    }

                    copyFile(file, destFile)
                    backedUpCount++
                    backedUpSize += file.length()

                } catch (e: Exception) {
                    Log.w(TAG, "Failed to backup file: ${file.absolutePath}, error: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Error scanning directory: ${sourceDir.absolutePath}, error: ${e.message}")
        }

        return BackupStats(totalCount, backedUpCount, skippedCount, totalSize, backedUpSize)
    }

    private fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun formatSize(size: Long): String {
        return when {
            size >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.2f GB", size / 1_000_000_000.0)
            size >= 1_000_000 -> String.format(Locale.getDefault(), "%.2f MB", size / 1_000_000.0)
            size >= 1_000 -> String.format(Locale.getDefault(), "%.2f KB", size / 1_000.0)
            else -> "$size B"
        }
    }

    private data class BackupStats(
        val totalCount: Int,
        val backedUpCount: Int,
        val skippedCount: Int,
        val totalSize: Long,
        val backedUpSize: Long
    )

    private data class MessengerConfig(
        val name: String,
        val packageName: String,
        val folders: List<String>
    )
}
