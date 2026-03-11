package com.yourname.emergencymesh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yourname.emergencymesh.routing.MeshRoutingEngine
import com.yourname.emergencymesh.service.MeshService

class MainActivity : AppCompatActivity() {

    private lateinit var routingEngine: MeshRoutingEngine
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        routingEngine = MeshRoutingEngine(this)

        requestPermissions()

        findViewById<Button>(R.id.btnStartMesh).setOnClickListener {
            startMeshService()
        }

        findViewById<Button>(R.id.btnTriggerEmergency).setOnClickListener {
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
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), PERMISSION_REQUEST_CODE)
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
        routingEngine.triggerEmergency(
            type = "FIRE",
            payload = "Fire detected at Building A"
        )
        Toast.makeText(this, "Emergency Triggered!", Toast.LENGTH_SHORT).show()
    }
}