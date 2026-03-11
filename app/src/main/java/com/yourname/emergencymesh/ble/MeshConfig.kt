package com.yourname.emergencymesh.ble

import java.util.UUID

object MeshConfig {

    /**
     * BLE Service UUID for emergency mesh network
     * This UUID identifies our emergency mesh service
     */
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

    /**
     * Maximum Time-To-Live (TTL) for emergency messages
     * TTL=5 allows message to hop through 5 devices (~250m range at 50m per hop)
     */
    const val MAX_TTL = 5

    /**
     * Foreground service notification ID
     */
    const val NOTIFICATION_ID = 1001

    /**
     * Notification channel ID for the foreground service
     */
    const val NOTIFICATION_CHANNEL_ID = "emergency_mesh_channel"
}