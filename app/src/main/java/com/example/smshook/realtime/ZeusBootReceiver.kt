package com.example.smshook.realtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class ZeusBootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ZeusBootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Device boot completed - starting Zeus Realtime Service")
                startRealtimeService(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (intent.dataString?.contains(context.packageName) == true) {
                    Log.d(TAG, "Zeus app updated - restarting Realtime Service")
                    startRealtimeService(context)
                }
            }
            "android.intent.action.QUICKBOOT_POWERON" -> {
                // HTC devices
                Log.d(TAG, "HTC quick boot - starting Zeus Realtime Service")
                startRealtimeService(context)
            }
        }
    }
    
    private fun startRealtimeService(context: Context) {
        try {
            val serviceIntent = Intent(context, RealtimeService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(TAG, "Zeus Realtime Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Zeus Realtime Service: ${e.message}")
        }
    }
}
