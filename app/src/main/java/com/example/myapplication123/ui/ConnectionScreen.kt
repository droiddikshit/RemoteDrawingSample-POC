package com.example.myapplication123.ui

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication123.service.ConnectionStatus
import com.example.myapplication123.service.WiFiDirectService

@Composable
fun ConnectionScreen(
    wifiService: WiFiDirectService,
    isTeacher: Boolean,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled by wifiService.isEnabled.collectAsState()
    val peers by wifiService.peers.collectAsState()
    val connectionStatus by wifiService.connectionStatus.collectAsState()
    val groupOwnerAddress by wifiService.groupOwnerAddress.collectAsState()
    
    LaunchedEffect(connectionStatus, groupOwnerAddress) {
        // Only call onConnected when both WiFi Direct is connected AND we have the IP
        if (connectionStatus is ConnectionStatus.Connected && groupOwnerAddress != null) {
            // Small delay to ensure connection is stable
            kotlinx.coroutines.delay(1000)
            onConnected()
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isTeacher) "Teacher - Waiting for Student" else "Student - Find Teacher",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        if (!isEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "WiFi Direct is disabled",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Please enable WiFi Direct in Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            when (connectionStatus) {
                is ConnectionStatus.Connecting -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connecting...")
                }
                is ConnectionStatus.Connected -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "WiFi Direct Connected!",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (groupOwnerAddress != null) {
                                    "IP: $groupOwnerAddress\nSetting up drawing connection..."
                                } else {
                                    "Getting connection details..."
                                },
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                is ConnectionStatus.Disconnected -> {
                    Button(
                        onClick = { wifiService.discoverPeers() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Search for Devices")
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (peers.isEmpty()) {
                        Text(
                            text = "No devices found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Available Devices:",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(peers) { device ->
                                DeviceItem(
                                    device = device,
                                    onClick = {
                                        wifiService.connect(
                                            device,
                                            onSuccess = { },
                                            onFailure = { }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: WifiP2pDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName.ifEmpty { "Unknown Device" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = device.deviceAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            val statusText = when (device.status) {
                WifiP2pDevice.AVAILABLE -> "Available"
                WifiP2pDevice.INVITED -> "Invited"
                WifiP2pDevice.CONNECTED -> "Connected"
                WifiP2pDevice.FAILED -> "Failed"
                WifiP2pDevice.UNAVAILABLE -> "Unavailable"
                else -> "Unknown"
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

