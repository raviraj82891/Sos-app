package com.yourname.emergencymesh

import java.util.UUID

data class EmergencyMessage(
    val senderId: String,
    val type: EmergencyType,
    val message: String,
    val ttl: Int,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null
) {

    companion object {

        fun fromCompactString(compact: String): EmergencyMessage {

            val parts = compact.split("|")

            if (parts.size < 3) {
                throw IllegalArgumentException("Invalid compact format: $compact")
            }

            val senderId = parts[0]
            val typeChar = parts[1]
            val ttl = parts[2].toInt()

            var latitude: Double? = null
            var longitude: Double? = null

            if (parts.size >= 4) {
                try {
                    val coords = parts[3].split(",")
                    if (coords.size == 2) {
                        latitude = coords[0].toDouble()
                        longitude = coords[1].toDouble()
                    }
                } catch (e: Exception) {
                    // Invalid coordinates, ignore
                }
            }

            val type = when (typeChar) {
                "F" -> EmergencyType.FIRE
                "M" -> EmergencyType.MEDICAL
                "E" -> EmergencyType.EVACUATION
                else -> throw IllegalArgumentException("Unknown type: $typeChar")
            }

            val message = when (type) {
                EmergencyType.FIRE -> "🔥 Fire emergency detected"
                EmergencyType.MEDICAL -> "🚑 Medical emergency"
                EmergencyType.EVACUATION -> "⚠️ Evacuation required"
            }

            return EmergencyMessage(
                senderId = senderId,
                type = type,
                message = message,
                ttl = ttl,
                timestamp = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude
            )
        }

        fun generateDeviceId(): String {
            return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
        }
    }

    fun toCompactString(): String {
        val typeChar = when (type) {
            EmergencyType.FIRE -> "F"
            EmergencyType.MEDICAL -> "M"
            EmergencyType.EVACUATION -> "E"
        }

        // 🔥 BLE format: NO GPS (12 bytes total)
        return "$senderId|$typeChar|$ttl"
    }

    fun decrementTTL(): EmergencyMessage {
        return copy(ttl = ttl - 1)
    }

    fun distanceTo(lat: Double, lng: Double): Float {
        if (latitude == null || longitude == null) return Float.MAX_VALUE

        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            lat, lng,
            latitude, longitude,
            results
        )
        return results[0]
    }

    fun formatLocation(): String {
        return if (latitude != null && longitude != null) {
            // 🔥 FIX: Display 2 decimal places
            "📍 ${String.format("%.2f", latitude)}, ${String.format("%.2f", longitude)}"
        } else {
            "📍 Location unknown"
        }
    }

    fun getMapUrl(): String {
        return if (latitude != null && longitude != null) {
            "https://www.google.com/maps?q=$latitude,$longitude"
        } else {
            ""
        }
    }
}

enum class EmergencyType {
    FIRE,
    MEDICAL,
    EVACUATION
}