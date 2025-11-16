package com.example.myapplication123.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.example.myapplication123.MainActivity
import com.example.myapplication123.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FloatingDrawingOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: DrawingOverlayView? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _drawingPaths = MutableStateFlow<List<DrawingPathData>>(emptyList())
    val drawingPaths: StateFlow<List<DrawingPathData>> = _drawingPaths.asStateFlow()
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        
        // Always call startForeground immediately when service is created
        // This is required when using startForegroundService()
        // For Android 14+, we'll handle the error gracefully if permission is missing
        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: SecurityException) {
            // Android 14+ might throw SecurityException if we don't have the right foreground service type
            // In that case, just show a regular notification
            Log.w("FloatingOverlay", "Could not start foreground service: ${e.message}")
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e("FloatingOverlay", "Error starting foreground: ${e.message}", e)
            // Fallback to regular notification
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            createOverlay()
        }
        
        // Handle path updates from intent
        intent?.let {
            when (it.action) {
                "UPDATE_PATHS" -> {
                    @Suppress("UNCHECKED_CAST")
                    val pathList = it.getSerializableExtra("paths") as? ArrayList<DrawingPathData>
                    pathList?.let { paths ->
                        scope.launch {
                            _drawingPaths.value = paths
                            overlayView?.updatePaths(paths)
                        }
                    }
                }
                "CLEAR_PATHS" -> {
                    clearDrawing()
                }
                else -> {
                    // No action needed for other actions
                }
            }
        }
        
        return START_STICKY
    }
    
    private fun createOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        overlayView = DrawingOverlayView(this)
        
        try {
            windowManager?.addView(overlayView, layoutParams)
            Log.d("FloatingOverlay", "Overlay created successfully")
        } catch (e: Exception) {
            Log.e("FloatingOverlay", "Error creating overlay: ${e.message}", e)
        }
    }
    
    fun addDrawingPath(path: DrawingPathData) {
        scope.launch {
            val currentPaths = _drawingPaths.value.toMutableList()
            currentPaths.add(path)
            _drawingPaths.value = currentPaths
            overlayView?.updatePaths(currentPaths)
        }
    }
    
    fun clearDrawing() {
        scope.launch {
            _drawingPaths.value = emptyList()
            overlayView?.clearPaths()
        }
    }
    
    fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e("FloatingOverlay", "Error removing overlay: ${e.message}")
            }
        }
        overlayView = null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Drawing Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Drawing overlay is active"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Drawing Overlay Active")
            .setContentText("Teacher can draw on your screen")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        scope.cancel()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Only stop foreground if we started it (Android 13 and below)
            stopForeground(true)
        } else {
            // Cancel notification on Android 14+
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }
    
    companion object {
        private const val CHANNEL_ID = "drawing_overlay_channel"
        private const val NOTIFICATION_ID = 1002
    }
    
    private class DrawingOverlayView(context: Context) : View(context) {
        private val paths = mutableListOf<DrawingPathData>()
        private val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        
        // Paint for text rendering
        private val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 48f // Default text size, will be scaled based on strokeWidth
            style = Paint.Style.FILL
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) // More bold
            isFakeBoldText = true // Add fake bold for extra thickness
        }
        
        // Make view non-interactive - all touches pass through
        override fun onTouchEvent(event: MotionEvent?): Boolean {
            // Return false to let touches pass through to underlying apps
            return false
        }
        
        fun updatePaths(newPaths: List<DrawingPathData>) {
            paths.clear()
            paths.addAll(newPaths)
            invalidate()
        }
        
        fun clearPaths() {
            paths.clear()
            invalidate()
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            paths.forEach { pathData ->
                if (pathData.color == "#CLEAR") return@forEach
                
                try {
                    // Check if this is a text path
                    if (pathData.text.isNotEmpty() && pathData.points.isNotEmpty()) {
                        // Draw text without background
                        val pos = pathData.points[0]
                        
                        // Set text properties
                        textPaint.color = Color.parseColor(pathData.color)
                        textPaint.textSize = pathData.strokeWidth * 2.5f // Increased size multiplier (was 1.2f)
                        
                        // Get text metrics for proper baseline positioning
                        val textMetrics = textPaint.fontMetrics
                        val textBaseline = pos.y - textMetrics.top // Position baseline so text top aligns with pos.y
                        
                        // Draw text at baseline (no background)
                        canvas.drawText(pathData.text, pos.x, textBaseline, textPaint)
                        
                        Log.d("DrawingOverlay", "Drew text '${pathData.text}' at (${pos.x}, ${pos.y}), baseline: $textBaseline")
                    } else if (pathData.points.isNotEmpty()) {
                        // Draw path (normal drawing)
                        paint.color = Color.parseColor(pathData.color)
                        paint.strokeWidth = pathData.strokeWidth
                        
                        val path = android.graphics.Path()
                        val first = pathData.points[0]
                        path.moveTo(first.x, first.y)
                        
                        for (i in 1 until pathData.points.size) {
                            val prev = pathData.points[i - 1]
                            val curr = pathData.points[i]
                            
                            if (i == 1) {
                                path.lineTo(curr.x, curr.y)
                            } else {
                                val midX = (prev.x + curr.x) / 2
                                val midY = (prev.y + curr.y) / 2
                                path.quadTo(prev.x, prev.y, midX, midY)
                            }
                        }
                        
                        if (pathData.points.size > 1) {
                            val last = pathData.points.last()
                            path.lineTo(last.x, last.y)
                        }
                        
                        canvas.drawPath(path, paint)
                    }
                } catch (e: Exception) {
                    Log.e("DrawingOverlay", "Error drawing path: ${e.message}", e)
                }
            }
        }
    }
}

