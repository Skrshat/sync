package com.offlinesync.service

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class FtpServer(private val context: Context, private val port: Int = 2121) {
    
    companion object {
        private const val TAG = "FtpServer"
        const val DEFAULT_PORT = 2121
    }
    
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val clients = ConcurrentHashMap<String, ClientHandler>()
    
    var isRunning = false
        private set
    
    var currentIp: String? = null
        private set
    
    var onClientConnect: ((String) -> Unit)? = null
    var onClientDisconnect: ((String) -> Unit)? = null
    
    private val rootDir: File by lazy {
        Environment.getExternalStorageDirectory()
    }
    
    private val mediaDirectories: List<File> by lazy {
        listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_ALARMS)
        )
    }
    
    fun start() {
        if (isRunning) {
            Log.d(TAG, "Server already running")
            return
        }
        
        try {
            serverSocket = ServerSocket(port)
            serverSocket?.soTimeout = 0
            isRunning = true
            
            serverJob = scope.launch {
                Log.d(TAG, "FTP Server started on port $port")
                
                while (isActive && serverSocket != null) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let { socket ->
                            val clientId = UUID.randomUUID().toString()
                            val handler = ClientHandler(socket, rootDir, clientId)
                            clients[clientId] = handler
                            onClientConnect?.invoke(clientId)
                            
                            scope.launch {
                                handler.process()
                                clients.remove(clientId)
                                onClientDisconnect?.invoke(clientId)
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error accepting connection: ${e.message}")
                        }
                    }
                }
            }
            
            currentIp = getLocalIpAddress()
            Log.d(TAG, "FTP Server IP: $currentIp")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start FTP server: ${e.message}")
            stop()
        }
    }
    
    fun stop() {
        isRunning = false
        serverJob?.cancel()
        
        clients.values.forEach { it.close() }
        clients.clear()
        
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        serverSocket = null
        currentIp = null
        
        Log.d(TAG, "FTP Server stopped")
    }
    
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP: ${e.message}")
        }
        return null
    }
    
    fun getPort(): Int = port
    
    fun getRootDirectory(): File = rootDir
    
    fun getMediaFolders(): List<File> = mediaDirectories.filter { it.exists() && it.isDirectory }
    
    fun getConnectedClientsCount(): Int = clients.size
}

