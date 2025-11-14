package com.example.myapplication123.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import com.example.myapplication123.service.DrawingPathData
import com.example.myapplication123.service.DrawingSyncService
import com.example.myapplication123.service.FloatingDrawingOverlayService
import com.example.myapplication123.service.ScreenSharingService

@Composable
fun StudentScreen(
    syncService: DrawingSyncService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    val receivedPaths by syncService.receivedPaths.collectAsState()
    val isConnected by syncService.isConnected.collectAsState()
    
    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }
    var screenSharingStarted by remember { mutableStateOf(false) }
    
    // Screen sharing launcher
    val screenShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, ScreenSharingService::class.java).apply {
                putExtra(ScreenSharingService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenSharingService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            screenSharingStarted = true
        }
    }
    
    // Overlay permission launcher
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = checkOverlayPermission(context)
        if (hasOverlayPermission) {
            startOverlayService(context, null)
        }
    }
    
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
    
    // Update overlay with received paths - send to overlay service
    LaunchedEffect(receivedPaths) {
        if (hasOverlayPermission && isConnected) {
            val intent = Intent(context, FloatingDrawingOverlayService::class.java)
            if (receivedPaths.isNotEmpty()) {
                intent.action = "UPDATE_PATHS"
                intent.putExtra("paths", ArrayList(receivedPaths))
            } else {
                intent.action = "CLEAR_PATHS"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    // Setup screen sharing callback - do this early, before starting screen sharing
    LaunchedEffect(Unit) {
        ScreenSharingService.setFrameCallback { frameData ->
            android.util.Log.d("StudentScreen", "Screen frame callback received: ${frameData.size} bytes")
            if (isConnected) {
                syncService.sendScreenFrame(frameData)
            } else {
                android.util.Log.w("StudentScreen", "Not connected, dropping frame")
            }
        }
    }
    
    // Start screen sharing when connected
    LaunchedEffect(isConnected) {
        if (isConnected && !screenSharingStarted) {
            android.util.Log.d("StudentScreen", "Connected, starting screen sharing setup")
            // Send screen dimensions to teacher
            syncService.setScreenDimensions(screenWidth, screenHeight, screenWidth, screenHeight)
            
            // Ensure callback is set before requesting screen sharing
            ScreenSharingService.setFrameCallback { frameData ->
                android.util.Log.d("StudentScreen", "Screen frame callback: ${frameData.size} bytes")
                if (isConnected) {
                    syncService.sendScreenFrame(frameData)
                }
            }
            
            // Request screen sharing
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            
            // Start overlay service
            if (hasOverlayPermission) {
                startOverlayService(context, null)
            } else {
                // Request overlay permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    overlayPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                    )
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        syncService.initialize()
    }
    
    // Cleanup on disconnect
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            // Stop services
            context.stopService(Intent(context, ScreenSharingService::class.java))
            context.stopService(Intent(context, FloatingDrawingOverlayService::class.java))
            screenSharingStarted = false
        }
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
                Column {
                    Text(
                        text = "Student Mode - Screen Sharing",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (isConnected) "✓ Connected & Sharing" else "Waiting...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!hasOverlayPermission && isConnected) {
                        Text(
                            text = "⚠ Overlay permission needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun startOverlayService(context: Context, existingService: FloatingDrawingOverlayService?) {
    val intent = Intent(context, FloatingDrawingOverlayService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

