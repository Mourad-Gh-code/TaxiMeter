package com.example.taximeter

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import kotlin.math.roundToInt

class ComptourFragment : Fragment(), OnMapReadyCallback, EasyPermissions.PermissionCallbacks {

    // Map
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // UI Elements
    private lateinit var fareAmount: TextView
    private lateinit var distanceText: TextView
    private lateinit var timeText: TextView
    private lateinit var btnStartTrip: Button
    private lateinit var btnEndTrip: Button

    // Trip Data
    private var isTripStarted = false
    private var startLocation: Location? = null
    private var currentLocation: Location? = null
    private var totalDistance = 0.0 // in meters
    private var tripStartTime = 0L
    private var elapsedTime = 0L // in seconds

    // Fare Configuration
    private val BASE_FARE = 2.5 // DH
    private val FARE_PER_KM = 1.5 // DH
    private val FARE_PER_MINUTE = 0.5 // DH

    // Timer
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTripStarted) {
                elapsedTime = (System.currentTimeMillis() - tripStartTime) / 1000
                updateUI()
                handler.postDelayed(this, 1000)
            }
        }
    }

    // Polyline for route
    private val routePoints = mutableListOf<LatLng>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 123
        private const val NOTIFICATION_CHANNEL_ID = "taxi_meter_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_comptour, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        fareAmount = view.findViewById(R.id.fareAmount)
        distanceText = view.findViewById(R.id.distanceText)
        timeText = view.findViewById(R.id.timeText)
        btnStartTrip = view.findViewById(R.id.btnStartTrip)
        btnEndTrip = view.findViewById(R.id.btnEndTrip)

        btnEndTrip.visibility = View.GONE

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

        // Setup buttons
        btnStartTrip.setOnClickListener {
            startTrip()
        }

        btnEndTrip.setOnClickListener {
            endTrip()
        }

        // Create notification channel
        createNotificationChannel()

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
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMyLocationButtonEnabled = true
        }
    }

    @AfterPermissionGranted(LOCATION_PERMISSION_REQUEST_CODE)
    private fun requestLocationPermission() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (EasyPermissions.hasPermissions(requireContext(), *perms)) {
            setupLocationUpdates()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "This app needs location permission to track your trip",
                LOCATION_PERMISSION_REQUEST_CODE,
                *perms
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 2000 // 2 seconds
            fastestInterval = 1000 // 1 second
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun handleLocationUpdate(location: Location) {
        currentLocation = location
        val latLng = LatLng(location.latitude, location.longitude)

        if (isTripStarted) {
            // Calculate distance from last point
            if (routePoints.isNotEmpty()) {
                val lastPoint = routePoints.last()
                val lastLocation = Location("").apply {
                    latitude = lastPoint.latitude
                    longitude = lastPoint.longitude
                }
                totalDistance += location.distanceTo(lastLocation)
            }

            // Add to route
            routePoints.add(latLng)

            // Draw polyline
            googleMap?.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .color(ContextCompat.getColor(requireContext(), R.color.taxi_yellow))
                    .width(10f)
            )

            updateUI()
        }

        // Move camera to current location
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
    }

    private fun startTrip() {
        if (currentLocation == null) {
            Toast.makeText(requireContext(), "Waiting for location...", Toast.LENGTH_SHORT).show()
            return
        }

        isTripStarted = true
        startLocation = currentLocation
        tripStartTime = System.currentTimeMillis()
        totalDistance = 0.0
        elapsedTime = 0L
        routePoints.clear()

        currentLocation?.let {
            routePoints.add(LatLng(it.latitude, it.longitude))
            googleMap?.addMarker(
                MarkerOptions()
                    .position(LatLng(it.latitude, it.longitude))
                    .title("Start")
            )
        }

        btnStartTrip.visibility = View.GONE
        btnEndTrip.visibility = View.VISIBLE

        handler.post(timerRunnable)

        Toast.makeText(requireContext(), "Trip started!", Toast.LENGTH_SHORT).show()
    }

    private fun endTrip() {
        isTripStarted = false
        handler.removeCallbacks(timerRunnable)

        currentLocation?.let {
            googleMap?.addMarker(
                MarkerOptions()
                    .position(LatLng(it.latitude, it.longitude))
                    .title("End")
            )
        }

        btnStartTrip.visibility = View.VISIBLE
        btnEndTrip.visibility = View.GONE

        // Send notification
        sendTripNotification()

        Toast.makeText(requireContext(), "Trip ended!", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val distanceKm = totalDistance / 1000.0
        val timeMinutes = elapsedTime / 60.0
        val fare = calculateFare(distanceKm, timeMinutes)

        fareAmount.text = String.format("€%.2f", fare)
        distanceText.text = String.format("%.2f km", distanceKm)

        val minutes = (elapsedTime / 60).toInt()
        val seconds = (elapsedTime % 60).toInt()
        timeText.text = String.format("%02d:%02d min", minutes, seconds)
    }

    private fun calculateFare(distanceKm: Double, timeMinutes: Double): Double {
        return BASE_FARE + (distanceKm * FARE_PER_KM) + (timeMinutes * FARE_PER_MINUTE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Taxi Meter",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for trip completion"
            }

            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendTripNotification() {
        val distanceKm = totalDistance / 1000.0
        val timeMinutes = elapsedTime / 60.0
        val fare = calculateFare(distanceKm, timeMinutes)

        val notification = NotificationCompat.Builder(requireContext(), NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("Trip Completed")
            .setContentText(String.format("Distance: %.2f km | Time: %d min | Fare: €%.2f",
                distanceKm, timeMinutes.roundToInt(), fare))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
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
        setupLocationUpdates()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(requireContext(), "Location permission is required", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timerRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}