package com.example.myapplication123.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication123.service.DrawingSyncService
import com.example.myapplication123.util.NetworkUtils
import java.net.InetSocketAddress
import java.net.Socket

@Composable
fun DevConnectionScreen(
    syncService: DrawingSyncService,
    isTeacher: Boolean,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val deviceIp = remember { NetworkUtils.getLocalIpAddress(context) }
    
    var serverIp by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8888") }
    val isConnected by syncService.isConnected.collectAsState()
    val connectionMessage by syncService.connectionStatusMessage.collectAsState()
    val isServerRunning by syncService.isServerRunning.collectAsState()
    
    LaunchedEffect(isConnected) {
        if (isConnected) {
            onConnected()
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                            text = "Network Mode",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Connect devices on same WiFi network",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = if (isTeacher) "Teacher - Start Server" else "Student - Connect to Server",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        if (isTeacher) {
            // Teacher starts server
            if (isConnected) {
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
                            text = "✓ Connected!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Student is connected. You can now draw!",
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else if (isServerRunning) {
                // Server running but no client yet
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Server Running",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Waiting for student to connect...",
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Share this IP with Student:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = deviceIp ?: "IP not found",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (deviceIp == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⚠️ Make sure WiFi is connected\nCheck Settings → WiFi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Port: ${port}",
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else {
                // Not started yet
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Button(
                    onClick = {
                        syncService.startServer(port.toIntOrNull() ?: 8888)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Server")
                }
                
                if (connectionMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = connectionMessage ?: "",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        } else {
            // Student connects to server
            OutlinedTextField(
                value = serverIp,
                onValueChange = { serverIp = it },
                label = { Text("Teacher's IP Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g., 192.168.1.10") }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    syncService.connectToServer(serverIp, port.toIntOrNull() ?: 8888)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConnected && serverIp.isNotBlank()
            ) {
                if (connectionMessage?.contains("Connecting") == true) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...")
                } else {
                    Text(if (isConnected) "Connected!" else "Connect")
                }
            }
            
            if (connectionMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (connectionMessage!!.contains("failed") || connectionMessage!!.contains("Error"))
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = connectionMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (connectionMessage!!.contains("failed") || connectionMessage!!.contains("Error"))
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            if (!isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "IP Address Guide:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Requirements:\n" +
                                    "• Teacher and Student on same WiFi\n" +
                                    "• Enter teacher's IP address shown on their screen\n" +
                                    "• Port: ${port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

