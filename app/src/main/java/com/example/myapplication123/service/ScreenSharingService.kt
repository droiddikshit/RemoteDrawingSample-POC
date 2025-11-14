package com.example.myapplication123.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication123.MainActivity
import com.example.myapplication123.R
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenSharingService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private val isSharing = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var screenWidth = 1080
    private var screenHeight = 1920
    private var screenDensity = 420
    
    companion object {
        private const val CHANNEL_ID = "screen_sharing_channel"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        
        private var frameCallback: ((ByteArray) -> Unit)? = null
        
        fun setFrameCallback(callback: (ByteArray) -> Unit) {
            frameCallback = callback
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
        // Don't start foreground here - wait until MediaProjection is obtained
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: return START_NOT_STICKY
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        
        if (resultData != null && !isSharing.get()) {
            // Start foreground service before starting screen sharing
            try {
                startForeground(NOTIFICATION_ID, createNotification())
            } catch (e: SecurityException) {
                Log.e("ScreenSharing", "Failed to start foreground service: ${e.message}")
                // Continue anyway - screen sharing might still work
            }
            startScreenSharing(resultCode, resultData)
        }
        
        return START_STICKY
    }
    
    private fun startScreenSharing(resultCode: Int, resultData: Intent) {
        try {
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
            
            // Register callback before creating VirtualDisplay (required by Android)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d("ScreenSharing", "MediaProjection stopped")
                    stopSharing()
                }
            }, null)
            
            val displayMetrics = resources.displayMetrics
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            screenDensity = displayMetrics.densityDpi
            
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenSharing",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
            
            isSharing.set(true)
            startCapturing()
            
            Log.d("ScreenSharing", "Screen sharing started: ${screenWidth}x${screenHeight}")
        } catch (e: Exception) {
            Log.e("ScreenSharing", "Error starting screen sharing: ${e.message}", e)
        }
    }
    
    private fun startCapturing() {
        scope.launch {
            while (isSharing.get()) {
                try {
                    val image = imageReader?.acquireLatestImage()
                    image?.let { processImage(it) }
                    delay(100) // Capture at ~10 FPS
                } catch (e: Exception) {
                    Log.e("ScreenSharing", "Error capturing frame: ${e.message}")
                }
            }
        }
    }
    
    private fun processImage(image: Image) {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            
            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            // Crop to actual screen size
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            
            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val imageBytes = outputStream.toByteArray()
            
            // Invoke callback if set
            frameCallback?.let { callback ->
                try {
                    callback(imageBytes)
                    Log.d("ScreenSharing", "Frame sent: ${imageBytes.size} bytes")
                } catch (e: Exception) {
                    Log.e("ScreenSharing", "Error in frame callback: ${e.message}", e)
                }
            } ?: run {
                Log.w("ScreenSharing", "Frame callback not set, frame dropped")
            }
            
            bitmap.recycle()
            croppedBitmap.recycle()
            image.close()
        } catch (e: Exception) {
            Log.e("ScreenSharing", "Error processing image: ${e.message}", e)
            image.close()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen sharing is active"
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
            .setContentTitle("Screen Sharing Active")
            .setContentText("Your screen is being shared with teacher")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    fun stopSharing() {
        isSharing.set(false)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        stopForeground(true)
        stopSelf()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopSharing()
        scope.cancel()
    }
}

