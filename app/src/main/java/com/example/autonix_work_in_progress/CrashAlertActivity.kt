package com.example.autonix_work_in_progress

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.telephony.SmsManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.random.Random
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CrashAlertActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tvName: TextView
    private lateinit var tvAge: TextView
    private lateinit var tvDob: TextView
    private lateinit var tvBloodGroup: TextView
    private lateinit var tvCrashTime: TextView
    private lateinit var tvContacts: TextView
    private lateinit var btnStop: Button

    private var sosJob: Job? = null
    private var gradientJob: Job? = null
    private var torchOn = false
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    private val LOCATION_PERMISSION_CODE = 2001
    private val SMS_PERMISSION_CODE = 2002
    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var pendingSendEmergency = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_alert)

        // Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Views
        tvName = findViewById(R.id.tvName)
        tvAge = findViewById(R.id.tvAge)
        tvDob = findViewById(R.id.tvDob)
        tvBloodGroup = findViewById(R.id.tvBloodGroupIcon)
        tvCrashTime = findViewById(R.id.tvCrashTime)
        tvContacts = findViewById(R.id.tvContacts)
        btnStop = findViewById(R.id.btnStop)

        // Torch
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager?.cameraIdList?.getOrNull(0)

        // Location
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermission()

        // Populate user info
        loadUserInfo()

        // Start SOS + gradient
        startEmergencyLoop()

        btnStop.setOnClickListener { finish() }
    }

    private fun checkLocationPermission() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_CODE)
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1f, locationListener)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 1f, locationListener)
        } catch (_: SecurityException) {}
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun loadUserInfo() {
        val user = auth.currentUser ?: return

        // Read user profile
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val fullName = doc.getString("fullName") ?: "Unknown"
                val phone = doc.getString("phone") ?: "Unknown"
                val blood_group =  doc.getString("bloodGroup")
                val createdAt = doc.getLong("createdAt") ?: 0L

                tvName.text = "Name: $fullName"
                tvDob.text = "Phone: $phone"  // you can map DOB later if you store it
                tvBloodGroup.text = "$blood_group"
                tvAge.text = "Joined: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(createdAt))}"

                val time = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date())
                tvCrashTime.text = "Crash Occurred: $time"
            }

        // Read emergency contacts from subcollection
        db.collection("emergency_contacts").document(user.uid)
            .collection("contacts")
            .get()
            .addOnSuccessListener { snapshot ->
                val phones = snapshot.mapNotNull { it.getString("phone") }
                if (phones.isNotEmpty()) {
                    tvContacts.text = "Emergency Contacts: ${phones.joinToString()}"
                } else {
                    tvContacts.text = "Emergency Contacts: None"
                }
            }
    }


    private fun calculateAge(dob: String): Int {
        return try {
            val parts = dob.split("-")
            val calDob = Calendar.getInstance().apply {
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            val now = Calendar.getInstance()
            var age = now.get(Calendar.YEAR) - calDob.get(Calendar.YEAR)
            if (now.get(Calendar.DAY_OF_YEAR) < calDob.get(Calendar.DAY_OF_YEAR)) age--
            age
        } catch (e: Exception) { 0 }
    }

    private fun startEmergencyLoop() {
        gradientJob = CoroutineScope(Dispatchers.Main).launch {
            val colors = arrayOf(
                intArrayOf(0xFFFF0000.toInt(), 0xFF800000.toInt()),
                intArrayOf(0xFF800000.toInt(), 0xFFFF0000.toInt())
            )
            var i = 0
            while (isActive) {
                val gd = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, colors[i % 2])
                window.decorView.background = gd
                i++
                delay(500)
            }
        }

        sosJob = CoroutineScope(Dispatchers.IO).launch {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            val morse = "... --- ..."
            while (isActive) {
                for (ch in morse) {
                    if (!isActive) break
                    when (ch) {
                        '.' -> { toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200); toggleFlash(200) }
                        '-' -> { toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600); toggleFlash(600) }
                        else -> delay(200)
                    }
                    delay(200)
                }
                delay(800)
                getLastLocation { loc -> loc?.let { sendEmergencySMS(it.latitude, it.longitude) } }
            }
        }
    }

    private suspend fun toggleFlash(duration: Long) {
        cameraId?.let {
            try {
                cameraManager?.setTorchMode(it, true)
                delay(duration)
                cameraManager?.setTorchMode(it, false)
            } catch (_: Exception) {}
        }
    }

    private fun getLastLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback(null)
            return
        }

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) callback(loc)
                    else {
                        val cts = CancellationTokenSource()
                        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                            .addOnSuccessListener { callback(it) }
                            .addOnFailureListener { callback(null) }
                    }
                }
                .addOnFailureListener { callback(null) }
        } catch (e: SecurityException) {
            callback(null)
        }
    }

    private fun sendEmergencySMS(lat: Double, lon: Double) {
        val user = auth.currentUser ?: return
        val contactsRef = db.collection("emergency_contacts").document(user.uid).collection("contacts")
        contactsRef.get().addOnSuccessListener { snapshot ->
            val contacts = snapshot.mapNotNull { it.getString("phone") }.filter { it.isNotEmpty() }
            if (contacts.isEmpty()) return@addOnSuccessListener

            val smsBody = "EMERGENCY! Crash detected. My location: https://www.google.com/maps?q=$lat,$lon&z=18"

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val smsManager = SmsManager.getDefault()
                contacts.forEach { phone ->
                    try {
                        val parts = smsManager.divideMessage(smsBody)
                        if (parts.size > 1) smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                        else smsManager.sendTextMessage(phone, null, smsBody, null, null)
                    } catch (_: Exception) {}
                }
            } else {
                pendingSendEmergency = true
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
            }
        }
    }

    private fun stopEmergencyLoop() {
        sosJob?.cancel()
        gradientJob?.cancel()
        cameraId?.let { cameraManager?.setTorchMode(it, false) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) startLocationUpdates()
        } else if (requestCode == SMS_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingSendEmergency) {
                pendingSendEmergency = false
                currentLocation?.let { sendEmergencySMS(it.latitude, it.longitude) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEmergencyLoop()
        locationManager.removeUpdates(locationListener)
    }
}
