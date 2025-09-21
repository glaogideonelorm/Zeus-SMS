package com.example.smshook.realtime

import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smshook.R
import com.example.smshook.ZeusUssdActivity
import com.example.smshook.config.ServerConfig
import com.yourpackage.simpleussd.ussd.UssdController
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

class RealtimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WebSocket? = null
    private var backoffMs = 1000L
    @Volatile private var running = false
    private val gson = Gson()
    
    companion object {
        private const val TAG = "RealtimeService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "zeus_realtime"
    }

    override fun onCreate() {
        super.onCreate()
        createNotifChannel()
        Log.d(TAG, "RealtimeService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotif("Connecting to Zeus Cloud..."))
        if (!running) {
            running = true
            scope.launch { connectLoop() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "RealtimeService destroyed")
        running = false
        ws?.close(1000, "service stopping")
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun connectLoop() {
        while (running) {
            try {
                Log.d(TAG, "Attempting to connect to Zeus Cloud...")
                val token = fetchToken()
                // Get WebSocket URL from configuration
                val baseWsUrl = ServerConfig.getWebSocketUrl(applicationContext)
                val url = "$baseWsUrl?token=$token"
                
                val client = OkHttpClient.Builder()
                    .pingInterval(25, TimeUnit.SECONDS)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)
                    .build()

                val req = Request.Builder().url(url).build()
                val listener = WsListener(
                    onOpen = { 
                        backoffMs = 1000L
                        updateNotif("Connected to Zeus Cloud")
                        Log.d(TAG, "WebSocket connected")
                    },
                    onMessage = { text -> handleServerMessage(text) },
                    onClosed = { code, reason -> 
                        updateNotif("Disconnected from Zeus Cloud")
                        Log.d(TAG, "WebSocket closed: $code - $reason")
                    },
                    onFailure = { t -> 
                        updateNotif("Retrying connection...")
                        Log.e(TAG, "WebSocket failure: ${t.message}")
                    }
                )

                ws = client.newWebSocket(req, listener)

                // Keep alive and periodic ping
                var i = 0
                while (running && ws != null) {
                    delay(5000)
                    if (++i % 12 == 0) {
                        ws?.send("""{"type":"ping","ts":${System.currentTimeMillis()}}""")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connect loop error: ${e.message}")
            } finally {
                // Exponential backoff with jitter
                val jitter = Random.nextLong(0, 500)
                delay(backoffMs + jitter)
                backoffMs = min(backoffMs * 2, 120_000L)
            }
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            Log.d(TAG, "Received message: $text")
            val message = gson.fromJson(text, ServerMessage::class.java)
            
            when (message.type) {
                "data" -> {
                    // Process USSD command from Zeus Cloud
                    message.id?.let { id ->
                        processUssdCommand(message.body)
                        // Send ACK
                        ws?.send("""{"type":"ack","id":"$id"}""")
                    }
                }
                "hello" -> {
                    Log.d(TAG, "Received hello from server")
                }
                else -> {
                    Log.d(TAG, "Unknown message type: ${message.type}")
                }
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse server message: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling server message: ${e.message}")
        }
    }

    private fun processUssdCommand(body: Any?) {
        try {
            val commandData = gson.fromJson(gson.toJsonTree(body), UssdCommand::class.java)
            Log.d(TAG, "Processing USSD command: $commandData")
            
            // Execute USSD command via Zeus USSD
            val intent = Intent(this, ZeusUssdActivity::class.java).apply {
                putExtra("auto_execute", true)
                putExtra("ussd_code", commandData.code)
                putExtra("options", commandData.options?.joinToString(",") ?: "")
                putExtra("sim_slot", commandData.simSlot ?: 0)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing USSD command: ${e.message}")
        }
    }

    private suspend fun fetchToken(): String {
        return try {
            // For development/testing - use dummy token
            // TODO: Replace with ZeusTokenProvider.getToken(applicationContext) when Zeus Cloud is ready
            ZeusTokenProvider.getDummyToken(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch token: ${e.message}")
            throw e
        }
    }

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zeus Realtime",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zeus Cloud real-time connection"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Zeus Cloud")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotif(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotif(text))
    }

    private class WsListener(
        val onOpen: (Response) -> Unit,
        val onMessage: (String) -> Unit,
        val onClosed: (Int, String) -> Unit,
        val onFailure: (Throwable) -> Unit
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = onOpen(response)
        override fun onMessage(webSocket: WebSocket, text: String) = onMessage(text)
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onMessage(bytes.utf8())
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { 
            webSocket.close(1000, null) 
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onClosed(code, reason)
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onFailure(t)
    }

    // Data classes for message parsing
    data class ServerMessage(
        val id: String? = null,
        val type: String,
        val ts: Long? = null,
        val body: Any? = null
    )

    data class UssdCommand(
        val code: String,
        val options: List<String>? = null,
        val simSlot: Int? = 0
    )
}
