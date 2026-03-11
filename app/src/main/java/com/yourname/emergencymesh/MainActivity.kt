package com.yourname.emergencymesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yourname.emergencymesh.service.MeshService
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private lateinit var tvServiceStatus: TextView
    private lateinit var tvDeviceCount: TextView
    private lateinit var tvLastEmergency: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvDeviceCount = findViewById(R.id.tvDeviceCount)
        tvLastEmergency = findViewById(R.id.tvLastEmergency)

        requestPermissionsIfNeeded()

        findViewById<Button>(R.id.btnStartMesh).setOnClickListener {
            ensureBluetoothEnabled()
            startMeshService()
        }

        findViewById<Button>(R.id.btnStopMesh).setOnClickListener {
            stopMeshService()
        }

        findViewById<Button>(R.id.btnTriggerFire).setOnClickListener {
            triggerEmergency("FIRE", "🔥 Fire detected at Building A, Room 301!")
        }

        findViewById<Button>(R.id.btnTriggerMedical).setOnClickListener {
            triggerEmergency("MEDICAL", "🚑 Medical emergency - person collapsed!")
        }

        findViewById<Button>(R.id.btnTriggerEvacuation).setOnClickListener {
            triggerEmergency("EVACUATION", "⚠️ Immediate evacuation required!")
        }
    }

    private fun requestPermissionsIfNeeded() {

        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 🔥 NEW: Request FOREGROUND_SERVICE_LOCATION permission (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                notGranted.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            Toast.makeText(this, "✅ Permissions granted", Toast.LENGTH_SHORT).show()
            ensureBluetoothEnabled()
        }
    }

    private fun ensureBluetoothEnabled() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionsIfNeeded()
                return
            }
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }

        if (!adapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        }
    }

    private fun startMeshService() {
        val intent = Intent(this, MeshService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        tvServiceStatus.text = "● Service: Active ✅"
        tvServiceStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        Toast.makeText(this, "🔍 Mesh Service Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopMeshService() {
        val intent = Intent(this, MeshService::class.java)
        stopService(intent)

        tvServiceStatus.text = "● Service: Inactive"
        tvServiceStatus.setTextColor(getColor(android.R.color.darker_gray))
        tvDeviceCount.text = "● Nearby devices: 0"
        Toast.makeText(this, "🛑 Mesh Service Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun triggerEmergency(type: String, payload: String) {
        val intent = Intent(this, MeshService::class.java).apply {
            putExtra("EMERGENCY_TYPE", type)
            putExtra("EMERGENCY_PAYLOAD", payload)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time = timeFormat.format(Date())
        tvLastEmergency.text = "[$time] $type\n$payload"

        Toast.makeText(this, "🚨 $type emergency broadcast!", Toast.LENGTH_SHORT).show()
    }
}
