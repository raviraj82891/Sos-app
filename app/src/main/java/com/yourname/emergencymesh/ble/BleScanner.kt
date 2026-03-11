package com.yourname.emergencymesh.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.yourname.emergencymesh.EmergencyMessage
import com.yourname.emergencymesh.EmergencyType

class BleScanner(
    private val context: Context,
    private val onEmergencyReceived: (EmergencyMessage) -> Unit,
    private val onLocationReceived: (String, Double, Double) -> Unit  // 🔥 NEW callback
) {

    private val tag = "BleScanner"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanCallback: ScanCallback? = null

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    fun startScanning() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MeshConfig.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val serviceData = result.scanRecord?.getServiceData(ParcelUuid(MeshConfig.SERVICE_UUID))

                serviceData?.let { data ->
                    val compactString = String(data)
                    Log.d(tag, "📡 Received: $compactString (${data.size} bytes)")

                    try {
                        // 🔥 Check if this is a location update (format: "senderId|L|lat,lng")
                        if (compactString.contains("|L|")) {
                            parseLocationUpdate(compactString)
                        } else {
                            // Regular emergency message
                            val message = EmergencyMessage.fromCompactString(compactString)
                            onEmergencyReceived(message)
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "❌ Parse error: ${e.message}")
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(tag, "❌ Scan failed: $errorCode")
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.e(tag, "❌ Missing BLUETOOTH_SCAN permission")
                    return
                }
            }

            bluetoothAdapter?.bluetoothLeScanner?.startScan(
                listOf(filter),
                settings,
                scanCallback
            )

            Log.d(tag, "🔍 Scanning started")

        } catch (e: SecurityException) {
            Log.e(tag, "❌ SecurityException: ${e.message}")
        }
    }

    // 🔥 NEW: Parse location updates
    private fun parseLocationUpdate(compact: String) {
        val parts = compact.split("|")
        if (parts.size == 3 && parts[1] == "L") {
            val senderId = parts[0]
            val coords = parts[2].split(",")
            if (coords.size == 2) {
                val lat = coords[0].toDouble()
                val lng = coords[1].toDouble()
                Log.d(tag, "📍 Location update: $senderId at $lat, $lng")
                onLocationReceived(senderId, lat, lng)
            }
        }
    }

    fun stopScanning() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            scanCallback?.let {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            }
            scanCallback = null
            Log.d(tag, "🛑 Scanning stopped")

        } catch (e: SecurityException) {
            Log.e(tag, "❌ Stop scan error: ${e.message}")
        }
    }
}