package com.raviraj.emergencymesh

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.Locale

data class EmergencyMessage(
    val senderId: String,
    val type: EmergencyType,
    val message: String,
    val ttl: Int,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val userName: String? = null
) {

    companion object {
        private const val MAX_NAME_LENGTH = 6

        /**
         * Decodes a binary payload received via BLE
         * Format: Sender(4) | Type(1) | TTL(1) | Name(6) | Lat(4) | Lng(4)
         */
        fun fromBinaryPayload(bytes: ByteArray): EmergencyMessage {
            if (bytes.size < 6) throw IllegalArgumentException("Payload too short")
            
            val buffer = ByteBuffer.wrap(bytes)
            
            // 1. Sender ID
            val idInt = buffer.int
            val senderId = String.format("%08x", idInt)
            
            // 2. Type
            val typeByte = buffer.get().toInt()
            val type = EmergencyType.values().getOrNull(typeByte) ?: EmergencyType.HEARTBEAT
            
            // 3. TTL
            val ttl = buffer.get().toInt()
            
            // 4. User Name (if available)
            var userName: String? = null
            if (buffer.remaining() >= MAX_NAME_LENGTH) {
                val nameBytes = ByteArray(MAX_NAME_LENGTH)
                buffer.get(nameBytes)
                userName = String(nameBytes, StandardCharsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                if (userName.isEmpty()) userName = null
            }

            // 5. Coordinates
            var lat: Double? = null
            var lng: Double? = null
            if (buffer.remaining() >= 8) {
                lat = buffer.float.toDouble()
                lng = buffer.float.toDouble()
            }

            val msgText = when (type) {
                EmergencyType.FIRE -> "🔥 Fire emergency detected"
                EmergencyType.MEDICAL -> "🚑 Medical emergency"
                EmergencyType.EVACUATION -> "⚠️ Evacuation required"
                EmergencyType.SOS -> "🆘 CRITICAL SOS SIGNAL!"
                EmergencyType.HEARTBEAT -> "💓 Heartbeat"
            }

            return EmergencyMessage(
                senderId = senderId,
                type = type,
                message = msgText,
                ttl = ttl,
                timestamp = System.currentTimeMillis(),
                latitude = lat,
                longitude = lng,
                userName = userName
            )
        }

        fun generateDeviceId(): String {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        }
    }

    /**
     * Encodes the message into a compact binary format to fit BLE limits (max 31 bytes)
     * Sender(4) + Type(1) + TTL(1) + Name(6) + Lat(4) + Lng(4) = 20 bytes total
     */
    fun toBinaryPayload(): ByteArray {
        val hasLocation = latitude != null && longitude != null
        val size = 6 + MAX_NAME_LENGTH + (if (hasLocation) 8 else 0)
        val buffer = ByteBuffer.allocate(size)
        
        // 1. Sender ID
        val idInt = try {
            java.lang.Long.parseLong(senderId, 16).toInt()
        } catch (e: Exception) {
            0
        }
        buffer.putInt(idInt)
        
        // 2. Type
        buffer.put(type.ordinal.toByte())
        
        // 3. TTL
        buffer.put(ttl.toByte())
        
        // 4. User Name (padded to MAX_NAME_LENGTH bytes)
        val nameBytes = ByteArray(MAX_NAME_LENGTH)
        val originalNameBytes = (userName ?: "").toByteArray(StandardCharsets.UTF_8)
        val lengthToCopy = minOf(originalNameBytes.size, MAX_NAME_LENGTH)
        System.arraycopy(originalNameBytes, 0, nameBytes, 0, lengthToCopy)
        buffer.put(nameBytes)
        
        // 5. Location
        if (hasLocation) {
            buffer.putFloat(latitude!!.toFloat())
            buffer.putFloat(longitude!!.toFloat())
        }
        
        return buffer.array()
    }

    fun decrementTTL(): EmergencyMessage = copy(ttl = ttl - 1)

    fun formatLocation(): String {
        return if (latitude != null && longitude != null) {
            "📍 ${String.format(Locale.US, "%.4f", latitude)}, ${String.format(Locale.US, "%.4f", longitude)}"
        } else {
            "📍 Location unknown"
        }
    }
}

enum class EmergencyType {
    FIRE, MEDICAL, EVACUATION, SOS, HEARTBEAT
}
