package com.raviraj.emergencymesh.routing

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.raviraj.emergencymesh.ble.MeshConfig
import com.raviraj.emergencymesh.EmergencyMessage
import com.raviraj.emergencymesh.EmergencyType
import com.raviraj.emergencymesh.location.LocationProvider

class MeshRoutingEngine(
    private val context: Context,
    private val onBroadcast: (EmergencyMessage) -> Unit,
    private val onNewEmergency: (EmergencyMessage) -> Unit
) {
    private val tag = "MeshRoutingEngine"
    
    // 🔥 Use a map to track message timestamps to avoid permanent ignoring
    private val seenMessages = mutableMapOf<String, Long>()
    private val DUPLICATE_WINDOW_MS = 60_000L // Ignore duplicates within 1 minute

    private val locationProvider = LocationProvider(context)

    // 🔥 Store GPS locations
    private val emergencyLocations = mutableMapOf<String, Pair<Double, Double>>()
    
    // 🔥 Track unique nearby devices
    private val nearbyDevices = mutableMapOf<String, Long>()
    private val DEVICE_TIMEOUT_MS = 30_000L // Device is "nearby" if seen in last 30s

    fun handleIncomingMessage(message: EmergencyMessage): Boolean {
        // Record device as nearby
        nearbyDevices[message.senderId] = System.currentTimeMillis()
        
        // If it's just a heartbeat, we stop here (already marked as nearby)
        if (message.type == EmergencyType.HEARTBEAT) {
            return true
        }

        val messageKey = "${message.senderId}_${message.type}"
        val currentTime = System.currentTimeMillis()

        val lastSeen = seenMessages[messageKey]
        if (lastSeen != null && (currentTime - lastSeen) < DUPLICATE_WINDOW_MS) {
            return false
        }

        seenMessages[messageKey] = currentTime

        // 🔥 Attach location if we have it
        val location = emergencyLocations[message.senderId]
        val messageWithLocation = if (location != null) {
            message.copy(latitude = location.first, longitude = location.second)
        } else {
            message
        }

        Log.d(tag, """
            🚨 NEW EMERGENCY RECEIVED 🚨
            Type: ${message.type}
            Sender: ${message.senderId}
            User: ${message.userName ?: "Unknown"}
            TTL: ${message.ttl}
            Location: ${messageWithLocation.formatLocation()}
        """.trimIndent())

        onNewEmergency(messageWithLocation)

        if (message.ttl <= 1) {
            Log.d(tag, "⛔ TTL too low (${message.ttl}), stop forwarding")
            return true
        }

        val forwarded = message.decrementTTL()
        Log.d(tag, "➡️ Forwarding, TTL now ${forwarded.ttl}")
        onBroadcast(forwarded)
        return true
    }

    // 🔥 NEW: Handle incoming location updates
    fun handleLocationUpdate(senderId: String, lat: Double, lng: Double) {
        nearbyDevices[senderId] = System.currentTimeMillis()
        emergencyLocations[senderId] = Pair(lat, lng)
        Log.d(tag, "📍 Stored location for $senderId: $lat, $lng")
    }

    fun getNearbyDeviceCount(): Int {
        val currentTime = System.currentTimeMillis()
        // Clean up old devices
        nearbyDevices.entries.removeIf { currentTime - it.value > DEVICE_TIMEOUT_MS }
        return nearbyDevices.size
    }

    fun createHeartbeat(): EmergencyMessage {
        return EmergencyMessage(
            senderId = getDeviceId(),
            userName = getUserName(),
            type = EmergencyType.HEARTBEAT,
            message = "Heartbeat",
            ttl = 1, // Heartbeats don't need to hop far
            timestamp = System.currentTimeMillis()
        )
    }

    fun triggerEmergency(type: EmergencyType, payload: String): EmergencyMessage {

        val location = locationProvider.getCurrentLocation()

        val message = EmergencyMessage(
            senderId = getDeviceId(),
            userName = getUserName(),
            type = type,
            message = payload,
            ttl = MeshConfig.MAX_TTL,
            timestamp = System.currentTimeMillis(),
            latitude = location?.first,
            longitude = location?.second
        )

        // 🔥 Store location locally
        if (location != null) {
            emergencyLocations[message.senderId] = Pair(location.first, location.second)
        }

        Log.d(tag, "📤 Local emergency: ${message.type}, broadcasting with TTL=${message.ttl}, location=${message.formatLocation()}")
        onBroadcast(message)

        seenMessages["${message.senderId}_${message.type}"] = System.currentTimeMillis()
        onNewEmergency(message)

        return message
    }

    private fun getUserName(): String? {
        val prefs = context.getSharedPreferences("MeshPrefs", Context.MODE_PRIVATE)
        return prefs.getString("user_name", null)
    }

    fun getDeviceId(): String {
        val fullId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return if (fullId.length >= 8) {
            fullId.substring(0, 8)
        } else {
            fullId.padEnd(8, '0')
        }
    }
}
