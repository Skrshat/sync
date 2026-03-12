package com.offlinesync.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

class MDNSService(private val context: Context) {

    private var jmDNS: JmDNS? = null
    private var serviceInfo: ServiceInfo? = null
    private val serviceListener: ServiceListener = MyServiceListener()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _discoveredServices = MutableSharedFlow<MDNSServiceInfo>()
    val discoveredServices = _discoveredServices.asSharedFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val webServer = WebServer(context) // Initialize WebServer here

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val linkProperties = connectivityManager.getLinkProperties(network)
            val ipAddress = linkProperties?.linkAddresses?.firstOrNull {
                it.address is InetAddress && !it.address.isLoopbackAddress && !it.address.isLinkLocalAddress
            }?.address

            ipAddress?.let {
                Log.d(TAG, "Network available, IP: ${it.hostAddress}")
                startDiscovery(it)
            } ?: Log.e(TAG, "No suitable IP address found for network: $network")
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "Network lost: $network")
            stopDiscovery()
        }
    }

    fun startDiscovery() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun startDiscovery(address: InetAddress) {
        scope.launch {
            try {
                if (jmDNS != null) {
                    stopDiscovery() // Ensure previous instance is stopped
                }
                jmDNS = JmDNS.create(address, "OfflineSyncHost")
                jmDNS?.addServiceListener(SERVICE_TYPE, serviceListener)
                webServer.start(address) // Start the web server
                registerService(address)
                Log.d(TAG, "JmDNS discovery started on ${address.hostAddress}")
            } catch (e: IOException) {
                Log.e(TAG, "JmDNS creation failed: ${e.message}")
            }
        }
    }

    fun stopDiscovery() {
        scope.launch {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
                jmDNS?.removeServiceListener(SERVICE_TYPE, serviceListener)
                jmDNS?.unregisterAllServices()
                jmDNS?.close()
                jmDNS = null
                serviceInfo = null
                webServer.stop() // Stop the web server
                Log.d(TAG, "JmDNS discovery stopped")
            } catch (e: IOException) {
                Log.e(TAG, "JmDNS close failed: ${e.message}")
            }
        }
    }

    private fun registerService(address: InetAddress) {
        val port = webServer.port // Use the actual port from WebServer
        val serviceName = "OfflineSyncDevice_${android.os.Build.MODEL}"
        val properties = mapOf(
            "path" to "/",
            "deviceType" to "android",
            "ipAddress" to address.hostAddress,
            "port" to port.toString() // Add port to properties for discovery clients
        )
        serviceInfo = ServiceInfo.create(
            SERVICE_TYPE,
            serviceName,
            port,
            0, // weight
            0, // priority
            properties // text properties as a Map
        )
        jmDNS?.registerService(serviceInfo)
        Log.d(TAG, "Service registered: $serviceName on port $port")
    }

    private inner class MyServiceListener : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            Log.d(TAG, "Service added: ${event.name}")
            // Request service info to get details like IP and port
            jmDNS?.requestServiceInfo(event.type, event.name, true)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            Log.d(TAG, "Service removed: ${event.name}")
            scope.launch {
                _discoveredServices.emit(
                    MDNSServiceInfo(
                        name = event.name,
                        type = event.type,
                        address = null,
                        port = -1,
                        isRemoved = true
                    )
                )
            }
        }

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info
            val address = info.inet4Addresses.firstOrNull()?.hostAddress
            val port = info.port
            val deviceType = info.getPropertyString("deviceType") ?: "unknown"

            if (address != null) {
                val mDNSServiceInfo = MDNSServiceInfo(
                    name = info.name,
                    type = info.type,
                    address = address,
                    port = port,
                    deviceType = deviceType
                )
                Log.d(TAG, "Service resolved: $mDNSServiceInfo")
                scope.launch {
                    _discoveredServices.emit(mDNSServiceInfo)
                }
            } else {
                Log.e(TAG, "Service resolved but no IP address found: ${info.name}")
            }
        }
    }

    data class MDNSServiceInfo(
        val name: String,
        val type: String,
        val address: String?,
        val port: Int,
        val deviceType: String = "unknown",
        val isRemoved: Boolean = false
    )

    companion object {
        private const val TAG = "MDNSService"
        private const val SERVICE_TYPE = "_offlinesync._tcp.local."
    }
}
