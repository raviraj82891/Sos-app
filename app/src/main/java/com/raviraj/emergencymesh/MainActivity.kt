package com.raviraj.emergencymesh

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.*
import android.text.InputFilter
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.raviraj.emergencymesh.ble.MeshConfig
import com.raviraj.emergencymesh.service.MeshService
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "MeshPrefs"
        private const val KEY_USER_NAME = "user_name"
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                Toast.makeText(this, "✅ Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                showPermissionRationaleDialog()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private lateinit var tvServiceStatus: TextView
    private lateinit var tvDeviceCount: TextView
    private lateinit var tvLastEmergency: TextView
    private lateinit var statusIndicator: View
    private lateinit var radarPulse: CircularProgressIndicator
    private lateinit var btnMeshToggle: MaterialButton
    private lateinit var fabSOS: FloatingActionButton
    private lateinit var meshRadarView: MeshRadarView

    private var isMeshActive = false

    private val meshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MeshConfig.ACTION_MESH_UPDATE) {
                val deviceCount = intent.getIntExtra(MeshConfig.EXTRA_DEVICE_COUNT, 0)
                tvDeviceCount.text = "$deviceCount"

                val senderId = intent.getStringExtra(MeshConfig.EXTRA_SENDER_ID)
                if (senderId != null) {
                    meshRadarView.addDiscovery(senderId)
                }

                val type = intent.getStringExtra(MeshConfig.EXTRA_EMERGENCY_TYPE)
                val msg = intent.getStringExtra(MeshConfig.EXTRA_EMERGENCY_MESSAGE)
                
                if (type != null && msg != null) {
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val time = timeFormat.format(Date())
                    tvLastEmergency.text = "[$time] $type: $msg"
                    
                    tvLastEmergency.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvDeviceCount = findViewById(R.id.tvDeviceCount)
        tvLastEmergency = findViewById(R.id.tvLastEmergency)
        statusIndicator = findViewById(R.id.statusIndicator)
        radarPulse = findViewById(R.id.radarPulse)
        btnMeshToggle = findViewById(R.id.btnMeshToggle)
        fabSOS = findViewById(R.id.fabSOS)
        meshRadarView = findViewById(R.id.meshRadarView)

        checkFirstRun()
        setupClickListeners()
        setupAnimations()
        
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions()
        }
    }

    private fun checkFirstRun() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_USER_NAME)) {
            showNameInputDialog()
        }
    }

    private fun showNameInputDialog() {
        val input = EditText(this).apply {
            hint = "Enter your name (max 10 chars)"
            filters = arrayOf(InputFilter.LengthFilter(10))
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
            val params = input.layoutParams as LinearLayout.LayoutParams
            params.setMargins(40, 20, 40, 20)
            input.layoutParams = params
        }

        AlertDialog.Builder(this)
            .setTitle("Welcome to Emergency Mesh")
            .setMessage("Please enter a display name for the mesh network. This will be visible to nearby responders.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_USER_NAME, name)
                        .apply()
                    Toast.makeText(this, "Welcome, $name!", Toast.LENGTH_SHORT).show()
                } else {
                    showNameInputDialog()
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun setupClickListeners() {
        btnMeshToggle.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isMeshActive) {
                stopMeshService()
            } else {
                if (hasRequiredPermissions()) {
                    ensureBluetoothEnabled()
                    startMeshService()
                } else {
                    requestRequiredPermissions()
                }
            }
        }

        findViewById<View>(R.id.btnTriggerFire).apply {
            setOnClickListener { 
                applyScaleAnimation(this)
                if (hasRequiredPermissions()) {
                    triggerEmergency("FIRE", "🔥 Fire alert broadcasted!") 
                } else {
                    requestRequiredPermissions()
                }
            }
        }

        findViewById<View>(R.id.btnTriggerMedical).apply {
            setOnClickListener { 
                applyScaleAnimation(this)
                if (hasRequiredPermissions()) {
                    triggerEmergency("MEDICAL", "🚑 Medical alert broadcasted!") 
                } else {
                    requestRequiredPermissions()
                }
            }
        }

        findViewById<View>(R.id.btnTriggerEvacuation).apply {
            setOnClickListener { 
                applyScaleAnimation(this)
                if (hasRequiredPermissions()) {
                    triggerEmergency("EVACUATION", "⚠️ Evacuation alert broadcasted!") 
                } else {
                    requestRequiredPermissions()
                }
            }
        }

        fabSOS.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (hasRequiredPermissions()) {
                triggerEmergency("SOS", "🆘 CRITICAL SOS SIGNAL!")
                Toast.makeText(this, "🆘 EMERGENCY BROADCAST SENT!", Toast.LENGTH_LONG).show()
            } else {
                requestRequiredPermissions()
            }
            true
        }
    }

    private fun setupAnimations() {
        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            fabSOS,
            PropertyValuesHolder.ofFloat("scaleX", 1.1f),
            PropertyValuesHolder.ofFloat("scaleY", 1.1f)
        ).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulse.start()
    }

    private fun applyScaleAnimation(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
    }

    private fun startMeshService() {
        val intent = Intent(this, MeshService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        updateUiState(true)
        Toast.makeText(this, "🚀 Mesh Activated", Toast.LENGTH_SHORT).show()
    }

    private fun stopMeshService() {
        val intent = Intent(this, MeshService::class.java)
        stopService(intent)

        updateUiState(false)
        Toast.makeText(this, "🛑 Mesh Deactivated", Toast.LENGTH_SHORT).show()
    }

    private fun updateUiState(active: Boolean) {
        isMeshActive = active
        if (active) {
            btnMeshToggle.text = "DEACTIVATE MESH"
            btnMeshToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_red))
            tvServiceStatus.text = "SYSTEM ACTIVE"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
            statusIndicator.setBackgroundResource(R.drawable.status_dot_active)
            radarPulse.visibility = View.VISIBLE
            meshRadarView.visibility = View.VISIBLE
        } else {
            btnMeshToggle.text = "ACTIVATE MESH"
            btnMeshToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_blue))
            tvServiceStatus.text = "SYSTEM READY"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            statusIndicator.setBackgroundResource(R.drawable.status_dot_inactive)
            radarPulse.visibility = View.INVISIBLE
            meshRadarView.visibility = View.INVISIBLE
            tvDeviceCount.text = "0"
        }
    }

    private fun triggerEmergency(type: String, payload: String) {
        if (!isMeshActive) {
            Toast.makeText(this, "Please activate Mesh first", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, MeshService::class.java).apply {
            putExtra("EMERGENCY_TYPE", type)
            putExtra("EMERGENCY_PAYLOAD", payload)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }
        return permissions.toTypedArray()
    }

    private fun requestRequiredPermissions() {
        requestPermissionLauncher.launch(getRequiredPermissions())
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Emergency Mesh requires Location and Bluetooth permissions to scan for nearby devices and broadcast alerts during emergencies. Without these, the app cannot function.")
            .setPositiveButton("Grant") { _, _ ->
                requestRequiredPermissions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun ensureBluetoothEnabled() {
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

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(MeshConfig.ACTION_MESH_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(meshReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(meshReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(meshReceiver)
    }
}
