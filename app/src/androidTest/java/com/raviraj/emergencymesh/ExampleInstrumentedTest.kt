package com.raviraj.emergencymesh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.raviraj.emergencymesh.routing.MeshRoutingEngine
import com.raviraj.emergencymesh.service.MeshService
import com.raviraj.emergencymesh.R
import com.raviraj.emergencymesh.EmergencyType

/**
 * Note: This file contained a duplicate MainActivity class which caused build errors.
 * It has been renamed to MainActivityTestActivity and updated to match the project's logic.
 */
class MainActivityTestActivity : AppCompatActivity() {

    private lateinit var routingEngine: MeshRoutingEngine
    private val permissionRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Fixed: Added missing constructor parameters for MeshRoutingEngine
        routingEngine = MeshRoutingEngine(
            context = this,
            onBroadcast = { /* No-op for test activity */ },
            onNewEmergency = { /* No-op for test activity */ }
        )

        requestPermissions()

        findViewById<View>(R.id.btnStartMesh).setOnClickListener {
            startMeshService()
        }

        // Fixed: btnTriggerEmergency doesn't exist in layout, using btnTriggerFire
        findViewById<View>(R.id.btnTriggerFire).setOnClickListener {
            triggerEmergency()
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), permissionRequestCode)
        }
    }

    private fun startMeshService() {
        val intent = Intent(this, MeshService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Mesh Service Started", Toast.LENGTH_SHORT).show()
    }

    private fun triggerEmergency() {
        // Fixed: Changed String "FIRE" to EmergencyType.FIRE enum
        routingEngine.triggerEmergency(
            type = EmergencyType.FIRE,
            payload = "Fire detected at Building A"
        )
        Toast.makeText(this, "Emergency Triggered!", Toast.LENGTH_SHORT).show()
    }
}
