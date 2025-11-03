package com.example.myapplication123.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
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
    
    LaunchedEffect(Unit) {
        syncService.initialize()
        // Server is automatically started in MainActivity when WiFi Direct connects
        // This is just a fallback for Network Mode where server might not be started yet
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with clear button
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
                    Text("Clear Canvas")
                }
            }
        }
        
        // Drawing canvas
        DrawingCanvas(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
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
                    strokeWidth = newPath.strokeWidth
                )
                // Always try to send, service will check connection
                syncService.sendDrawingPath(pathData)
            },
            onClear = {
                paths = emptyList()
            }
        )
    }
}

