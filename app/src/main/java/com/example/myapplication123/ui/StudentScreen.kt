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
import com.example.myapplication123.service.DrawingPathData
import com.example.myapplication123.service.DrawingSyncService

@Composable
fun StudentScreen(
    syncService: DrawingSyncService,
    modifier: Modifier = Modifier
) {
    val receivedPaths by syncService.receivedPaths.collectAsState()
    val isConnected by syncService.isConnected.collectAsState()
    
    // Convert received paths to DrawingPath objects - update reactively when receivedPaths changes
    val paths = derivedStateOf {
        receivedPaths.map { pathData ->
            DrawingPath(
                path = convertToPath(pathData),
                points = pathData.points.map { p -> Offset(p.x, p.y) },
                color = Color(android.graphics.Color.parseColor(pathData.color)),
                strokeWidth = pathData.strokeWidth
            )
        }
    }.value
    
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
        
        // Drawing canvas (read-only)
        DrawingCanvas(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            isDrawable = false,
            paths = paths
        )
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

