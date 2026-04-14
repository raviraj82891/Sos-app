package com.raviraj.emergencymesh.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.raviraj.emergencymesh.EmergencyMessage

class BleAdvertiser(private val context: Context) {

    private val tag = "BleAdvertiser"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private var currentData: ByteArray? = null

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    fun startAdvertising(message: EmergencyMessage) {
        val compactData = message.toBinaryPayload()
        
        if (currentData?.contentEquals(compactData) == true) {
            return
        }

        broadcastData(compactData)
    }

    private fun broadcastData(dataBytes: ByteArray) {
        currentData = dataBytes

        Log.d(tag, "📝 Broadcasting: ${dataBytes.size} bytes")

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setTimeout(0)
            .setConnectable(false)
            .build()

        // 🔥 Switch to Manufacturer Data for maximum efficiency and compatibility.
        // This only uses 4 bytes of overhead (Length, Type, 2-byte ID).
        // Flags(3) + ManufacturerData(4 + 20) = 27 bytes total. Fits perfectly in 31-byte limit!
        val advertisementData = AdvertiseData.Builder()
            .addManufacturerData(MeshConfig.MANUFACTURER_ID, dataBytes)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.e(tag, "❌ Missing BLUETOOTH_ADVERTISE permission")
                    return
                }
            }

            stopCurrentLeAdvertising()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(tag, "📢 Advertising started")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(tag, "❌ Advertising failed: $errorCode")
                    currentData = null
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

    private fun stopCurrentLeAdvertising() {
        advertiseCallback?.let {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                        == PackageManager.PERMISSION_GRANTED) {
                        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(it)
                    }
                } else {
                    bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(it)
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Error stopping: ${e.message}")
            }
            advertiseCallback = null
        }
    }

    fun stopAdvertising() {
        handler.removeCallbacksAndMessages(null)
        stopCurrentLeAdvertising()
        currentData = null
        Log.d(tag, "🛑 Advertising stopped")
    }
}
