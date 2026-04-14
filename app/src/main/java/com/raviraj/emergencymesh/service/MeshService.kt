package com.raviraj.emergencymesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.raviraj.emergencymesh.EmergencyMessage
import com.raviraj.emergencymesh.EmergencyType
import com.raviraj.emergencymesh.R
import com.raviraj.emergencymesh.ble.BleAdvertiser
import com.raviraj.emergencymesh.ble.BleScanner
import com.raviraj.emergencymesh.ble.MeshConfig
import com.raviraj.emergencymesh.routing.MeshRoutingEngine

class MeshService : Service() {

    private val TAG = "MeshService"

    private lateinit var routingEngine: MeshRoutingEngine
    private lateinit var bleScanner: BleScanner
    private lateinit var bleAdvertiser: BleAdvertiser
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val handler = Handler(Looper.getMainLooper())
    
    // 🔥 Periodic UI update & Heartbeat
    private val updateRunnable = object : Runnable {
        override fun run() {
            // 1. Send heartbeat so others can see us
            sendHeartbeat()
            
            // 2. Update UI with current neighbor count
            broadcastStatusUpdate()
            
            handler.postDelayed(this, 10000) // Heartbeat every 10 seconds
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "🟢 Service Created")

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EmergencyMesh::WakeLock"
        )
        wakeLock?.acquire()

        routingEngine = MeshRoutingEngine(
            context = this,
            onBroadcast = { message ->
                broadcastEmergency(message)
            },
            onNewEmergency = { message ->
                showEmergencyNotification(message)
                broadcastEmergencyToUI(message)
            }
        )

        bleScanner = BleScanner(
            context = this,
            onEmergencyReceived = { message ->
                routingEngine.handleIncomingMessage(message)
            },
            onLocationReceived = { senderId, lat, lng ->
                routingEngine.handleLocationUpdate(senderId, lat, lng)
            }
        )
        bleAdvertiser = BleAdvertiser(this)

        createNotificationChannel()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    MeshConfig.NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(MeshConfig.NOTIFICATION_ID, createNotification())
            }
            Log.d(TAG, "✅ Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground: ${e.message}")
        }

        bleScanner.startScanning()
        handler.post(updateRunnable)
        Log.d(TAG, "🔍 BLE scanning started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.getStringExtra("EMERGENCY_TYPE")?.let { typeString ->
            val payload =
                intent.getStringExtra("EMERGENCY_PAYLOAD") ?: "Emergency"

            Log.d(TAG, "🆘 Local emergency: $typeString")

            val emergencyType = EmergencyType.valueOf(typeString)
            routingEngine.triggerEmergency(emergencyType, payload)
        }

        return START_STICKY
    }

    private fun sendHeartbeat() {
        val heartbeat = routingEngine.createHeartbeat()
        bleAdvertiser.startAdvertising(heartbeat)
    }

    private fun broadcastEmergency(message: EmergencyMessage) {
        Log.d(TAG, "📡 Broadcasting: ${message.type}, TTL=${message.ttl}")
        bleAdvertiser.startAdvertising(message)
    }

    private fun broadcastStatusUpdate() {
        val intent = Intent(MeshConfig.ACTION_MESH_UPDATE)
        intent.putExtra(MeshConfig.EXTRA_DEVICE_COUNT, routingEngine.getNearbyDeviceCount())
        sendBroadcast(intent)
    }

    private fun broadcastEmergencyToUI(message: EmergencyMessage) {
        val intent = Intent(MeshConfig.ACTION_MESH_UPDATE)
        intent.putExtra(MeshConfig.EXTRA_EMERGENCY_TYPE, message.type.name)
        intent.putExtra(MeshConfig.EXTRA_EMERGENCY_MESSAGE, message.message)
        intent.putExtra(MeshConfig.EXTRA_SENDER_ID, message.senderId)
        intent.putExtra(MeshConfig.EXTRA_USER_NAME, message.userName)
        intent.putExtra(MeshConfig.EXTRA_DEVICE_COUNT, routingEngine.getNearbyDeviceCount())
        sendBroadcast(intent)
    }

    private fun showEmergencyNotification(message: EmergencyMessage) {

        val notificationManager =
            getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val emergencyChannel = NotificationChannel(
                "emergency_alerts",
                "🚨 Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(emergencyChannel)
        }

        val mapIntent = if (message.latitude != null && message.longitude != null) {
            val gmmIntentUri = Uri.parse("geo:${message.latitude},${message.longitude}?q=${message.latitude},${message.longitude}(${message.type})")
            val intent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            intent.setPackage("com.google.android.apps.maps")
            PendingIntent.getActivity(
                this,
                message.senderId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        val senderDisplay = message.userName ?: message.senderId
        val notificationText = buildString {
            append(message.message)
            append("\n\nFrom: $senderDisplay")
            append("\nHops left: ${message.ttl}")
            if (message.latitude != null && message.longitude != null) {
                append("\n${message.formatLocation()}")
            }
        }

        val notification = NotificationCompat.Builder(this, "emergency_alerts")
            .setContentTitle("🚨 ${message.type}")
            .setContentText(message.message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(notificationText)
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .apply {
                if (mapIntent != null) {
                    addAction(
                        android.R.drawable.ic_menu_mapmode,
                        "📍 View on Map",
                        mapIntent
                    )
                }
            }
            .build()

        notificationManager.notify(
            message.senderId.hashCode() + message.type.hashCode(),
            notification
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        bleScanner.stopScanning()
        bleAdvertiser.stopAdvertising()

        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        Log.d(TAG, "🛑 Mesh service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MeshConfig.NOTIFICATION_CHANNEL_ID,
                "Emergency Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(
            this,
            MeshConfig.NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle("Emergency Mesh Active")
            .setContentText("Scanning & broadcasting emergency messages...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