class ClientHandler(
    private val socket: Socket,
    private val rootDir: File,
    private val clientId: String
) {
    private val TAG = "FtpClient"
    private var currentDir: File = rootDir
    private var dataServer: ServerSocket? = null
    private var dataSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var isLoggedIn = false
    private var username: String = ""
    private var clientIp: String = ""
    private var clientDataPort: Int = 0
    
    init {
        clientIp = socket.inetAddress.hostAddress ?: ""
    }
    
    suspend fun process() = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 300000
            reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            writer = PrintWriter(socket.getOutputStream(), true)
            
            sendResponse(220, "OfflineSync FTP Server ready")
            
            var line: String?
            while (reader?.readLine().also { line = it } != null) {
                line?.let { 
                    Log.d(TAG, "Received: $it")
                    if (!processCommand(it)) {
                        return@withContext
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client error: ${e.message}")
        } finally {
            close()
        }
    }
    
    private fun processCommand(line: String): Boolean {
        val parts = line.trim().split(" ")
        val command = parts.getOrNull(0)?.uppercase() ?: return true
        val args = if (parts.size > 1) parts.subList(1, parts.size).joinToString(" ") else ""
        
        Log.d(TAG, "Processing command: $command args: $args")
        
        return when (command) {
            "USER" -> handleUser(args)
            "PASS" -> handlePass(args)
            "QUIT" -> {
                sendResponse(221, "Goodbye")
                false
            }
            "NOOP" -> { sendResponse(200, "OK"); true }
            "SYST" -> { sendResponse(215, "UNIX Type: L8"); true }
            "FEAT" -> { sendResponse(211, "No features"); true }
            "PWD" -> { sendResponse(257, "\"${getCurrentPath()}\""); true }
            "CWD" -> handleCwd(args)
            "CDUP" -> handleCdup()
            "TYPE" -> { sendResponse(200, "Type set to I"); true }
            "PASV" -> handlePasv()
            "PORT" -> handlePort(args)
            "LIST" -> handleList(args)
            "NLST" -> handleNlst(args)
            "RETR" -> handleRetr(args)
            "STOR" -> handleStor(args)
            "DELE" -> handleDele(args)
            "RNFR" -> { sendResponse(350, "Ready for RNTO"); true }
            "RNTO" -> { sendResponse(250, "Rename successful"); true }
            "MKD" -> handleMkd(args)
            "RMD" -> handleRmd(args)
            "SIZE" -> handleSize(args)
            "OPTS" -> { sendResponse(200, "OK"); true }
            else -> { sendResponse(502, "Command not implemented"); true }
        }
    }
    
    private fun getCurrentPath(): String {
        val relPath = currentDir.relativeTo(rootDir).path
        return if (relPath.isEmpty() || relPath == ".") "/" else "/$relPath"
    }
    
    private fun handleUser(args: String): Boolean {
        username = args
        sendResponse(331, "Password required")
        return true
    }
    
    private fun handlePass(args: String): Boolean {
        isLoggedIn = true
        sendResponse(230, "User logged in")
        return true
    }
    
    private fun handleCwd(args: String): Boolean {
        val newDir = resolvePath(args)
        if (newDir.exists() && newDir.isDirectory && newDir.path.startsWith(rootDir.path)) {
            currentDir = newDir
            sendResponse(250, "CWD command successful")
        } else {
            sendResponse(550, "Failed to change directory")
        }
        return true
    }
    
    private fun handleCdup(): Boolean {
        if (currentDir.parentFile != null && currentDir.path.startsWith(rootDir.path)) {
            currentDir = currentDir.parentFile
            sendResponse(250, "CDUP command successful")
        } else {
            sendResponse(550, "Cannot go above root")
        }
        return true
    }
    
    private fun handlePasv(): Boolean {
        try {
            dataServer = ServerSocket(0, 1, InetAddress.getByName(clientIp))
            val port = dataServer!!.localPort
            val ip = clientIp.split(".")
            
            val p1 = port / 256
            val p2 = port % 256
            
            sendResponse(227, "Entering Passive Mode ($ip[0],$ip[1],$ip[2],$ip[3],$p1,$p2)")
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dataSocket = dataServer?.accept()
                } catch (e: Exception) {
                    Log.e(TAG, "PASV accept error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PASV error: ${e.message}")
            sendResponse(425, "Can't open data connection")
        }
        return true
    }
    
    private fun handlePort(args: String): Boolean {
        try {
            val parts = args.split(",")
            if (parts.size != 6) {
                sendResponse(501, "Invalid PORT command")
                return true
            }
            clientIp = "${parts[0]}.${parts[1]}.${parts[2]}.${parts[3]}"
            clientDataPort = parts[4].toInt() * 256 + parts[5].toInt()
            sendResponse(200, "PORT command successful")
        } catch (e: Exception) {
            Log.e(TAG, "PORT error: ${e.message}")
            sendResponse(501, "Invalid PORT command")
        }
        return true
    }
    
    private fun handleList(args: String): Boolean {
        try {
            val listData = StringBuilder()
            
            currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.forEach { file ->
                val perms = if (file.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
                val size = file.length()
                val date = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(file.lastModified()))
                listData.appendLine("$perms   1 1000 1000 $size $date ${file.name}")
            }
            
            sendResponse(150, "Opening ASCII mode data connection")
            
            val ds = dataSocket ?: run {
                dataSocket = Socket(clientIp, clientDataPort)
                dataSocket
            }
            
            ds?.getOutputStream()?.write(listData.toString().toByteArray())
            ds?.close()
            dataSocket = null
            
            sendResponse(226, "Transfer complete")
        } catch (e: Exception) {
            Log.e(TAG, "LIST error: ${e.message}")
            sendResponse(426, "Transfer failed: ${e.message}")
        }
        return true
    }
    
    private fun handleNlst(args: String): Boolean {
        try {
            val listData = currentDir.listFiles()
                ?.sortedBy { it.name.lowercase() }
                ?.joinToString("\r\n") { it.name } ?: ""
            
            sendResponse(150, "Opening ASCII mode data connection")
            
            val ds = dataSocket ?: run {
                dataSocket = Socket(clientIp, clientDataPort)
                dataSocket
            }
            
            ds?.getOutputStream()?.write(listData.toByteArray())
            ds?.close()
            dataSocket = null
            
            sendResponse(226, "Transfer complete")
        } catch (e: Exception) {
            Log.e(TAG, "NLST error: ${e.message}")
            sendResponse(426, "Transfer failed")
        }
        return true
    }
    
    private fun handleRetr(args: String): Boolean {
        val file = resolvePath(args)
        if (!file.exists() || !file.isFile) {
            sendResponse(550, "File not found")
            return true
        }
        
        try {
            sendResponse(150, "Opening BINARY mode data connection")
            
            val ds = dataSocket ?: run {
                dataSocket = Socket(clientIp, clientDataPort)
                dataSocket
            }
            
            FileInputStream(file).use { input ->
                ds?.getOutputStream()?.use { output ->
                    input.copyTo(output)
                }
            }
            ds?.close()
            dataSocket = null
            
            sendResponse(226, "Transfer complete")
        } catch (e: Exception) {
            Log.e(TAG, "RETR error: ${e.message}")
            sendResponse(426, "Transfer failed: ${e.message}")
        }
        return true
    }
    
    private fun handleStor(args: String): Boolean {
        val file = resolvePath(args)
        
        try {
            sendResponse(150, "Opening BINARY mode data connection")
            
            val ds = dataSocket ?: run {
                dataSocket = Socket(clientIp, clientDataPort)
                dataSocket
            }
            
            ds?.getInputStream()?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            ds?.close()
            dataSocket = null
            
            sendResponse(226, "Transfer complete")
        } catch (e: Exception) {
            Log.e(TAG, "STOR error: ${e.message}")
            sendResponse(426, "Transfer failed: ${e.message}")
        }
        return true
    }
    
    private fun handleDele(args: String): Boolean {
        val file = resolvePath(args)
        if (file.exists() && file.isFile && file.path.startsWith(rootDir.path)) {
            file.delete()
            sendResponse(250, "Delete successful")
        } else {
            sendResponse(550, "Delete failed")
        }
        return true
    }
    
    private fun handleMkd(args: String): Boolean {
        val dir = resolvePath(args)
        if (dir.mkdir()) {
            sendResponse(257, "\"${dir.relativeTo(rootDir).path}\" created")
        } else {
            sendResponse(550, "Create directory failed")
        }
        return true
    }
    
    private fun handleRmd(args: String): Boolean {
        val dir = resolvePath(args)
        if (dir.exists() && dir.isDirectory && dir.path.startsWith(rootDir.path)) {
            dir.deleteRecursively()
            sendResponse(250, "Remove directory successful")
        } else {
            sendResponse(550, "Remove directory failed")
        }
        return true
    }
    
    private fun handleSize(args: String): Boolean {
        val file = resolvePath(args)
        if (file.exists() && file.isFile) {
            sendResponse(213, "${file.length()}")
        } else {
            sendResponse(550, "File not found")
        }
        return true
    }
    
    private fun resolvePath(path: String): File {
        val cleanPath = path.trim()
        return when {
            cleanPath.isEmpty() -> currentDir
            cleanPath == "/" -> rootDir
            cleanPath.startsWith("/") -> File(rootDir, cleanPath.substring(1))
            cleanPath == ".." -> currentDir.parentFile ?: currentDir
            cleanPath.startsWith("../") -> {
                var parent = currentDir.parentFile ?: currentDir
                File(parent, cleanPath.substring(3))
            }
            else -> File(currentDir, cleanPath)
        }.let { file ->
            try {
                val canonical = file.canonicalFile
                if (canonical.path.startsWith(rootDir.path)) canonical else file
            } catch (e: Exception) {
                file
            }
        }
    }
    
    private fun sendResponse(code: Int, message: String) {
        try {
            writer?.println("$code $message")
            Log.d(TAG, "Sent: $code $message")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending response: ${e.message}")
        }
    }
    
    fun close() {
        try {
            dataSocket?.close()
            dataServer?.close()
            reader?.close()
            writer?.close()
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing client: ${e.message}")
        }
    }
}
