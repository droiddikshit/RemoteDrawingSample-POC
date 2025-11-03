package com.example.myapplication123.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WiFiDirectService(private val context: Context) {
    private val manager: WifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: Channel? = null
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _groupOwnerAddress = MutableStateFlow<String?>(null)
    val groupOwnerAddress: StateFlow<String?> = _groupOwnerAddress.asStateFlow()
    
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _isEnabled.value = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.d("WiFiDirect", "WiFi Direct state: ${_isEnabled.value}")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    channel?.let { requestPeers(it) }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        channel?.let { ch ->
                            manager.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
                                info?.let {
                                    if (it.groupFormed) {
                                        val ownerAddress = it.groupOwnerAddress?.hostAddress
                                        _groupOwnerAddress.value = ownerAddress
                                        _connectionStatus.value = ConnectionStatus.Connected
                                        Log.d("WiFiDirect", "Connected to peer. GO IP: $ownerAddress")
                                    }
                                }
                            }
                        }
                    } else {
                        _connectionStatus.value = ConnectionStatus.Disconnected
                        _groupOwnerAddress.value = null
                        Log.d("WiFiDirect", "Disconnected from peer")
                    }
                }
            }
        }
    }
    
    fun initialize() {
        channel = manager.initialize(context, context.mainLooper, null)
        context.registerReceiver(receiver, intentFilter)
        discoverPeers()
    }
    
    @RequiresPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
    fun discoverPeers() {
        channel?.let { ch ->
            manager.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("WiFiDirect", "Discovery started")
                }
                
                override fun onFailure(reasonCode: Int) {
                    Log.e("WiFiDirect", "Discovery failed: $reasonCode")
                }
            })
        }
    }
    
    private fun requestPeers(ch: Channel) {
        manager.requestPeers(ch) { peers ->
            _peers.value = peers?.deviceList?.toList() ?: emptyList()
            Log.d("WiFiDirect", "Found ${_peers.value.size} peers")
        }
    }
    
    @RequiresPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
    fun connect(device: WifiP2pDevice, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        channel?.let { ch ->
            val config = WifiP2pConfig().apply {
                deviceAddress = device.deviceAddress
                wps.setup = WpsInfo.PBC
            }
            
            manager.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("WiFiDirect", "Connection initiated")
                    _connectionStatus.value = ConnectionStatus.Connecting
                    onSuccess()
                }
                
                override fun onFailure(reasonCode: Int) {
                    Log.e("WiFiDirect", "Connection failed: $reasonCode")
                    _connectionStatus.value = ConnectionStatus.Disconnected
                    onFailure(reasonCode)
                }
            })
        }
    }
    
    fun disconnect() {
        channel?.let { ch ->
            manager.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("WiFiDirect", "Disconnected")
                    _connectionStatus.value = ConnectionStatus.Disconnected
                }
                
                override fun onFailure(reasonCode: Int) {
                    Log.e("WiFiDirect", "Disconnect failed: $reasonCode")
                }
            })
        }
    }
    
    fun cleanup() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e("WiFiDirect", "Error unregistering receiver: ${e.message}")
        }
    }
}

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object Connected : ConnectionStatus()
}

