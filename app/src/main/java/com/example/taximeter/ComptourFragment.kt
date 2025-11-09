package com.example.taximeter

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
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
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import kotlin.math.abs
import kotlin.math.roundToInt

class ComptourFragment : Fragment(), OnMapReadyCallback, EasyPermissions.PermissionCallbacks {

    // Map
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    // UI Elements - Normal View
    private lateinit var fareAmount: TextView
    private lateinit var distanceText: TextView
    private lateinit var timeText: TextView
    private lateinit var btnStartTrip: Button
    private lateinit var btnEndTrip: Button
    private lateinit var fareContainer: View

    // UI Elements - Seven Segment View
    private var sevenSegmentContainer: View? = null
    private var sevenSegmentFare: TextView? = null
    private var sevenSegmentDistance: TextView? = null
    private var sevenSegmentTime: TextView? = null

    // Trip Data
    private var isTripStarted = false
    private var startLocation: Location? = null
    private var currentLocation: Location? = null
    private var previousLocation: Location? = null
    private var totalDistance = 0.0 // in meters
    private var tripStartTime = 0L
    private var elapsedTime = 0L // in seconds

    // Fare Configuration
    private val BASE_FARE = 2.5 // DH
    private val FARE_PER_KM = 1.5 // DH
    private val FARE_PER_MINUTE = 0.5 // DH
    private val MIN_MOVEMENT_DISTANCE = 5.0 // meters

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
    private var routePolyline: Polyline? = null

    // View switcher
    private var isSevenSegmentView = false
    private var gestureDetector: GestureDetector? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 123
        private const val NOTIFICATION_CHANNEL_ID = "taxi_meter_channel"
        private const val NOTIFICATION_ID = 1
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
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

        initializeViews(view)
        setupButtons()
        createNotificationChannel()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupMap()

