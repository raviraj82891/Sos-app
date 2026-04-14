package com.raviraj.emergencymesh.ble

import java.util.UUID

object MeshConfig {

    /**
     * BLE Service UUID for emergency mesh network
     */
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

    /**
     * Manufacturer ID for the mesh network.
     * 0xFFFF is used for internal/testing. 
     * In a production app, this should be a registered Bluetooth SIG company ID.
     */
    const val MANUFACTURER_ID = 0xFFFF

    /**
     * Maximum Time-To-Live (TTL) for emergency messages
     */
    const val MAX_TTL = 5

    /**
     * Foreground service notification ID
     */
    const val NOTIFICATION_ID = 1001

    /**
     * Notification channel ID
     */
    const val NOTIFICATION_CHANNEL_ID = "emergency_mesh_channel"

    /**
     * Broadcast Action for Mesh Updates
     */
    const val ACTION_MESH_UPDATE = "com.raviraj.emergencymesh.ACTION_MESH_UPDATE"
    const val EXTRA_DEVICE_COUNT = "extra_device_count"
    const val EXTRA_EMERGENCY_TYPE = "extra_emergency_type"
    const val EXTRA_EMERGENCY_MESSAGE = "extra_emergency_message"
    const val EXTRA_SENDER_ID = "extra_sender_id"
    const val EXTRA_USER_NAME = "extra_user_name"
}
