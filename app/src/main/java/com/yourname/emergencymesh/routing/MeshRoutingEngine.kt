package com.yourname.emergencymesh.routing

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.yourname.emergencymesh.ble.MeshConfig
import com.yourname.emergencymesh.EmergencyMessage
import com.yourname.emergencymesh.EmergencyType
import com.yourname.emergencymesh.location.LocationProvider

class MeshRoutingEngine(
    private val context: Context,
    private val onBroadcast: (EmergencyMessage) -> Unit,
    private val onNewEmergency: (EmergencyMessage) -> Unit
) {
    private val tag = "MeshRoutingEngine"
    private val seenMessages = mutableSetOf<String>()
    private val locationProvider = LocationProvider(context)

    // 🔥 Store GPS locations
    private val emergencyLocations = mutableMapOf<String, Pair<Double, Double>>()

    fun handleIncomingMessage(message: EmergencyMessage) {
        val messageKey = "${message.senderId}_${message.type}"

        if (seenMessages.contains(messageKey)) {
            Log.d(tag, "♻️ Duplicate ignored: $messageKey")
            return
        }

        seenMessages.add(messageKey)

        // 🔥 Attach location if we have it
        val location = emergencyLocations[message.senderId]
        val messageWithLocation = if (location != null) {
            message.copy(latitude = location.first, longitude = location.second)
        } else {
            message
        }

        Log.d(tag, """
            🚨 EMERGENCY RECEIVED 🚨
            Type: ${message.type}
            Sender: ${message.senderId}
            TTL: ${message.ttl}
            Location: ${messageWithLocation.formatLocation()}
        """.trimIndent())

        onNewEmergency(messageWithLocation)

        if (message.ttl <= 1) {
            Log.d(tag, "⛔ TTL too low (${message.ttl}), stop forwarding")
            return
        }

        val forwarded = message.decrementTTL()
        Log.d(tag, "➡️ Forwarding, TTL now ${forwarded.ttl}")
        onBroadcast(forwarded)
    }

    // 🔥 NEW: Handle incoming location updates
    fun handleLocationUpdate(senderId: String, lat: Double, lng: Double) {
        emergencyLocations[senderId] = Pair(lat, lng)
        Log.d(tag, "📍 Stored location for $senderId: $lat, $lng")
    }

    fun triggerEmergency(type: EmergencyType, payload: String): EmergencyMessage {

        val location = locationProvider.getCurrentLocation()

        val message = EmergencyMessage(
            senderId = getDeviceId(),
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

        seenMessages.add("${message.senderId}_${message.type}")
        onNewEmergency(message)

        return message
    }

    private fun getDeviceId(): String {
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