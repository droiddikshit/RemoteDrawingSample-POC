package com.example.myapplication123.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import com.example.myapplication123.service.DrawingPathData
import com.example.myapplication123.service.DrawingSyncService

@Composable
fun StudentScreen(
    syncService: DrawingSyncService,
    modifier: Modifier = Modifier
) {
    val receivedPaths by syncService.receivedPaths.collectAsState()
    val isConnected by syncService.isConnected.collectAsState()
    
    // Convert received paths - update immediately when receivedPaths changes
    // Use size and last item to detect changes properly
    val pathsSize = receivedPaths.size
    val lastPathText = receivedPaths.lastOrNull()?.text ?: ""
    
    val paths = remember(pathsSize, lastPathText) {
        android.util.Log.d("StudentScreen", "Converting ${receivedPaths.size} paths, last text: '$lastPathText', all texts: ${receivedPaths.map { "'${it.text}'" }}")
        receivedPaths.map { pathData ->
            try {
                DrawingPath(
                    path = convertToPath(pathData),
                    points = pathData.points.map { p -> Offset(p.x, p.y) },
                    color = Color(android.graphics.Color.parseColor(pathData.color)),
                    strokeWidth = pathData.strokeWidth,
                    text = pathData.text ?: "" // Support text from server
                )
            } catch (e: Exception) {
                android.util.Log.w("StudentScreen", "Error converting path: ${e.message}")
                DrawingPath(
                    path = Path(),
                    points = emptyList(),
                    color = Color.Black,
                    strokeWidth = 5f,
                    text = ""
                )
            }
        }
    }
    
    // Debug: Log text paths
    LaunchedEffect(paths.size) {
        val textPaths = paths.filter { it.text.isNotEmpty() }
        if (textPaths.isNotEmpty()) {
            android.util.Log.d("StudentScreen", "Found ${textPaths.size} text paths: ${textPaths.map { "'${it.text}' at ${it.points.firstOrNull()}" }}")
        }
    }
    
    LaunchedEffect(Unit) {
        syncService.initialize()
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Student Mode - Watching",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = if (isConnected) "✓ Connected" else "Waiting...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isConnected) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Drawing canvas (read-only) with text overlay
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val density = LocalDensity.current
            
            DrawingCanvas(
                modifier = Modifier.fillMaxSize(),
                isDrawable = false,
                paths = paths
            )
            
            // Draw text overlays on top - use absolute positioning
            paths.forEachIndexed { index, drawingPath ->
                if (drawingPath.text.isNotEmpty()) {
                    drawingPath.points.firstOrNull()?.let { pos ->
                        // Convert canvas coordinates (pixels) to dp
                        val xDp = (pos.x / density.density).dp
                        val yDp = (pos.y / density.density).dp
                        
                        android.util.Log.d("StudentScreen", "Drawing text '${drawingPath.text}' at ($xDp, $yDp), color: ${drawingPath.color}")
                        
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
}

private fun convertToPath(pathData: DrawingPathData): Path {
    val path = Path()
    if (pathData.points.isNotEmpty()) {
        val first = pathData.points[0]
        path.moveTo(first.x, first.y)
        
        // Draw smooth curves between points - similar to teacher's drawing style
        for (i in 1 until pathData.points.size) {
            val prev = pathData.points[i - 1]
            val curr = pathData.points[i]
            
            if (i == 1) {
                // First segment - simple line
                path.lineTo(curr.x, curr.y)
            } else {
                // Use quadratic bezier for smooth curves
                val midX = (prev.x + curr.x) / 2
                val midY = (prev.y + curr.y) / 2
                path.quadraticBezierTo(prev.x, prev.y, midX, midY)
            }
        }
        // Ensure last point is reached
        if (pathData.points.size > 1) {
            val last = pathData.points.last()
            path.lineTo(last.x, last.y)
        }
    }
    return path
}

