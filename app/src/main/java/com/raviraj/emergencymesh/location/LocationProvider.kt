package com.raviraj.emergencymesh.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat

class LocationProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocationProvider"
    }

    fun getCurrentLocation(): Pair<Double, Double>? {

        // Check permissions
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ Missing location permission")
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Try GPS first (most accurate)
        var location: Location? = null

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                Log.d(TAG, "📍 GPS location: $location")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ GPS error: ${e.message}")
        }

        // Fallback to Network location
        if (location == null) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    Log.d(TAG, "📍 Network location: $location")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Network location error: ${e.message}")
            }
        }

        // Fallback to Fused (if available)
        if (location == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                    location = locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
                    Log.d(TAG, "📍 Fused location: $location")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Fused location error: ${e.message}")
            }
        }

        return if (location != null) {
            Log.d(TAG, "✅ Location: ${location.latitude}, ${location.longitude}")
            Pair(location.latitude, location.longitude)
        } else {
            Log.e(TAG, "❌ No location available")
            null
        }
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}