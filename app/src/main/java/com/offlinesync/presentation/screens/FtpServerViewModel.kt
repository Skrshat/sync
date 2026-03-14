package com.offlinesync.presentation.screens

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinesync.service.FtpServer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FtpServerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val ftpServer = FtpServer(context)
    private val handler = Handler(Looper.getMainLooper())
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _serverIp = MutableStateFlow<String?>(null)
    val serverIp: StateFlow<String?> = _serverIp.asStateFlow()
    
    private val _serverPort = MutableStateFlow(FtpServer.DEFAULT_PORT)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()
    
    private val _rootDirectory = MutableStateFlow<File?>(null)
    val rootDirectory: StateFlow<File?> = _rootDirectory.asStateFlow()
    
    private val _mediaDirectories = MutableStateFlow<List<File>>(emptyList())
    val mediaDirectories: StateFlow<List<File>> = _mediaDirectories.asStateFlow()
    
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()
    
    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()
    
    init {
        ftpServer.onClientConnect = { clientId ->
            handler.post {
                _connectedClients.value = ftpServer.getConnectedClientsCount()
                _statusMessage.value = "Client connected"
            }
        }
        
        ftpServer.onClientDisconnect = { clientId ->
            handler.post {
                _connectedClients.value = ftpServer.getConnectedClientsCount()
                _statusMessage.value = "Client disconnected"
            }
        }
    }
    
    fun startServer() {
        viewModelScope.launch {
            try {
                ftpServer.start()
                _isRunning.value = true
                _serverIp.value = ftpServer.currentIp
                _serverPort.value = ftpServer.getPort()
                _rootDirectory.value = ftpServer.getRootDirectory()
                _mediaDirectories.value = ftpServer.getMediaFolders()
                _statusMessage.value = "Server started on ${ftpServer.currentIp}:${ftpServer.getPort()}"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }
    
    fun stopServer() {
        viewModelScope.launch {
            ftpServer.stop()
            _isRunning.value = false
            _serverIp.value = null
            _rootDirectory.value = null
            _connectedClients.value = 0
            _statusMessage.value = "Server stopped"
        }
    }
    
    fun getFtpUrl(): String {
        return "ftp://${_serverIp.value}:${_serverPort.value}"
    }
    
    override fun onCleared() {
        super.onCleared()
        if (_isRunning.value) {
            ftpServer.stop()
        }
    }
}
