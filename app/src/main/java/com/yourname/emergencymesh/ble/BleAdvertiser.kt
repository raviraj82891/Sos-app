package com.yourname.emergencymesh.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.yourname.emergencymesh.EmergencyMessage

class BleAdvertiser(private val context: Context) {

    private val tag = "BleAdvertiser"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    fun startAdvertising(message: EmergencyMessage) {

        // 🔥 STEP 1: Broadcast emergency (12 bytes)
        val compactData = "${message.senderId}|${when(message.type) {
            com.yourname.emergencymesh.EmergencyType.FIRE -> "F"
            com.yourname.emergencymesh.EmergencyType.MEDICAL -> "M"
            com.yourname.emergencymesh.EmergencyType.EVACUATION -> "E"
        }}|${message.ttl}"

        broadcastData(compactData)

        // 🔥 STEP 2: Broadcast GPS location 500ms later (if available)
        if (message.latitude != null && message.longitude != null) {
            handler.postDelayed({
                val locationData = "${message.senderId}|L|${String.format("%.2f", message.latitude)},${String.format("%.2f", message.longitude)}"
                broadcastData(locationData)
            }, 500)
        }
    }

    private fun broadcastData(data: String) {
        val dataBytes = data.toByteArray()

        Log.d(tag, "📝 Broadcasting: $data (${dataBytes.size} bytes)")

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setTimeout(0)
            .setConnectable(false)
            .build()

        val advertisementData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(MeshConfig.SERVICE_UUID))
            .addServiceData(ParcelUuid(MeshConfig.SERVICE_UUID), dataBytes)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.e(tag, "❌ Missing BLUETOOTH_ADVERTISE permission")
                    return
                }
            }

            stopAdvertising()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(tag, "📢 Advertising started")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(tag, "❌ Advertising failed: $errorCode")
                }
            }

            bluetoothAdapter?.bluetoothLeAdvertiser?.startAdvertising(
                settings,
                advertisementData,
                advertiseCallback
            )

        } catch (e: SecurityException) {
            Log.e(tag, "❌ SecurityException: ${e.message}")
        }
    }

    fun stopAdvertising() {
        try {
            handler.removeCallbacksAndMessages(null)
            advertiseCallback?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                }
                bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(it)
                advertiseCallback = null
            }
        } catch (e: SecurityException) {
            Log.e(tag, "❌ Stop advertising error: ${e.message}")
        }
    }
}