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
    
    private val _receivedScreenFrame = MutableStateFlow<ByteArray?>(null)
    val receivedScreenFrame: StateFlow<ByteArray?> = _receivedScreenFrame.asStateFlow()
    
    private val allPaths = mutableListOf<DrawingPathData>()
    
    // Screen dimensions for coordinate transformation
    private var studentScreenWidth = 1080f
    private var studentScreenHeight = 1920f
    private var teacherScreenWidth = 1080f
    private var teacherScreenHeight = 1920f
    
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
                try {
                    clientSocket?.close()
                } catch (e: Exception) {
                    // Ignore
                }
                
                // Create socket with timeout
                clientSocket = Socket()
                clientSocket!!.soTimeout = 0 // No read timeout
                clientSocket!!.tcpNoDelay = true // Disable Nagle's algorithm
                clientSocket!!.connect(
                    java.net.InetSocketAddress(host, port),
                    10000 // 10 second timeout
                )
                
                // Verify socket is connected and open
                if (clientSocket!!.isClosed || !clientSocket!!.isConnected) {
                    throw java.net.SocketException("Socket not properly connected")
                }
                
                Log.d("DrawingSync", "Connected to server at $host:$port, socket open: ${!clientSocket!!.isClosed}")
                _connectionStatusMessage.value = "Connected successfully!"
                setupConnection(clientSocket!!)
                
                // Only start receiving if connection was successful
                if (_isConnected.value) {
                    startReceiving()
                }
            } catch (e: java.net.ConnectException) {
                Log.e("DrawingSync", "Connection refused: ${e.message}")
                _connectionStatusMessage.value = "Connection Refused!\n\nTroubleshooting:\n✓ Check IP: $host\n✓ Check Port: $port\n✓ Make sure Teacher started server\n✓ Both devices on same WiFi\n✓ Try teacher's WiFi IP from settings"
                _isConnected.value = false
                try {
                    clientSocket?.close()
                } catch (closeEx: Exception) {
                    // Ignore
                }
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("DrawingSync", "Connection timeout: ${e.message}")
                _connectionStatusMessage.value = "⏱️ Connection Timeout!\n\nServer not responding.\n\nCheck:\n✓ IP: $host (exactly as shown)\n✓ Port: $port\n✓ Teacher server is running\n✓ Firewall allows port $port\n✓ If teacher on emulator, check computer firewall\n✓ Try different port (8080, 9999)"
                _isConnected.value = false
                try {
                    clientSocket?.close()
                } catch (closeEx: Exception) {
                    // Ignore
                }
            } catch (e: java.net.SocketException) {
                Log.e("DrawingSync", "Socket error: ${e.message}", e)
                _connectionStatusMessage.value = "Socket Error: ${e.message}\n\nTroubleshooting:\n✓ IP: $host, Port: $port\n✓ Server must be running\n✓ Same WiFi network required\n✓ Try restarting both apps"
                _isConnected.value = false
                try {
                    clientSocket?.close()
                } catch (closeEx: Exception) {
                    // Ignore
                }
            } catch (e: Exception) {
                Log.e("DrawingSync", "Connection error: ${e.message}", e)
                _connectionStatusMessage.value = "Connection Failed: ${e.javaClass.simpleName}\n${e.message}\n\nTroubleshooting:\n✓ IP: $host, Port: $port\n✓ Server must be running\n✓ Same WiFi network required"
                _isConnected.value = false
                try {
                    clientSocket?.close()
                } catch (closeEx: Exception) {
                    // Ignore
                }
            }
        }
    }
    
    private fun setupConnection(socket: Socket) {
        try {
            // Check if socket is still open
            if (socket.isClosed) {
                Log.e("DrawingSync", "Socket is already closed")
                _connectionStatusMessage.value = "Stream setup failed: Socket closed"
                _isConnected.value = false
                return
            }
            
            socket.soTimeout = 0 // No timeout on read
            socket.tcpNoDelay = true // Disable Nagle's algorithm for lower latency
            
            // Create output stream first and flush to send header
            // This sends the ObjectOutputStream header to the other side
            outputStream = ObjectOutputStream(socket.getOutputStream())
            outputStream!!.flush()
            
            // Check socket is still open before creating input stream
            if (socket.isClosed) {
                Log.e("DrawingSync", "Socket closed before creating input stream")
                _connectionStatusMessage.value = "Stream setup failed: Socket closed"
                _isConnected.value = false
                return
            }
            
            // Create input stream (this will wait for the header from the other side)
            // ObjectInputStream constructor blocks until it receives the header
            inputStream = ObjectInputStream(socket.getInputStream())
            
            _isConnected.value = true
            _connectionStatusMessage.value = "Connection established!"
            Log.d("DrawingSync", "Streams initialized successfully")
        } catch (e: java.net.SocketException) {
            Log.e("DrawingSync", "Stream setup error: Socket closed or error - ${e.message}", e)
            _connectionStatusMessage.value = "Stream setup failed: ${e.message}"
            _isConnected.value = false
            try {
                socket.close()
            } catch (closeEx: Exception) {
                // Ignore
            }
        } catch (e: Exception) {
            Log.e("DrawingSync", "Stream setup error: ${e.message}", e)
            _connectionStatusMessage.value = "Stream setup failed: ${e.message}"
            _isConnected.value = false
            try {
                socket.close()
            } catch (closeEx: Exception) {
                // Ignore
            }
        }
    }
    
    fun sendDrawingPath(path: DrawingPathData) {
        if (!_isConnected.value) {
            Log.w("DrawingSync", "Cannot send path - not connected")
            return
        }
        
        // Transform coordinates from teacher screen to student screen
        val transformedPath = transformCoordinatesToStudent(path)
        
        // Send immediately on IO dispatcher for faster transmission
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("DrawingSync", "Sending path: ${transformedPath.points.size} points, text: '${transformedPath.text}'")
                outputStream?.writeObject(transformedPath)
                outputStream?.flush()
                Log.d("DrawingSync", "Path sent successfully")
            } catch (e: Exception) {
                Log.e("DrawingSync", "Error sending path: ${e.message}", e)
                _isConnected.value = false
            }
        }
    }
    
    fun sendScreenFrame(frameData: ByteArray) {
        if (!_isConnected.value) return
        
        scope.launch(Dispatchers.IO) {
            try {
                val screenData = ScreenFrameData(frameData)
                outputStream?.writeObject(screenData)
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e("DrawingSync", "Error sending screen frame: ${e.message}", e)
            }
        }
    }
    
    fun setScreenDimensions(studentWidth: Float, studentHeight: Float, teacherWidth: Float, teacherHeight: Float) {
        studentScreenWidth = studentWidth
        studentScreenHeight = studentHeight
        teacherScreenWidth = teacherWidth
        teacherScreenHeight = teacherHeight
        Log.d("DrawingSync", "Screen dimensions set - Student: ${studentWidth}x${studentHeight}, Teacher: ${teacherWidth}x${teacherHeight}")
    }
    
    private fun transformCoordinatesToStudent(path: DrawingPathData): DrawingPathData {
        // Skip transformation for text paths - they already have student coordinates
        if (path.text.isNotEmpty()) {
            Log.d("DrawingSync", "Skipping transformation for text path: '${path.text}'")
            return path
        }
        
        if (studentScreenWidth == 0f || studentScreenHeight == 0f || teacherScreenWidth == 0f || teacherScreenHeight == 0f) {
            return path // No transformation if dimensions not set
        }
        
        val scaleX = studentScreenWidth / teacherScreenWidth
        val scaleY = studentScreenHeight / teacherScreenHeight
        
        val transformedPoints = path.points.map { point ->
            PointData(point.x * scaleX, point.y * scaleY)
        }
        
        return path.copy(points = transformedPoints)
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
                        val obj = inputStream!!.readObject()
                        
                        when (obj) {
                            is DrawingPathData -> {
                                val path = obj
                                Log.d("DrawingSync", "Received path with ${path.points.size} points, color: ${path.color}, text: '${path.text}'")
                                if (path.color == "#CLEAR") {
                                    // Clear command
                                    Log.d("DrawingSync", "Received clear command")
                                    allPaths.clear()
                                    _receivedPaths.value = emptyList()
                                } else {
                                    // Add new path
                                    allPaths.add(path)
                                    // Update immediately - no delay
                                    _receivedPaths.value = allPaths.toList()
                                    Log.d("DrawingSync", "Total paths now: ${allPaths.size}, text: '${path.text}', all texts: ${allPaths.map { "'${it.text}'" }}")
                                }
                            }
                            is ScreenFrameData -> {
                                // Received screen frame from student
                                Log.d("DrawingSync", "Received screen frame: ${obj.frameData.size} bytes")
                                _receivedScreenFrame.value = obj.frameData
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
    val strokeWidth: Float = 5f,
    val text: String = "" // For text tool
) : java.io.Serializable

data class PointData(
    val x: Float,
    val y: Float
) : java.io.Serializable

data class ScreenFrameData(
    val frameData: ByteArray
) : java.io.Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScreenFrameData
        return frameData.contentEquals(other.frameData)
    }
    
    override fun hashCode(): Int {
        return frameData.contentHashCode()
    }
}
