package com.example.smshook.fcm

import android.util.Log
import com.example.smshook.api.ZeusApi
import com.example.smshook.api.ZeusIds
import com.example.smshook.logs.LogLevel
import com.example.smshook.logs.LogManager
import com.example.smshook.work.ZeusWork
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ZeusFcmService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "ZeusFcmService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM Token: $token")
        
        LogManager.addLog(LogLevel.FCM, TAG, "New FCM token received", "Token: ${token.take(20)}...")
        
        // Send token to your server
        val deviceId = ZeusIds.deviceId(applicationContext)
        ZeusApi.registerFcm(applicationContext, deviceId, token)
        
        LogManager.addLog(LogLevel.API, TAG, "FCM token registered with server", "Device ID: $deviceId")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "FCM Message received: ${remoteMessage.data}")
        LogManager.addLog(LogLevel.FCM, TAG, "FCM message received", "Data: ${remoteMessage.data}")
        
        val data = remoteMessage.data
        when (data["action"]) {
            "run" -> {
                val jobId = data["jobId"] ?: return
                Log.d(TAG, "Enqueueing USSD job: $jobId")
                LogManager.addLog(LogLevel.FCM, TAG, "USSD job enqueued", "Job ID: $jobId")
                ZeusWork.enqueueRunJob(applicationContext, jobId)
            }
            else -> {
                Log.w(TAG, "Unknown FCM action: ${data["action"]}")
                LogManager.addLog(LogLevel.WARN, TAG, "Unknown FCM action", "Action: ${data["action"]}")
            }
        }
    }
}
