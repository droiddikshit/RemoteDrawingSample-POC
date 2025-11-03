package com.example.myapplication123

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication123.service.ConnectionStatus
import com.example.myapplication123.service.DrawingSyncService
import com.example.myapplication123.service.WiFiDirectService
import com.example.myapplication123.ui.ConnectionScreen
import com.example.myapplication123.ui.DevConnectionScreen
import com.example.myapplication123.ui.RoleSelectionScreen
import com.example.myapplication123.ui.StudentScreen
import com.example.myapplication123.ui.TeacherScreen
import com.example.myapplication123.ui.theme.MyApplication123Theme
import com.example.myapplication123.util.PermissionsHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var wifiService: WiFiDirectService
    private lateinit var syncService: DrawingSyncService
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions granted, can proceed
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        wifiService = WiFiDirectService(this)
        syncService = DrawingSyncService()
        
        // Request permissions
        requestPermissionsIfNeeded()
        
        setContent {
            MyApplication123Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DrawingApp(
                        wifiService = wifiService,
                        syncService = syncService
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        wifiService.initialize()
    }
    
    override fun onPause() {
        super.onPause()
        wifiService.cleanup()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        syncService.cleanup()
        wifiService.cleanup()
    }
    
    private fun requestPermissionsIfNeeded() {
        val permissions = PermissionsHelper.getRequiredPermissions()
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions)
        }
    }
}

@Composable
fun DrawingApp(
    wifiService: WiFiDirectService,
    syncService: DrawingSyncService
) {
    val navController = rememberNavController()
    val connectionStatus by wifiService.connectionStatus.collectAsState()
    
    NavHost(
        navController = navController,
        startDestination = "role_selection"
    ) {
        composable("role_selection") {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    when (role) {
                        "teacher" -> navController.navigate("connection/teacher")
                        "student" -> navController.navigate("connection/student")
                        "dev_teacher" -> navController.navigate("dev_connection/teacher")
                        "dev_student" -> navController.navigate("dev_connection/student")
                    }
                }
            )
        }
        
        composable("dev_connection/{role}") { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "teacher"
            val isTeacher = role == "teacher"
            
            DevConnectionScreen(
                syncService = syncService,
                isTeacher = isTeacher,
                onConnected = {
                    if (isTeacher) {
                        navController.navigate("teacher") {
                            popUpTo("role_selection") { inclusive = false }
                        }
                    } else {
                        navController.navigate("student") {
                            popUpTo("role_selection") { inclusive = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        composable("connection/{role}") { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "teacher"
            val isTeacher = role == "teacher"
            
            val groupOwnerAddress by wifiService.groupOwnerAddress.collectAsState()
            val connectionStatus by wifiService.connectionStatus.collectAsState()
            
            LaunchedEffect(connectionStatus, groupOwnerAddress) {
                if (connectionStatus is ConnectionStatus.Connected && groupOwnerAddress != null) {
                    // Once connected via WiFi Direct, establish socket connection automatically
                    if (isTeacher) {
                        // Teacher starts server automatically
                        syncService.startServer(8888)
                        // Give server a moment to start
                        kotlinx.coroutines.delay(500)
                        navController.navigate("teacher") {
                            popUpTo("role_selection") { inclusive = false }
                        }
                    } else {
                        // Student connects to teacher automatically using WiFi Direct IP
                        val ownerAddress = groupOwnerAddress ?: "192.168.49.1"
                        syncService.connectToServer(ownerAddress, 8888)
                        navController.navigate("student") {
                            popUpTo("role_selection") { inclusive = false }
                        }
                    }
                }
            }
            
            ConnectionScreen(
                wifiService = wifiService,
                isTeacher = isTeacher,
                onConnected = {
                    // This is called when WiFi Direct connects
                    // Socket connection happens automatically in LaunchedEffect above
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        composable("teacher") {
            TeacherScreen(
                syncService = syncService,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        composable("student") {
            StudentScreen(
                syncService = syncService,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}