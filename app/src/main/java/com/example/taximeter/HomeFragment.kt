package com.example.taximeter

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class HomeFragment : Fragment(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var searchInput: EditText
    private lateinit var btnSavedPlaces: Button
    private lateinit var btnRecentTrips: Button

    private val TAG = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        searchInput = view.findViewById(R.id.searchInput)
        btnSavedPlaces = view.findViewById(R.id.btnSavedPlaces)
        btnRecentTrips = view.findViewById(R.id.btnRecentTrips)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Setup map
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, it)
                    .commit()
            }
        mapFragment.getMapAsync(this)

        // Setup button listeners
        btnSavedPlaces.setOnClickListener {
            Toast.makeText(requireContext(), "Saved Places feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        btnRecentTrips.setOnClickListener {
            Toast.makeText(requireContext(), "Recent Trips feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Setup search
        searchInput.setOnEditorActionListener { _, _, _ ->
            val query = searchInput.text.toString()
            if (query.isNotEmpty()) {
                Toast.makeText(requireContext(), "Searching for: $query", Toast.LENGTH_SHORT).show()
                // TODO: Implement geocoding/search here
            }
            true
        }

        Log.d(TAG, "HomeFragment initialized")
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        Log.d(TAG, "Map is ready")

        // Setup map with permission check
        setupMap()
    }

    /**
     * ✅ FIXED: Now checks permission BEFORE enabling location
     */
    @SuppressLint("MissingPermission")
    private fun setupMap() {
        googleMap?.apply {
            // Basic map settings (safe to do without permission)
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isCompassEnabled = true

            // ✅ CHECK PERMISSION FIRST
            if (hasLocationPermission()) {
                try {
                    // Now it's safe to enable location features
                    isMyLocationEnabled = true
                    uiSettings.isMyLocationButtonEnabled = true
                    Log.d(TAG, "My location enabled on map")

                    // Show current location
                    showCurrentLocation()
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException: ${e.message}")
                    Toast.makeText(
                        requireContext(),
                        "Cannot access location",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // Permission not granted - setup map without location
                Log.w(TAG, "Location permission not granted")
                setupMapWithoutLocation()
            }
        }
    }

    /**
     * Check if location permission is granted
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Setup map without location features (fallback)
     */
    private fun setupMapWithoutLocation() {
        googleMap?.apply {
            // Show a default location (e.g., Morocco center)
            val defaultLocation = LatLng(33.5731, -7.5898) // Casablanca
            moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))

            Log.d(TAG, "Map setup without location features")

            Toast.makeText(
                requireContext(),
                "Grant location permission to see your location",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Show current location on map
     */
    @SuppressLint("MissingPermission")
    private fun showCurrentLocation() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot show location: permission not granted")
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    googleMap?.apply {
                        // Add marker
                        addMarker(
                            MarkerOptions()
                                .position(currentLatLng)
                                .title("You are here")
                        )

                        // Move camera
                        animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

                        Log.d(TAG, "Current location shown: ${location.latitude}, ${location.longitude}")
                    }
                } else {
                    Log.w(TAG, "Last known location is null")
                    Toast.makeText(
                        requireContext(),
                        "Cannot get current location. Make sure GPS is enabled.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Failed to get location",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    /**
     * Called from MainActivity when permission is granted
     */
    fun onPermissionGranted() {
        Log.d(TAG, "Permission granted - refreshing map")

        // Refresh map setup
        if (googleMap != null) {
            setupMap()
        }
    }

    /**
     * Refresh map when fragment resumes
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Fragment resumed")

        // If permission was granted while app was in background, refresh map
        if (hasLocationPermission() && googleMap != null) {
            setupMap()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Fragment paused")
    }
}