package com.example.myapplication123.service

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class DrawingSyncService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var inputStream: ObjectInputStream? = null
    private var outputStream: ObjectOutputStream? = null
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectionStatusMessage = MutableStateFlow<String?>(null)
    val connectionStatusMessage: StateFlow<String?> = _connectionStatusMessage.asStateFlow()
    
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()
    
    private val _receivedPaths = MutableStateFlow<List<DrawingPathData>>(emptyList())
    val receivedPaths: StateFlow<List<DrawingPathData>> = _receivedPaths.asStateFlow()
    
    private val allPaths = mutableListOf<DrawingPathData>()
    
    fun initialize() {
        // Socket initialization will happen when WiFi Direct connects
        Log.d("DrawingSync", "Service initialized")
    }
    
    fun startServer(port: Int = 8888) {
        scope.launch {
            try {
                _isServerRunning.value = true
                _connectionStatusMessage.value = "Starting server on port $port..."
                
                // Close existing server socket if any
                serverSocket?.close()
                
                // Create server socket bound to all interfaces (0.0.0.0) explicitly
                serverSocket = ServerSocket(port, 0, java.net.InetAddress.getByName("0.0.0.0"))
                serverSocket?.reuseAddress = true
                
                Log.d("DrawingSync", "Server socket bound to: ${serverSocket?.localSocketAddress}")
                
                _connectionStatusMessage.value = "Server running on port $port!\nWaiting for student..."
                Log.d("DrawingSync", "Server started on port $port, waiting for connection...")
                
                val socket = serverSocket!!.accept()
                Log.d("DrawingSync", "Client connected from ${socket.remoteSocketAddress}")
                _connectionStatusMessage.value = "Student connected!"
                setupConnection(socket)
                startReceiving()
            } catch (e: Exception) {
                Log.e("DrawingSync", "Server error: ${e.message}", e)
                _connectionStatusMessage.value = "Server Error: ${e.message}\n\nTry:\n• Check if port $port is available\n• Restart the app\n• Try a different port"
                _isServerRunning.value = false
                _isConnected.value = false
            }
        }
    }
    
    fun connectToServer(host: String, port: Int = 8888) {
        scope.launch {
            try {
                _connectionStatusMessage.value = "Connecting to $host:$port..."
                
                // Close existing connection if any
                clientSocket?.close()
                
                // Create socket with timeout
                clientSocket = Socket()
                clientSocket!!.connect(
                    java.net.InetSocketAddress(host, port),
                    5000 // 5 second timeout
                )
                
                Log.d("DrawingSync", "Connected to server at $host:$port")
                _connectionStatusMessage.value = "Connected successfully!"
                setupConnection(clientSocket!!)
                startReceiving()
            } catch (e: java.net.ConnectException) {
                Log.e("DrawingSync", "Connection refused: ${e.message}")
                _connectionStatusMessage.value = "Connection Refused!\n\nTroubleshooting:\n✓ Check IP: $host\n✓ Check Port: $port\n✓ Make sure Teacher started server\n✓ Both devices on same WiFi\n✓ Try teacher's WiFi IP from settings"
                _isConnected.value = false
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("DrawingSync", "Connection timeout: ${e.message}")
                _connectionStatusMessage.value = "⏱️ Connection Timeout!\n\nServer not responding.\n\nCheck:\n✓ IP: $host (exactly as shown)\n✓ Port: $port\n✓ Teacher server is running\n✓ Firewall allows port $port\n✓ If teacher on emulator, check computer firewall\n✓ Try different port (8080, 9999)"
                _isConnected.value = false
            } catch (e: Exception) {
                Log.e("DrawingSync", "Connection error: ${e.message}", e)
                _connectionStatusMessage.value = "Connection Failed: ${e.javaClass.simpleName}\n${e.message}\n\nTroubleshooting:\n✓ IP: $host, Port: $port\n✓ Server must be running\n✓ Same WiFi network required"
                _isConnected.value = false
            }
        }
    }
    
    private fun setupConnection(socket: Socket) {
        try {
            socket.soTimeout = 0 // No timeout on read
            outputStream = ObjectOutputStream(socket.getOutputStream())
            outputStream!!.flush()
            inputStream = ObjectInputStream(socket.getInputStream())
            _isConnected.value = true
            _connectionStatusMessage.value = "Connection established!"
            Log.d("DrawingSync", "Streams initialized successfully")
        } catch (e: Exception) {
            Log.e("DrawingSync", "Stream setup error: ${e.message}", e)
            _connectionStatusMessage.value = "Stream setup failed: ${e.message}"
            _isConnected.value = false
        }
    }
    
    fun sendDrawingPath(path: DrawingPathData) {
        if (!_isConnected.value) {
            Log.w("DrawingSync", "Cannot send path - not connected")
            return
        }
        
        scope.launch {
            try {
                Log.d("DrawingSync", "Sending path with ${path.points.size} points")
                outputStream?.writeObject(path)
                outputStream?.flush()
                Log.d("DrawingSync", "Path sent successfully: ${path.points.size} points")
            } catch (e: Exception) {
                Log.e("DrawingSync", "Error sending path: ${e.message}", e)
                _isConnected.value = false
            }
        }
    }
    
    fun clearCanvas() {
        if (!_isConnected.value) return
        
        scope.launch {
            try {
                val clearMessage = DrawingPathData(emptyList(), "#CLEAR", 0f)
                outputStream?.writeObject(clearMessage)
                outputStream?.flush()
                allPaths.clear()
                _receivedPaths.value = emptyList()
                Log.d("DrawingSync", "Canvas cleared")
            } catch (e: Exception) {
                Log.e("DrawingSync", "Error clearing canvas: ${e.message}")
            }
        }
    }
    
    private fun startReceiving() {
        scope.launch {
            try {
                Log.d("DrawingSync", "Starting to receive drawings...")
                while (_isConnected.value && inputStream != null) {
                    try {
                        val path = inputStream!!.readObject() as? DrawingPathData
                        path?.let {
                            Log.d("DrawingSync", "Received path with ${it.points.size} points, color: ${it.color}")
                            if (it.color == "#CLEAR") {
                                // Clear command
                                Log.d("DrawingSync", "Received clear command")
                                allPaths.clear()
                                _receivedPaths.value = emptyList()
                            } else {
                                // Add new path
                                allPaths.add(it)
                                _receivedPaths.value = allPaths.toList()
                                Log.d("DrawingSync", "Total paths now: ${allPaths.size}")
                            }
                        }
                    } catch (e: java.io.EOFException) {
                        Log.d("DrawingSync", "Stream closed normally")
                        break
                    } catch (e: Exception) {
                        Log.e("DrawingSync", "Error reading object: ${e.message}", e)
                        if (e !is java.io.EOFException) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DrawingSync", "Receive error: ${e.message}", e)
                _isConnected.value = false
            }
        }
    }
    
    fun listenToDrawings(): Flow<List<DrawingPathData>> = callbackFlow {
        val job = scope.launch {
            _receivedPaths.collect { paths ->
                trySend(paths)
            }
        }
        
        awaitClose {
            job.cancel()
        }
    }
    
    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            clientSocket?.close()
            serverSocket?.close()
            _isConnected.value = false
            _isServerRunning.value = false
            _connectionStatusMessage.value = null
            allPaths.clear()
            _receivedPaths.value = emptyList()
            Log.d("DrawingSync", "Disconnected")
        } catch (e: Exception) {
            Log.e("DrawingSync", "Disconnect error: ${e.message}")
        }
    }
    
    fun cleanup() {
        disconnect()
        scope.cancel()
    }
}

data class DrawingPathData(
    val points: List<PointData>,
    val color: String = "#000000",
    val strokeWidth: Float = 5f
) : java.io.Serializable

data class PointData(
    val x: Float,
    val y: Float
) : java.io.Serializable
