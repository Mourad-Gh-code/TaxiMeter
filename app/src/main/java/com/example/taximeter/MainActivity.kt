package com.example.taximeter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import pub.devrel.easypermissions.EasyPermissions
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), OnMapReadyCallback,
    EasyPermissions.PermissionCallbacks {

    // UI Components
    private lateinit var tvDistance: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvFare: TextView
    private lateinit var tvDriverName: TextView
    private lateinit var tvDriverAge: TextView
    private lateinit var tvDriverLicense: TextView
    private lateinit var ivQRCode: ImageView
    private lateinit var btnStartStop: Button
    private lateinit var chronometer: Chronometer

    // Map
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Trip Data
    private var isTripActive = false
    private var totalDistance = 0.0 // in meters
    private var lastLocation: Location? = null
    private var tripStartTime = 0L

    // Tariffs (in DH)
    private val BASE_FARE = 2.5
    private val FARE_PER_KM = 1.5
    private val FARE_PER_MINUTE = 0.5

    // Driver Info
    private val driverName = "Mohammed Alami"
    private val driverAge = 35
    private val driverLicense = "Permis B"

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 123
        private const val CHANNEL_ID = "taxi_meter_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initMap()
        initLocation()
        createNotificationChannel()
        displayDriverInfo()
        generateQRCode()

        btnStartStop.setOnClickListener {
            if (isTripActive) {
                stopTrip()
            } else {
                startTrip()
            }
        }
    }

    private fun initViews() {
        tvDistance = findViewById(R.id.tvDistance)
        tvTime = findViewById(R.id.tvTime)
        tvFare = findViewById(R.id.tvFare)
        tvDriverName = findViewById(R.id.tvDriverName)
        tvDriverAge = findViewById(R.id.tvDriverAge)
        tvDriverLicense = findViewById(R.id.tvDriverLicense)
        ivQRCode = findViewById(R.id.ivQRCode)
        btnStartStop = findViewById(R.id.btnStartStop)
        chronometer = findViewById(R.id.chronometer)
    }

    private fun initMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocation(location)
                }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        if (hasLocationPermission()) {
            enableMyLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return EasyPermissions.hasPermissions(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun requestLocationPermission() {
        EasyPermissions.requestPermissions(
            this,
            getString(R.string.location_permission_rationale),
            LOCATION_PERMISSION_REQUEST_CODE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)
                    )
                }
            }
        }
    }

    private fun startTrip() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        isTripActive = true
        totalDistance = 0.0
        lastLocation = null
        tripStartTime = System.currentTimeMillis()

        btnStartStop.text = getString(R.string.stop_trip)
        btnStartStop.setBackgroundColor(Color.parseColor("#FF0000"))

        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.start()

        startLocationUpdates()

        Toast.makeText(this, R.string.trip_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopTrip() {
        isTripActive = false

        btnStartStop.text = getString(R.string.start_trip)
        btnStartStop.setBackgroundColor(Color.parseColor("#28A745"))

        chronometer.stop()

        stopLocationUpdates()

        val fare = calculateFare()
        sendTripNotification(totalDistance / 1000, getElapsedMinutes(), fare)

        Toast.makeText(this, R.string.trip_ended, Toast.LENGTH_SHORT).show()
    }

    private fun startLocationUpdates() {

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).apply {
            setMinUpdateIntervalMillis(2000L)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
             the LocationRequest object
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            )
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateLocation(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)

        googleMap?.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng))

        if (isTripActive) {
            lastLocation?.let { last ->
                val distance = last.distanceTo(location)
                totalDistance += distance
                updateUI()
            }
            lastLocation = location
        }
    }

    private fun updateUI() {
        val distanceKm = totalDistance / 1000
        tvDistance.text = getString(R.string.distance_format, distanceKm)

        val minutes = getElapsedMinutes()
        tvTime.text = getString(R.string.time_format, minutes)

        val fare = calculateFare()
        tvFare.text = getString(R.string.fare_format, fare)
    }

    private fun getElapsedMinutes(): Double {
        if (!isTripActive || tripStartTime == 0L) return 0.0
        val elapsed = System.currentTimeMillis() - tripStartTime
        return elapsed / 60000.0
    }

    private fun calculateFare(): Double {
        val distanceKm = totalDistance / 1000
        val minutes = getElapsedMinutes()

        val fare = BASE_FARE + (distanceKm * FARE_PER_KM) + (minutes * FARE_PER_MINUTE)
        return (fare * 100).roundToInt() / 100.0
    }

    private fun displayDriverInfo() {
        tvDriverName.text = getString(R.string.driver_name_format, driverName)
        tvDriverAge.text = getString(R.string.driver_age_format, driverAge)
        tvDriverLicense.text = getString(R.string.driver_license_format, driverLicense)
    }

    private fun generateQRCode() {
        val driverInfo = "Nom: $driverName\nÂge: $driverAge ans\nPermis: $driverLicense"

        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(driverInfo, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    )
                }
            }

            ivQRCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendTripNotification(distance: Double, time: Double, fare: Double) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_taxi)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, fare))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(getString(R.string.notification_detail, distance, time, fare))
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            enableMyLocation()
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(
            this,
            R.string.permission_denied,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTripActive) {
            stopLocationUpdates()
        }
    }
}