package com.example.myapplication123.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.myapplication123.service.DrawingPathData
import com.example.myapplication123.service.DrawingSyncService
import com.example.myapplication123.service.PointData
import kotlin.math.abs

@Composable
fun TeacherScreen(
    syncService: DrawingSyncService,
    modifier: Modifier = Modifier
) {
    // NO local state for paths - teacher only sends, never renders locally
    // Teacher sees drawings only on student's screen via screen sharing
    val isConnected by syncService.isConnected.collectAsState()
    val receivedScreenFrame by syncService.receivedScreenFrame.collectAsState()
    // Don't receive drawing paths on teacher - teacher only sends, doesn't receive drawings
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var textPosition by remember { mutableStateOf<Offset?>(null) }
    var studentScreenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var studentScreenWidth by remember { mutableStateOf(1080f) }
    var studentScreenHeight by remember { mutableStateOf(1920f) }
    
    // Get screen dimensions in composable scope
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Update screen dimensions when connected
    LaunchedEffect(isConnected) {
        if (isConnected) {
            syncService.setScreenDimensions(studentScreenWidth, studentScreenHeight, screenWidth, screenHeight)
        }
    }
    
    // Process received screen frames
    LaunchedEffect(receivedScreenFrame) {
        receivedScreenFrame?.let { frameData ->
            try {
                android.util.Log.d("TeacherScreen", "Received screen frame: ${frameData.size} bytes")
                val bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.size)
                bitmap?.let {
                    android.util.Log.d("TeacherScreen", "Decoded bitmap: ${it.width}x${it.height}")
                    studentScreenBitmap = it
                    studentScreenWidth = it.width.toFloat()
                    studentScreenHeight = it.height.toFloat()
                    // Update screen dimensions for coordinate transformation
                    syncService.setScreenDimensions(studentScreenWidth, studentScreenHeight, screenWidth, screenHeight)
                } ?: run {
                    android.util.Log.w("TeacherScreen", "Failed to decode bitmap from frame data")
                }
            } catch (e: Exception) {
                android.util.Log.e("TeacherScreen", "Error decoding screen frame: ${e.message}", e)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        syncService.initialize()
        // Server is automatically started in MainActivity when WiFi Direct connects
        // This is just a fallback for Network Mode where server might not be started yet
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Student screen view - takes full screen
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val boxWidth = constraints.maxWidth.toFloat()
            val boxHeight = constraints.maxHeight.toFloat()
            
            // Display student screen if available
            Box(modifier = Modifier.fillMaxSize()) {
                studentScreenBitmap?.let { bitmap ->
                    android.util.Log.d("TeacherScreen", "Displaying bitmap: ${bitmap.width}x${bitmap.height}")
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Student Screen",
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )
                } ?: run {
                    // Placeholder when no screen is available
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isConnected) "Waiting for student screen..." else "Not connected",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (isConnected) {
                                Text(
                                    text = "Make sure student has granted screen sharing permission",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Invisible touch overlay - captures touches but doesn't render anything
            // Teacher sees drawings only on student's screen via screen sharing
            // This overlay only captures drag gestures, not clicks, so FABs remain clickable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isConnected) {
                        if (!isConnected) return@pointerInput

                        var currentPath = androidx.compose.ui.graphics.Path()
                        var currentPoints = mutableListOf<Offset>()
                        var lastOffset: Offset? = null
                        var isDragging = false

                        detectDragGestures(
                            onDragStart = { offset: Offset ->
                                isDragging = true
                                currentPath = androidx.compose.ui.graphics.Path()
                                currentPoints = mutableListOf()
                                currentPath.moveTo(offset.x, offset.y)
                                currentPoints.add(offset)
                                lastOffset = offset
                            },
                            onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Offset ->
                                val currentOffset = change.position
                                lastOffset?.let { last ->
                                    val distance = abs(currentOffset.x - last.x) + abs(currentOffset.y - last.y)
                                    if (distance > 2) {
                                        val midX = (currentOffset.x + last.x) / 2
                                        val midY = (currentOffset.y + last.y) / 2
                                        // Use quadraticBezierTo for Compose Path
                                        currentPath.quadraticBezierTo(
                                            last.x, last.y,
                                            midX, midY
                                        )
                                        currentPoints.add(Offset(midX, midY))
                                        currentPoints.add(currentOffset)
                                        lastOffset = currentOffset
                                    }
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                lastOffset?.let {
                                    currentPath.lineTo(it.x, it.y)
                                    if (currentPoints.isEmpty() || currentPoints.last() != it) {
                                        currentPoints.add(it)
                                    }
                                }
                                if (currentPoints.isNotEmpty()) {
                                    val newPath = DrawingPath(
                                        path = currentPath,
                                        points = currentPoints.toList(),
                                        color = androidx.compose.ui.graphics.Color.Black,
                                        strokeWidth = 5f,
                                        text = ""
                                    )
                                    // Transform and send to student - don't render locally
                                    val scaleX = if (studentScreenWidth > 0) boxWidth / studentScreenWidth else 1f
                                    val scaleY = if (studentScreenHeight > 0) boxHeight / studentScreenHeight else 1f
                                    val scale = minOf(scaleX, scaleY)
                                    
                                    val transformedPoints = newPath.points.map { offset ->
                                        val offsetX = (boxWidth - studentScreenWidth * scale) / 2
                                        val offsetY = (boxHeight - studentScreenHeight * scale) / 2
                                        val studentX = (offset.x - offsetX) / scale
                                        val studentY = (offset.y - offsetY) / scale
                                        PointData(studentX, studentY)
                                    }
                                    
                                    val pathData = DrawingPathData(
                                        points = transformedPoints,
                                        color = "#000000",
                                        strokeWidth = newPath.strokeWidth,
                                        text = newPath.text
                                    )
                                    syncService.sendDrawingPath(pathData)
                                }
                            }
                        )
                    }
            )
            
            // NO local rendering - teacher sees everything on student's screen via screen sharing
        }
        
        // Status indicator at top
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shadowElevation = 2.dp
        ) {
            Text(
                text = if (isConnected) {
                    if (studentScreenBitmap != null) "✓ Viewing Student Screen" else "Waiting for screen..."
                } else {
                    "Not connected"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isConnected) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        
        // FAB buttons - positioned at bottom right, above touch overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clear button
            FloatingActionButton(
                onClick = {
                    if (isConnected) {
                        syncService.clearCanvas()
                    }
                },
                modifier = Modifier.alpha(if (isConnected) 1f else 0.5f),
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear Canvas"
                )
            }
            
            // Text button
            FloatingActionButton(
                onClick = {
                    if (isConnected) {
                        showTextDialog = true
                    }
                },
                modifier = Modifier.alpha(if (isConnected) 1f else 0.5f),
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Text"
                )
            }
        }
    }
    
    // Text input dialog
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { 
                showTextDialog = false
                textInput = ""
                textPosition = null
            },
            title = { Text("Add Text") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Enter text") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            // Use center of student screen for text position
                            // Transform teacher screen coordinates to student screen coordinates
                            val teacherPos = textPosition ?: Offset(
                                screenWidth / 2,
                                screenHeight / 2
                            )
                            
                            // Calculate student screen center position
                            // The student screen is displayed in the BoxWithConstraints, so we need to
                            // use the student screen dimensions directly
                            // studentScreenWidth/Height are initialized to 1080x1920, so they're always > 0
                            // They get updated when the first screen frame is received
                            val studentX = studentScreenWidth / 2
                            val studentY = studentScreenHeight / 2
                            
                            android.util.Log.d("TeacherScreen", "Adding text '${textInput}' at student position ($studentX, $studentY)")
                            android.util.Log.d("TeacherScreen", "Student screen dimensions: ${studentScreenWidth}x${studentScreenHeight}")
                            
                            val pathData = DrawingPathData(
                                points = listOf(PointData(studentX, studentY)),
                                color = "#000000",
                                strokeWidth = 20f,
                                text = textInput
                            )
                            
                            android.util.Log.d("TeacherScreen", "Sending text path: text='${pathData.text}', points=${pathData.points.size}, pos=(${pathData.points.firstOrNull()?.x}, ${pathData.points.firstOrNull()?.y})")
                            syncService.sendDrawingPath(pathData)
                        }
                        showTextDialog = false
                        textInput = ""
                        textPosition = null
                    },
                    enabled = textInput.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showTextDialog = false
                    textInput = ""
                    textPosition = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

