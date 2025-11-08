package com.example.taximeter

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions

class HomeFragment : Fragment(), OnMapReadyCallback, EasyPermissions.PermissionCallbacks {

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var searchInput: EditText
    private lateinit var btnSavedPlaces: Button
    private lateinit var btnRecentTrips: Button

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 124
    }

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
                // Implement geocoding/search here
            }
            true
        }

        // Request location permission
        requestLocationPermission()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        setupMap()
        requestLocationPermission()
    }

    @SuppressLint("MissingPermission")
    private fun setupMap() {
        googleMap?.apply {
            isMyLocationEnabled = true
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isMyLocationButtonEnabled = true
        }

        showCurrentLocation()
    }

    @AfterPermissionGranted(LOCATION_PERMISSION_REQUEST_CODE)
    private fun requestLocationPermission() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (EasyPermissions.hasPermissions(requireContext(), *perms)) {
            showCurrentLocation()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "This app needs location permission to show your location",
                LOCATION_PERMISSION_REQUEST_CODE,
                *perms
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun showCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                googleMap?.apply {
                    addMarker(MarkerOptions().position(currentLatLng).title("You are here"))
                    animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        showCurrentLocation()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(requireContext(), "Location permission is required", Toast.LENGTH_LONG).show()
    }
}