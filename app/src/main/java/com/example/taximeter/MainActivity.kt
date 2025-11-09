package com.example.taximeter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private val TAG = "MainActivity"

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Request ALL permissions when app starts
        requestAllPermissions()

        // Set default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        // Setup bottom navigation listener
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_compteur -> {
                    loadFragment(ComptourFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }

        Log.d(TAG, "MainActivity created")
    }

    /**
     * Load a fragment into the container
     */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        Log.d(TAG, "Loaded fragment: ${fragment.javaClass.simpleName}")
    }

    /**
     * Request all necessary permissions at once
     */
    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check location permissions
        if (!hasLocationPermission()) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request all missing permissions
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $permissionsToRequest")
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            Log.d(TAG, "All permissions already granted")
        }
    }

    /**
     * Check if location permission is granted
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older Android versions
        }
    }

    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty()) {
                    // Check which permissions were granted
                    val locationGranted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }

                    if (locationGranted) {
                        Log.d(TAG, "✅ Location permission granted")
                        Toast.makeText(
                            this,
                            "✅ Location permission granted!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Notify all fragments that permission was granted
                        refreshAllFragments()
                    } else {
                        Log.w(TAG, "❌ Location permission denied")
                        Toast.makeText(
                            this,
                            "⚠️ Location permission is required for the taxi meter to work",
                            Toast.LENGTH_LONG
                        ).show()

                        // Check if user selected "Don't ask again"
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )) {
                            // Show dialog to open settings
                            showPermissionSettingsDialog()
                        }
                    }
                }
            }
        }
    }

    /**
     * Refresh all active fragments to apply permission changes
     */
    private fun refreshAllFragments() {
        Log.d(TAG, "Refreshing all fragments")

        supportFragmentManager.fragments.forEach { fragment ->
            when (fragment) {
                is HomeFragment -> {
                    fragment.onPermissionGranted()
                    Log.d(TAG, "Notified HomeFragment of permission grant")
                }
                is ComptourFragment -> {
                    fragment.onPermissionGranted()
                    Log.d(TAG, "Notified ComptourFragment of permission grant")
                }
            }
        }
    }

    /**
     * Show dialog to guide user to app settings
     */
    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage(
                "This app needs location access to:\n\n" +
                        "• Show your location on the map\n" +
                        "• Track distance traveled\n" +
                        "• Calculate accurate taxi fares\n\n" +
                        "Please enable location permission in app settings."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                // Open app settings
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                )
                intent.data = android.net.Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "Limited functionality without location permission",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Check permissions again when app resumes (in case user granted from settings)
     */
    override fun onResume() {
        super.onResume()

        Log.d(TAG, "MainActivity resumed")

        // If permission was just granted from settings, refresh fragments
        if (hasLocationPermission()) {
            refreshAllFragments()
        }
    }
}