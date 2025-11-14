package com.example.myapplication123.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
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

@Composable
fun TeacherScreen(
    syncService: DrawingSyncService,
    modifier: Modifier = Modifier
) {
    // Only track text paths - drawing paths are managed by DrawingCanvas locally
    var textPaths by remember { mutableStateOf<List<DrawingPath>>(emptyList()) }
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
    
    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with clear button and text button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Teacher Mode - Viewing Student Screen",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (isConnected) {
                            if (studentScreenBitmap != null) "✓ Connected & Viewing" else "✓ Connected - Waiting for screen..."
                        } else {
                            "Not connected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showTextDialog = true
                        },
                        enabled = isConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Add Text")
                    }
                    Button(
                        onClick = {
                            textPaths = emptyList()
                            syncService.clearCanvas()
                            // Trigger DrawingCanvas clear by updating clearTrigger
                            // The LaunchedEffect in DrawingCanvas will detect empty paths and clear localPaths
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        enabled = isConnected
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
        
        // Student screen view with drawing overlay
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
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
            
            // Drawing overlay on top of student screen (transparent background)
            // Note: DrawingCanvas maintains its own localPaths in teacher mode, so we don't need to track paths here
            DrawingCanvas(
                modifier = Modifier.fillMaxSize(),
                isDrawable = true,
                paths = emptyList(), // Don't pass paths - DrawingCanvas manages its own in teacher mode
                drawBackground = false,
                onPathDrawn = { newPath ->
                    // Don't add to paths - DrawingCanvas already rendered it locally
                    // Just send to student
                    // Transform coordinates from teacher view to student screen coordinates
                    val scaleX = if (studentScreenWidth > 0) boxWidth / studentScreenWidth else 1f
                    val scaleY = if (studentScreenHeight > 0) boxHeight / studentScreenHeight else 1f
                    val scale = minOf(scaleX, scaleY)
                    
                    // Convert coordinates back to student screen space
                    val transformedPoints = newPath.points.map { offset ->
                        // Account for centering if screen is smaller than view
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
                    // Send immediately - coordinates will be transformed in DrawingSyncService
                    syncService.sendDrawingPath(pathData)
                },
                onClear = {
                    textPaths = emptyList()
                }
            )
            
            // Draw text overlays on top
            textPaths.forEachIndexed { index, drawingPath ->
                if (drawingPath.text.isNotEmpty()) {
                    drawingPath.points.firstOrNull()?.let { pos ->
                        // Convert canvas coordinates (pixels) to dp
                        val xDp = (pos.x / density.density).dp
                        val yDp = (pos.y / density.density).dp
                        
                        Text(
                            text = drawingPath.text,
                            style = TextStyle(
                                fontSize = (drawingPath.strokeWidth * 1.2).sp,
                                fontWeight = FontWeight.Bold,
                                color = drawingPath.color
                            ),
                            modifier = Modifier.offset(x = xDp, y = yDp)
                        )
                    }
                }
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
                            // Use center of screen as default position
                            val pos = textPosition ?: Offset(
                                screenWidth / 2,
                                screenHeight / 2
                            )
                            android.util.Log.d("TeacherScreen", "Adding text '${textInput}' at position ($pos)")
                            val textPath = DrawingPath(
                                path = androidx.compose.ui.graphics.Path(),
                                points = listOf(pos),
                                color = androidx.compose.ui.graphics.Color.Black,
                                strokeWidth = 20f,
                                text = textInput
                            )
                            textPaths = textPaths + textPath
                            val pathData = DrawingPathData(
                                points = listOf(PointData(pos.x, pos.y)),
                                color = "#000000",
                                strokeWidth = 20f,
                                text = textInput
                            )
                            android.util.Log.d("TeacherScreen", "Sending text path: text='${pathData.text}', pos=(${pathData.points.firstOrNull()})")
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

