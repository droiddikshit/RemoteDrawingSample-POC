package com.example.myapplication123.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.net.NetworkInterface
import java.util.Enumeration

object NetworkUtils {
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }
    
    fun getLocalIpAddress(context: Context): String? {
        return try {
            // Try WiFi first
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipAddress = wifiInfo?.ipAddress
            
            if (ipAddress != null && ipAddress != 0) {
                String.format(
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            } else {
                // Fallback to network interfaces
                getIpFromNetworkInterfaces()
            }
        } catch (e: Exception) {
            // Fallback to network interfaces
            getIpFromNetworkInterfaces()
        }
    }
    
    private fun getIpFromNetworkInterfaces(): String? {
        return try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}