        // Check and request permissions
        checkLocationPermission()
    }

    private fun initializeViews(view: View) {
        fareAmount = view.findViewById(R.id.fareAmount)
        distanceText = view.findViewById(R.id.distanceText)
        timeText = view.findViewById(R.id.timeText)
        btnStartTrip = view.findViewById(R.id.btnStartTrip)
        btnEndTrip = view.findViewById(R.id.btnEndTrip)
        fareContainer = view.findViewById(R.id.fareContainer)

        // Seven segment views (optional if included)
        sevenSegmentContainer = view.findViewById(R.id.sevenSegmentContainer)
        sevenSegmentFare = view.findViewById(R.id.sevenSegmentFare)
        sevenSegmentDistance = view.findViewById(R.id.sevenSegmentDistance)
        sevenSegmentTime = view.findViewById(R.id.sevenSegmentTime)

        // Hide seven segment by default
        sevenSegmentContainer?.visibility = View.GONE

        btnEndTrip.visibility = View.GONE

        setupGestureDetector()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > SWIPE_THRESHOLD &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                    if (diffX > 0) {
                        onSwipeRight()
                    } else {
                        onSwipeLeft()
                    }
                    return true
                }
                return false
            }
        })

        fareContainer.setOnTouchListener { _, event ->
            gestureDetector?.onTouchEvent(event) ?: false
        }

        sevenSegmentContainer?.setOnTouchListener { _, event ->
            gestureDetector?.onTouchEvent(event) ?: false
        }
    }

    private fun onSwipeLeft() {
        if (!isSevenSegmentView && sevenSegmentContainer != null) {
            switchToSevenSegmentView()
        }
    }

    private fun onSwipeRight() {
        if (isSevenSegmentView) {
            switchToNormalView()
        }
    }

    private fun switchToSevenSegmentView() {
        isSevenSegmentView = true
        fareContainer.visibility = View.GONE
        sevenSegmentContainer?.visibility = View.VISIBLE
        updateUI()
        Toast.makeText(requireContext(), "Seven-Segment View", Toast.LENGTH_SHORT).show()
    }

    private fun switchToNormalView() {
        isSevenSegmentView = false
        fareContainer.visibility = View.VISIBLE
        sevenSegmentContainer?.visibility = View.GONE
        updateUI()
        Toast.makeText(requireContext(), "Normal View", Toast.LENGTH_SHORT).show()
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, it)
                    .commit()
            }
        mapFragment.getMapAsync(this)
    }

    private fun setupButtons() {
        btnStartTrip.setOnClickListener {
            startTrip()
        }

        btnEndTrip.setOnClickListener {
            endTrip()
        }
    }

    private fun checkLocationPermission() {
        val fineLocation = Manifest.permission.ACCESS_FINE_LOCATION
        val coarseLocation = Manifest.permission.ACCESS_COARSE_LOCATION

        if (ActivityCompat.checkSelfPermission(requireContext(), fineLocation) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), coarseLocation) == PackageManager.PERMISSION_GRANTED) {
            // Permission granted
            setupMapUI()
            setupLocationUpdates()
        } else {
            // Request permission
            requestPermissions(
                arrayOf(fineLocation, coarseLocation),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        checkLocationPermission()
    }

    @SuppressLint("MissingPermission")
    private fun setupMapUI() {
        googleMap?.apply {
            try {
                isMyLocationEnabled = true
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isMyLocationButtonEnabled = true
            } catch (e: SecurityException) {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
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

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Cannot access location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleLocationUpdate(location: Location) {
        currentLocation = location
        val latLng = LatLng(location.latitude, location.longitude)

        if (isTripStarted) {
            previousLocation?.let { prevLoc ->
                val distanceFromLast = location.distanceTo(prevLoc)

                if (distanceFromLast >= MIN_MOVEMENT_DISTANCE) {
                    totalDistance += distanceFromLast
                    previousLocation = location

                    routePoints.add(latLng)
                    updatePolyline()
                }
            } ?: run {
                previousLocation = location
                routePoints.add(latLng)
            }

            updateUI()
        }

        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
    }

    private fun updatePolyline() {
        routePolyline?.remove()

        routePolyline = googleMap?.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .color(ContextCompat.getColor(requireContext(), R.color.taxi_yellow))
                .width(10f)
        )
    }

    private fun startTrip() {
        if (currentLocation == null) {
            Toast.makeText(requireContext(), "Waiting for location...", Toast.LENGTH_SHORT).show()
            return
        }

        isTripStarted = true
        startLocation = currentLocation
        previousLocation = currentLocation
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

        sendTripNotification()
        saveToHistory()

        Toast.makeText(requireContext(), "Trip ended! Added to history.", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val distanceKm = totalDistance / 1000.0
        val timeMinutes = elapsedTime / 60.0
        val fare = calculateFare(distanceKm, timeMinutes)

        val minutes = (elapsedTime / 60).toInt()
        val seconds = (elapsedTime % 60).toInt()

        if (isSevenSegmentView) {
            // Update seven segment display
            sevenSegmentFare?.text = String.format("%06.2f", fare)
            sevenSegmentDistance?.text = String.format("%05.2f", distanceKm)
            sevenSegmentTime?.text = String.format("%02d:%02d", minutes, seconds)
        } else {
            // Update normal display
            fareAmount.text = String.format("%.2f DH", fare)
            distanceText.text = String.format("%.2f km", distanceKm)
            timeText.text = String.format("%02d:%02d min", minutes, seconds)
        }
    }

    private fun calculateFare(distanceKm: Double, timeMinutes: Double): Double {
        return BASE_FARE + (distanceKm * FARE_PER_KM) + (timeMinutes * FARE_PER_MINUTE)
    }

    private fun saveToHistory() {
        val prefs = requireContext().getSharedPreferences("trip_history", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val historyCount = prefs.getInt("history_count", 0)
        val newCount = historyCount + 1

        val distanceKm = totalDistance / 1000.0
        val timeMinutes = elapsedTime / 60.0
        val fare = calculateFare(distanceKm, timeMinutes)

        editor.putString("trip_$newCount",
            "$totalDistance|$elapsedTime|$fare|${System.currentTimeMillis()}")
        editor.putInt("history_count", newCount)
        editor.apply()
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
            .setContentText(String.format("Distance: %.2f km | Time: %d min | Fare: %.2f DH",
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

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    setupMapUI()
                    setupLocationUpdates()
                    Toast.makeText(requireContext(), "Location permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    // Permission denied
                    Toast.makeText(requireContext(), "Location permission is required for this app", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        setupMapUI()
        setupLocationUpdates()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(requireContext(), "Location permission is required", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timerRunnable)
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permission when fragment resumes
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (locationCallback == null) {
                setupLocationUpdates()
            }
        }
    }
}