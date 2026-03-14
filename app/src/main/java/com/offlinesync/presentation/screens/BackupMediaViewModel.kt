package com.offlinesync.presentation.screens

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
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

    private val _estimatedTimeRemaining = MutableStateFlow("")
    val estimatedTimeRemaining: StateFlow<String> = _estimatedTimeRemaining.asStateFlow()

    private val _selectedBackupPath = MutableStateFlow<String?>(null)
    val selectedBackupPath: StateFlow<String?> = _selectedBackupPath.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _backupResult = MutableStateFlow<MediaBackupResult?>(null)
    val backupResult: StateFlow<MediaBackupResult?> = _backupResult.asStateFlow()

    private var backupStartTime = 0L
    private var totalFilesToBackup = 0

    companion object {
        private const val TAG = "BackupMediaViewModel"
        
        const val IS_PREMIUM_VERSION = true
        
        val DEFAULT_BACKUP_PATH = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OfflineSync/Media")

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

    fun setBackupPath(path: String?) {
        _selectedBackupPath.value = path
    }

    fun getBackupDestination(): File {
        return _selectedBackupPath.value?.let { File(it) } ?: DEFAULT_BACKUP_PATH
    }

    fun startBackup(context: Context, includeImages: Boolean = true, includeVideos: Boolean = true, includeAudio: Boolean = true) {
        viewModelScope.launch(Dispatchers.Main) {
            _isLoading.value = true
            _backupResult.value = null
            try {
                _backupStatus.value = "Calculating media size..."
                
                val extensions = mutableListOf<String>()
                if (includeImages) {
                    extensions.addAll(listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "raw", "cr2", "nef", "arw"))
                }
                if (includeVideos) {
                    extensions.addAll(listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts"))
                }
                if (includeAudio) {
                    extensions.addAll(listOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "wma", "opus", "aiff"))
                }
                
                val totalBackupSize = withContext(Dispatchers.IO) { calculateTotalSize(extensions) }
                val availableSpace = withContext(Dispatchers.IO) { getAvailableSpace() }
                
                Log.d(TAG, "Total backup size: ${formatSize(totalBackupSize)}, Available: ${formatSize(availableSpace)}")
                
                if (totalBackupSize == 0L) {
                    _backupStatus.value = "No media files found."
                    _isLoading.value = false
                    return@launch
                }
                
                if (totalBackupSize > availableSpace) {
                    val needed = formatSize(totalBackupSize - availableSpace)
                    _backupStatus.value = "Not enough storage space. Need $needed more free space."
                    _isLoading.value = false
                    return@launch
                }
                
                _backupStatus.value = "Starting media backup... (${formatSize(totalBackupSize)} to backup)"
                backupStartTime = System.currentTimeMillis()
                totalFilesToBackup = withContext(Dispatchers.IO) { countTotalFiles(extensions) }
                _backupProgress.value = 0
                _estimatedTimeRemaining.value = "Calculating..."
                
                val result = withContext(Dispatchers.IO) { 
                    exportMedia(context, includeImages, includeVideos, includeAudio) 
                }
                _backupResult.value = result
                val msg = if (result.totalFiles == 0) {
                    "No media files found."
                } else {
                    "Backup complete: ${result.backedUpFiles}/${result.totalFiles} files (${formatSize(result.backedUpSize)})"
                }
                _backupStatus.value = msg
            } catch (e: Exception) {
                Log.e(TAG, "Error backing up media: ${e.message}", e)
                _backupStatus.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun calculateTotalSize(extensions: List<String>): Long {
        var totalSize = 0L
        val externalStorage = Environment.getExternalStorageDirectory()
        
        val allFolders = STANDARD_IMAGE_FOLDERS + STANDARD_VIDEO_FOLDERS + STANDARD_AUDIO_FOLDERS
        
        for (folder in allFolders.toSet()) {
            val mediaDir = Environment.getExternalStoragePublicDirectory(folder)
            if (mediaDir.exists() && mediaDir.isDirectory) {
                try {
                    val files = mediaDir.walkTopDown()
                        .filter { it.isFile && extensions.any { ext -> it.extension.equals(ext, ignoreCase = true) } }
                    totalSize += files.sumOf { it.length() }
                } catch (e: Exception) {
                    Log.w(TAG, "Error scanning $folder: ${e.message}")
                }
            }
        }
        
        for (messenger in MESSENGER_CONFIGS) {
            for (relativePath in messenger.folders) {
                val sourceDir = File(externalStorage, relativePath)
                if (sourceDir.exists() && sourceDir.isDirectory) {
                    try {
                        val files = sourceDir.walkTopDown()
                            .filter { it.isFile && extensions.any { ext -> it.extension.equals(ext, ignoreCase = true) } }
                        totalSize += files.sumOf { it.length() }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error scanning $relativePath: ${e.message}")
                    }
                }
            }
        }
        
        return totalSize
    }
    
    private fun getAvailableSpace(): Long {
        return try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available space: ${e.message}")
            0L
        }
    }
    
    private fun countTotalFiles(extensions: List<String>): Int {
        var count = 0
        val externalStorage = Environment.getExternalStorageDirectory()
        
        val allFolders = STANDARD_IMAGE_FOLDERS + STANDARD_VIDEO_FOLDERS + STANDARD_AUDIO_FOLDERS
        
        for (folder in allFolders.toSet()) {
            val mediaDir = Environment.getExternalStoragePublicDirectory(folder)
            if (mediaDir.exists() && mediaDir.isDirectory) {
                try {
                    count += mediaDir.walkTopDown()
                        .filter { it.isFile && extensions.any { ext -> it.extension.equals(ext, ignoreCase = true) } }
                        .count()
                } catch (e: Exception) {
                    Log.w(TAG, "Error counting $folder: ${e.message}")
                }
            }
        }
        
        for (messenger in MESSENGER_CONFIGS) {
            for (relativePath in messenger.folders) {
                val sourceDir = File(externalStorage, relativePath)
                if (sourceDir.exists() && sourceDir.isDirectory) {
                    try {
                        count += sourceDir.walkTopDown()
                            .filter { it.isFile && extensions.any { ext -> it.extension.equals(ext, ignoreCase = true) } }
                            .count()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error counting $relativePath: ${e.message}")
                    }
                }
            }
        }
        
        return count
    }

    private suspend fun exportMedia(
        context: Context,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeAudio: Boolean
    ): MediaBackupResult = withContext(Dispatchers.IO) {
        val syncDir = getBackupDestination()
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
            extensions.addAll(listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "raw", "cr2", "nef", "arw"))
        }
        if (includeVideos) {
            extensions.addAll(listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts"))
        }
        if (includeAudio) {
            extensions.addAll(listOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "wma", "opus", "aiff"))
        }

        Log.d(TAG, "Starting backup with extensions: $extensions")
        val externalStorage = Environment.getExternalStorageDirectory()
        Log.d(TAG, "External storage: ${externalStorage.absolutePath}")
        
        _backupStatus.value = "Scanning standard folders..."
        
        var processedFiles = 0
        
        if (includeImages) {
            for (folder in STANDARD_IMAGE_FOLDERS) {
                val mediaDir = Environment.getExternalStoragePublicDirectory(folder)
                Log.d(TAG, "Processing image folder: ${mediaDir.absolutePath}")
                val (count, backed, skipped, size, backedSize) = scanAndBackupDirectory(
                    mediaDir, File(backupDir, folder), extensions, "Images"
                )
                totalFiles += count
                backedUpFiles += backed
                skippedFiles += skipped
                totalSize += size
                backedUpSize += backedSize
                if (count > 0) sourceDetails[folder] = count
                
                processedFiles += backed + skipped
                updateProgress(processedFiles)
            }
        }

        if (includeVideos) {
            for (folder in STANDARD_VIDEO_FOLDERS) {
                val mediaDir = Environment.getExternalStoragePublicDirectory(folder)
                Log.d(TAG, "Processing video folder: ${mediaDir.absolutePath}")
                val (count, backed, skipped, size, backedSize) = scanAndBackupDirectory(
                    mediaDir, File(backupDir, folder), extensions, "Videos"
                )
                totalFiles += count
                backedUpFiles += backed
                skippedFiles += skipped
                totalSize += size
                backedUpSize += backedSize
                if (count > 0) sourceDetails[folder] = count
                
                processedFiles += backed + skipped
                updateProgress(processedFiles)
            }
        }

        if (includeAudio) {
            for (folder in STANDARD_AUDIO_FOLDERS) {
                val mediaDir = Environment.getExternalStoragePublicDirectory(folder)
                Log.d(TAG, "Processing audio folder: ${mediaDir.absolutePath}")
                val (count, backed, skipped, size, backedSize) = scanAndBackupDirectory(
                    mediaDir, File(backupDir, folder), extensions, "Audio"
                )
                totalFiles += count
                backedUpFiles += backed
                skippedFiles += skipped
                totalSize += size
                backedUpSize += backedSize
                if (count > 0) sourceDetails[folder] = count
                
                processedFiles += backed + skipped
                updateProgress(processedFiles)
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
                    
                    processedFiles += backed + skipped
                    updateProgress(processedFiles)
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
                
                processedFiles += backed + skipped
                updateProgress(processedFiles)
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
        Log.d(TAG, "Scanning directory: ${sourceDir.absolutePath}, category: $category")
        
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            Log.d(TAG, "Directory does not exist or is not a directory: ${sourceDir.absolutePath}")
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
            Log.d(TAG, "Found $totalCount files in ${sourceDir.absolutePath}")

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
                    Log.d(TAG, "Backed up: ${file.name}")

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

    private fun updateProgress(processedFiles: Int) {
        if (totalFilesToBackup > 0) {
            val progress = (processedFiles * 100) / totalFilesToBackup
            _backupProgress.value = progress.coerceIn(0, 100)
            
            val elapsedTime = System.currentTimeMillis() - backupStartTime
            if (processedFiles > 0 && elapsedTime > 0) {
                val timePerFile = elapsedTime / processedFiles
                val remainingFiles = totalFilesToBackup - processedFiles
                val remainingTimeMs = remainingFiles * timePerFile
                _estimatedTimeRemaining.value = formatTime(remainingTimeMs)
            }
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        return when {
            seconds < 60 -> "$seconds sec"
            seconds < 3600 -> "${seconds / 60} min ${seconds % 60} sec"
            else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
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
