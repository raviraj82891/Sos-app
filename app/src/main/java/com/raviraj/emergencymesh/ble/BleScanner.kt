package com.raviraj.emergencymesh.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.raviraj.emergencymesh.EmergencyMessage

class BleScanner(
    private val context: Context,
    private val onEmergencyReceived: (EmergencyMessage) -> Unit,
    private val onLocationReceived: (String, Double, Double) -> Unit
) {

    private val tag = "BleScanner"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanCallback: ScanCallback? = null

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    fun startScanning() {
        // 🔥 Scan for Manufacturer Data with our specific ID
        val filter = ScanFilter.Builder()
            .setManufacturerData(MeshConfig.MANUFACTURER_ID, byteArrayOf())
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val manufacturerData = result.scanRecord?.getManufacturerSpecificData(MeshConfig.MANUFACTURER_ID)

                manufacturerData?.let { data ->
                    try {
                        val message = EmergencyMessage.fromBinaryPayload(data)
                        
                        if (message.latitude != null && message.longitude != null) {
                            onLocationReceived(message.senderId, message.latitude, message.longitude)
                        }
                        
                        onEmergencyReceived(message)
                    } catch (e: Exception) {
                        // Log.v(tag, "Parse error: ${e.message}")
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
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.e(tag, "❌ Missing BLUETOOTH_SCAN permission")
                    return
                }
            }

            bluetoothAdapter?.bluetoothLeScanner?.startScan(
                listOf(filter),
                settings,
                scanCallback
            )

            Log.d(tag, "🔍 Scanning started (Manufacturer ID: ${MeshConfig.MANUFACTURER_ID})")

        } catch (e: SecurityException) {
            Log.e(tag, "❌ SecurityException: ${e.message}")
        }
    }

    fun stopScanning() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
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