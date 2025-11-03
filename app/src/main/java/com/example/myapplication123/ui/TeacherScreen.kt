package com.example.myapplication123.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
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
    var paths by remember { mutableStateOf<List<DrawingPath>>(emptyList()) }
    val isConnected by syncService.isConnected.collectAsState()
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var textPosition by remember { mutableStateOf<Offset?>(null) }
    
    // Get screen dimensions in composable scope
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    
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
                        text = "Teacher Mode",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (isConnected) "✓ Connected" else "Not connected",
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
                            paths = emptyList()
                            syncService.clearCanvas()
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
        
        // Drawing canvas with text overlay
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val density = LocalDensity.current
            
            DrawingCanvas(
                modifier = Modifier.fillMaxSize(),
                isDrawable = true,
                paths = paths,
                onPathDrawn = { newPath ->
                paths = paths + newPath
                // Convert DrawingPath to DrawingPathData for socket
                val points = newPath.points.map { offset ->
                    PointData(offset.x, offset.y)
                }
                val pathData = DrawingPathData(
                    points = points,
                    color = "#000000",
                    strokeWidth = newPath.strokeWidth,
                    text = newPath.text
                )
                // Send immediately - no delay
                syncService.sendDrawingPath(pathData)
            },
            onClear = {
                paths = emptyList()
            }
            )
            
            // Draw text overlays on top
            paths.forEachIndexed { index, drawingPath ->
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
                            paths = paths + textPath
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

