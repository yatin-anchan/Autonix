package com.example.autonix_work_in_progress

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.autonix_work_in_progress.databinding.ActivityTripMonitoringBinding
import com.example.autonix_work_in_progress.models.SafetyEvent
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import kotlin.concurrent.thread
import kotlin.math.sqrt
import kotlin.random.Random
import android.content.Context
import android.graphics.PointF
import android.media.MediaPlayer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageProxy
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetector
import java.util.concurrent.Executors
import kotlin.math.hypot

class TripMonitoring : AppCompatActivity(), LocationListener {

    private lateinit var binding: ActivityTripMonitoringBinding

    // Firebase & location clients
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var isGpsToastShown = false

    // Location manager & trip path
    private lateinit var locationManager: LocationManager
    private val tripPath = ArrayList<GeoPoint>()
    private var lastLocation: Location? = null

    // Trip state
    private var isActive = false
    private var isPaused = false
    private var startTime: Long = 0L
    private var pausedTime: Long = 0L

    // Metrics
    private var totalDistance = 0.0
    private var currentSpeed = 0.0
    private var topSpeed = 0.0
    private var avgSpeed = 0.0
    private var altitude = 0.0
    private var safetyScore = 100

    // Map polyline
    private lateinit var osmMap: org.osmdroid.views.MapView
    private var pathPolyline: Polyline? = null

    // UI update handler
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    // Drowsiness detection - Enhanced
    private val safetyEvents = mutableListOf<SafetyEvent>()
    private var lastDrowsinessTime = 0L
    private val cooldownMs = 30_000L // 30 seconds between drowsiness events (reduced for demo)
    private var drowsyEventCount = 0
    private val drowsyLocations = mutableListOf<Map<String, Any>>()


    // Crash detection - sensors
    private lateinit var sensorManager: SensorManager
    private var accelValues = FloatArray(3)
    private var gyroValues = FloatArray(3)
    @Volatile private var crashDetected = false

    @Volatile private var timer_elapsed = false

    // SOS alert thread control
    @Volatile private var sosRunning = false

    // Permissions and pending flags
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val SMS_PERMISSION_REQUEST_CODE = 1002
    private var pendingSendEmergency = false

    // Trip id
    private var currentTripId: String = "trip_${System.currentTimeMillis()}"

    private val TAG = "DrowsinessMain"

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Drowsiness detection params (tune to your environment)
    private val EYE_CLOSED_THRESHOLD = 0.45f   // probability below this => eye considered closed
    private val CLOSED_TIME_THRESHOLD_MS = 1800L // continuous ms eyes closed to trigger drowsiness
    private val YAWN_RATIO_THRESHOLD = 0.55f // lip vertical / lip horizontal ratio

    // State tracking
    @Volatile private var eyesClosedSince: Long = -1L
    private var isAlerting = false

    private lateinit var beepPlayer: MediaPlayer

    private val faceDetector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // for eyeOpenProbability
            .build()
        FaceDetection.getClient(options)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.CAMERA] == true
        if (granted) startCamera()
        else Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid configuration
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))

        binding = ActivityTripMonitoringBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Firebase & location
        db = FirebaseFirestore.getInstance()
        auth = Firebase.auth
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Sensor manager for accelerometer & gyroscope
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }



        setupMap()
        setupLocationManager()
        setupButtons()
        resetTripData()
    }
    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceAnalyzer())
                }

            try {
                cameraProvider.unbindAll()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            if (isPaused) {
                imageProxy.close() // skip analysis
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }
            val rotation = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        // reset state
                        runOnUiThread {
                            binding.tvStatus.text = "No face detected"
                            binding.tvEyeProb.text = "Eyes: - / -"
                            binding.tvYawn.text = "Yawn: -"
                        }
                        eyesClosedSince = -1L
                        isAlerting = false
                    } else {
                        val face = faces[0] // use first/primary face
                        handleFace(face)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun handleFace(face: Face) {
        val leftProb = face.leftEyeOpenProbability ?: -1f
        val rightProb = face.rightEyeOpenProbability ?: -1f

        // Update UI: show probabilities
        runOnUiThread {
            val leftText = if (leftProb >= 0) String.format("%.2f", leftProb) else "-"
            val rightText = if (rightProb >= 0) String.format("%.2f", rightProb) else "-"
            binding.tvEyeProb.text = "Eyes (L/R): $leftText / $rightText"
        }

        // Compute whether eyes are closed (both)
        val bothEyesClosed = (leftProb >= 0 && rightProb >= 0) && (leftProb < EYE_CLOSED_THRESHOLD && rightProb < EYE_CLOSED_THRESHOLD)

        val now = SystemClock.elapsedRealtime()

        if (bothEyesClosed) {
            if (eyesClosedSince < 0) eyesClosedSince = now
            val closedFor = now - eyesClosedSince
            runOnUiThread {
                binding.tvStatus.text = if (closedFor >= CLOSED_TIME_THRESHOLD_MS) "DROWSY: eyes closed ${closedFor}ms" else "Eyes closed for ${closedFor}ms"
                binding.tvLog.text = "Closed since ${eyesClosedSince}"
            }

            if (closedFor >= CLOSED_TIME_THRESHOLD_MS && !isAlerting) {
                // trigger alert
                isAlerting = true
                triggerAlert("Drowsiness detected: eyes closed")
                val currentTime = System.currentTimeMillis()
                val lastLat = lastLocation?.latitude ?: 0.0
                val lastLng = lastLocation?.longitude ?: 0.0
                val description = "EYE CLOSED at $currentTime"

            // Update counters and score
                drowsyEventCount++
                safetyScore = (safetyScore - 2).coerceAtLeast(0)

            // Store event location
            val eventData = mapOf( "latitude" to lastLat,
                "longitude" to lastLng, "timestamp" to currentTime,
                "description" to description,
                "eventType" to "drowsiness" )
                drowsyLocations.add(eventData)
                saveDrowsinessEventToFirestore(eventData)
                binding.tvSafetyScore.text = "Safety Score: $safetyScore"
            }

        } else {
            eyesClosedSince = -1L
            if (isAlerting) {
                isAlerting = false
                runOnUiThread {
                    binding.tvStatus.text = "Alert cleared — eyes opened"
                }
            } else {
                runOnUiThread {
                    binding.tvStatus.text = "Status: normal"
                }
            }
        }
        // Yawn detection using upper/lower lip contour
        detectYawn(face)

    }

    private fun detectYawn(face: Face) {
        val upperLipTopPoints = face.getContour(FaceContour.UPPER_LIP_TOP)?.points
        val lowerLipBottomPoints = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points

        // ensure we have valid contour points (non-null & non-empty)
        if (upperLipTopPoints.isNullOrEmpty() || lowerLipBottomPoints.isNullOrEmpty()) {
            runOnUiThread { binding.tvYawn.text = "Yawn: N/A" }
            return
        }

        val mouthLeft = lowerLipBottomPoints.firstOrNull()
        val mouthRight = lowerLipBottomPoints.lastOrNull()
        if (mouthLeft == null || mouthRight == null) {
            runOnUiThread { binding.tvYawn.text = "Yawn: N/A" }
            return
        }

        // approximate vertical distance (top of upper lip to bottom of lower lip)
        val top = averagePoint(upperLipTopPoints)
        val bottom = averagePoint(lowerLipBottomPoints)
        val vert = distance(top.x, top.y, bottom.x, bottom.y)

        // horizontal mouth width
        val hor = distance(mouthLeft.x, mouthLeft.y, mouthRight.x, mouthRight.y)

        val ratio = if (hor > 0f) vert / hor else 0f

        runOnUiThread {
            binding.tvYawn.text = "YawnRatio: ${"%.2f".format(ratio)}"
        }

        if (ratio > YAWN_RATIO_THRESHOLD) {
            runOnUiThread { binding.tvLog.text = "Yawn detected (ratio ${"%.2f".format(ratio)})" }
            triggerAlert("Yawn detected")
        }
    }

    private fun averagePoint(points: List<PointF>): PointF {
        if (points.isEmpty()) return PointF(0f, 0f)
        var sx = 0f
        var sy = 0f
        for (p in points) {
            sx += p.x
            sy += p.y
        }
        return PointF(sx / points.size, sy / points.size)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
    }

    private fun triggerAlert(message: String) {
        Log.i(TAG, "ALERT: $message")

        // beep
        try {
            if (!beepPlayer.isPlaying) {
                beepPlayer.start()
            }
        } catch (e: Exception) { /* ignore */ }

        // vibrate
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(700)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrator not available: ${e.message}")
        }

        runOnUiThread {
            binding.tvStatus.text = "ALERT: $message"
            binding.tvLog.text = "ALERT triggered at ${System.currentTimeMillis()}"
        }

        // stop alerting after some time (so it doesn't loop continuously)
        Handler(Looper.getMainLooper()).postDelayed({
            isAlerting = false
        }, 2500)
    }

    private fun setupMap() {
        osmMap = binding.osmMap
        osmMap.setTileSource(TileSourceFactory.MAPNIK)
        osmMap.setMultiTouchControls(true)
        osmMap.controller.setZoom(15.0)
        osmMap.controller.setCenter(GeoPoint(19.0760, 72.8777)) // Mumbai default
        pathPolyline = Polyline().apply {
            width = 5f
            color = ContextCompat.getColor(this@TripMonitoring, android.R.color.holo_blue_bright)
        }
        osmMap.overlays.add(pathPolyline)
    }

    private fun setupLocationManager() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    @OptIn(ExperimentalGetImage::class)
    private fun setupButtons() {
        binding.btnPlayPause.setOnClickListener {
            if (!isActive) startTrip()
            else if (isPaused) resumeTrip()
            else pauseTrip()
        }
        binding.btnStop.setOnClickListener { stopTrip() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun checkPermissions(): Boolean {
        val required = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.SEND_SMS
        )
        val missing = required.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing) {
            ActivityCompat.requestPermissions(this, required.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
            return false
        }
        return true
    }

    // SENSOR LISTENER
    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> accelValues = event.values.clone()
                Sensor.TYPE_GYROSCOPE -> gyroValues = event.values.clone()
            }
            detectCrash()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Detect crash: spike in accel/gyro + confirm via speed drop (fast -> ~0)
    private fun detectCrash() {
        if (crashDetected || !isActive || isPaused) return

        val accelForce = sqrt(
            accelValues[0]*accelValues[0] +
                    accelValues[1]*accelValues[1] +
                    accelValues[2]*accelValues[2]
        )
        val gyroForce = sqrt(
            gyroValues[0]*gyroValues[0] +
                    gyroValues[1]*gyroValues[1] +
                    gyroValues[2]*gyroValues[2]
        )

        val demoAccelThreshold = 15.0
        val demoGyroThreshold = 5.0

        if (accelForce > demoAccelThreshold || gyroForce > demoGyroThreshold) {
            crashDetected = true
            timer_elapsed = false
            runOnUiThread { showCrashPopup() }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun showCrashPopup() {
        pauseTrip()
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_crash_detected, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val cancelBtn = dialogView.findViewById<Button>(R.id.btnCancel)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()

        progressBar.max = 30
        progressBar.progress = 30

        startSOSAlert()

        val timer = object : CountDownTimer(30_000, 1000) {
            var countdown = 30
            val tvCountdown = dialogView.findViewById<TextView>(R.id.tvCountdown)

            override fun onTick(millisUntilFinished: Long) {
                countdown--
                progressBar.progress = countdown
                tvCountdown.text = "$countdown s"
            }

            override fun onFinish() {
                dialog.dismiss()
                stopSOSAlert()
                sendEmergencyAlert()
                crashDetected = false
                Toast.makeText(this@TripMonitoring, "Crash alert sent", Toast.LENGTH_SHORT).show()
                openCrashCard()
            }
        }

        cancelBtn.setOnClickListener {
            timer.cancel()
            dialog.dismiss()
            stopSOSAlert()
            crashDetected = false
            Toast.makeText(this, "Crash alert canceled", Toast.LENGTH_SHORT).show()
            resumeTrip()
        }

        timer.start()
    }

    private fun openCrashCard(){
        val intent = Intent(this, CrashAlertActivity::class.java)
        startActivity(intent)
    }

    private fun startSOSAlert() {
        if (sosRunning) return
        sosRunning = true

        thread {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = try { cameraManager.cameraIdList[0] } catch (e: Exception) { null }
            val pattern = "... --- ..."

            while (sosRunning) {
                for (ch in pattern) {
                    if (!sosRunning) break
                    when (ch) {
                        '.' -> {
                            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                            cameraId?.let { id ->
                                try { cameraManager.setTorchMode(id, true) } catch (_: Exception) {}
                            }
                            Thread.sleep(200)
                        }
                        '-' -> {
                            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                            cameraId?.let { id ->
                                try { cameraManager.setTorchMode(id, true) } catch (_: Exception) {}
                            }
                            Thread.sleep(600)
                        }
                        else -> {
                            Thread.sleep(200)
                        }
                    }
                    cameraId?.let { id ->
                        try { cameraManager.setTorchMode(id, false) } catch (_: Exception) {}
                    }
                    Thread.sleep(200)
                }
                Thread.sleep(800)
            }

            cameraId?.let { id ->
                try { (getSystemService(CAMERA_SERVICE) as CameraManager).setTorchMode(id, false) } catch (_: Exception) {}
            }
        }
    }

    private fun stopSOSAlert() {
        sosRunning = false
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = cameraManager.cameraIdList.getOrNull(0)
            if (id != null) cameraManager.setTorchMode(id, false)
        } catch (_: Exception) {}
    }

    private fun sendEmergencyAlert() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Not authenticated. Cannot send emergency alert.", Toast.LENGTH_SHORT).show()
            crashDetected = false
            return
        }

        val contactsRef = db.collection("emergency_contacts").document(user.uid).collection("contacts")
        contactsRef.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No emergency contacts available!", Toast.LENGTH_LONG).show()
                    crashDetected = false
                    return@addOnSuccessListener
                }

                val contacts = snapshot.mapNotNull { it.getString("phone") }.map { it.trim() }.filter { it.isNotEmpty() }
                if (contacts.isEmpty()) {
                    Toast.makeText(this, "Emergency contacts do not have phone numbers!", Toast.LENGTH_LONG).show()
                    crashDetected = false
                    return@addOnSuccessListener
                }

                getLastLocation { location ->
                    val smsBody = if (location != null) {
                        val lat = location.latitude
                        val lon = location.longitude
                        "EMERGENCY! Crash detected. My location: https://www.google.com/maps?q=$lat,$lon&z=18"
                    } else {
                        "EMERGENCY! Crash detected. Location not available. Please call me."
                    }

                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        val smsManager = SmsManager.getDefault()
                        val sent = mutableListOf<String>()
                        val failed = mutableListOf<String>()
                        for (phone in contacts) {
                            try {
                                val parts = smsManager.divideMessage(smsBody)
                                if (parts.size > 1) smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                                else smsManager.sendTextMessage(phone, null, smsBody, null, null)
                                sent.add(phone)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                failed.add(phone)
                            }
                        }
                        showResultDialog(sent, failed, true)
                    } else {
                        pendingSendEmergency = true
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_REQUEST_CODE)

                        for (phone in contacts) {
                            val smsUri = Uri.parse("smsto:$phone")
                            val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                putExtra("sms_body", smsBody)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
                        }
                        Toast.makeText(this, "SMS permission requested. Composer opened as fallback.", Toast.LENGTH_LONG).show()
                        crashDetected = false
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to fetch emergency contacts!", Toast.LENGTH_SHORT).show()
                crashDetected = false
            }
    }

    private fun showResultDialog(sent: List<String>, failed: List<String>, isEmergency: Boolean) {
        val title = if (isEmergency) "Emergency SMS Results" else "SMS Results"
        val builder = AlertDialog.Builder(this)
            .setTitle(title)

        val message = StringBuilder()
        if (sent.isNotEmpty()) {
            message.append("Sent to:\n")
            sent.forEach { message.append("• $it\n") }
        }
        if (failed.isNotEmpty()) {
            if (message.isNotEmpty()) message.append("\n")
            message.append("Failed to send to:\n")
            failed.forEach { message.append("• $it\n") }
        }
        if (message.isEmpty()) message.append("No recipients.")

        builder.setMessage(message.toString())
        builder.setPositiveButton("OK", null)
        builder.show()
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
                            .addOnSuccessListener { fresh -> callback(fresh) }
                            .addOnFailureListener { callback(null) }
                    }
                }
                .addOnFailureListener { callback(null) }
        } catch (e: SecurityException) {
            callback(null)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startTrip() {
        if (!checkPermissions()) {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            return
        }

        isActive = true
        isPaused = false
        startTime = System.currentTimeMillis()
        currentTripId = "trip_${System.currentTimeMillis()}"

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0.5f, this)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            return
        }

        startUIUpdates()
        startDrowsinessDetection()
        binding.btnPlayPause.text = "Pause"
        Toast.makeText(this, "Trip started - Drowsiness detection active", Toast.LENGTH_SHORT).show()
    }

    private fun pauseTrip() {
        if (!isActive || isPaused) return
        isPaused = true
        pausedTime = System.currentTimeMillis()
        stopUIUpdates() // optional: stop counters / timers
        binding.btnPlayPause.text = "Resume"
        Toast.makeText(this, "Trip paused", Toast.LENGTH_SHORT).show()
        // Do NOT shutdown cameraExecutor or close faceDetector
    }

    private fun resumeTrip() {
        if (!isPaused) return
        isPaused = false
        binding.btnPlayPause.text = "Pause"
        Toast.makeText(this, "Trip resumed", Toast.LENGTH_SHORT).show()
        // Analysis automatically resumes because analyzer checks isPaused
    }


    private fun stopTrip() {
        if (!isActive) return
        isActive = false
        isPaused = false
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        stopUIUpdates()
        stopDrowsinessDetection()
        cameraExecutor.shutdown()
        faceDetector.close()
        try { beepPlayer.release() } catch (_: Exception) {}

        val intent = Intent(this, TripSummaryActivity::class.java).apply {
            putExtra("startTime", startTime)
            putExtra("endTime", System.currentTimeMillis())
            putExtra("duration", binding.tvDuration.text.toString())
            putExtra("distance", totalDistance)
            putExtra("avgSpeed", avgSpeed)
            putExtra("topSpeed", topSpeed)
            putExtra("safetyScore", safetyScore)
            putExtra("drowsy_event_count", drowsyEventCount)
            putExtra("drowsy_locations", ArrayList(drowsyLocations))
            putParcelableArrayListExtra("safetyEvents", ArrayList(safetyEvents))
            putExtra("latitudes", tripPath.map { it.latitude }.toDoubleArray())
            putExtra("longitudes", tripPath.map { it.longitude }.toDoubleArray())
        }
        startActivity(intent)
        resetTripData()
        binding.btnPlayPause.text = "Start"
        Toast.makeText(this, "Trip stopped", Toast.LENGTH_SHORT).show()
    }

    private fun startUIUpdates() {
        updateRunnable = object : Runnable {
            override fun run() {
                if (!isActive || isPaused) return

                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val minutes = elapsed / 60
                val seconds = elapsed % 60
                binding.tvDuration.text = "%d:%02d".format(minutes, seconds)

                avgSpeed = if (totalDistance > 0) totalDistance / ((System.currentTimeMillis() - startTime) / 3600000.0) else 0.0
                binding.tvAvgSpeed.text = avgSpeed.toInt().toString()

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopUIUpdates() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }

    override fun onLocationChanged(location: Location) {
        if (!isActive || isPaused) return

        currentSpeed = location.speed * 3.6
        altitude = location.altitude
        if (currentSpeed > topSpeed) topSpeed = currentSpeed

        lastLocation?.let { last ->
            totalDistance += location.distanceTo(last) / 1000.0
        }
        lastLocation = location

        val point = GeoPoint(location.latitude, location.longitude)
        tripPath.add(point)
        pathPolyline?.setPoints(tripPath)
        osmMap.controller.setCenter(point)
        osmMap.invalidate()

        binding.tvCurrentSpeed.text = currentSpeed.toInt().toString()
        binding.tvTopSpeed.text = topSpeed.toInt().toString()
        binding.tvDistance.text = "%.2f".format(totalDistance)
        binding.tvAltitude.text = altitude.toInt().toString()

        saveLocationToFirestore(location)
    }

    private fun saveLocationToFirestore(location: Location) {
        val locData = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("completed_trips")
            .document(currentTripId)
            .collection("locations")
            .add(locData)
            .addOnFailureListener { e ->
                Log.e("TripMonitoring", "Failed to save location", e)
            }
    }

    // Enhanced Drowsiness Detection
    private fun startDrowsinessDetection() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission required for drowsiness detection", Toast.LENGTH_SHORT).show()
            return
        }

        beepPlayer = MediaPlayer.create(this, R.raw.alarm_sound)

        if (allPermissionsGranted()) startCamera()
        else requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        Log.d("TripMonitoring", "Starting drowsiness detection...")


    }
    private fun stopDrowsinessDetection() {
        Log.d("TripMonitoring", "Drowsiness detection stopped")
    }


    private fun saveDrowsinessEventToFirestore(eventData: Map<String, Any>) {
        val user = auth.currentUser
        if (user == null) {
            Log.w("TripMonitoring", "User not authenticated, cannot save drowsiness event")
            return
        }

        // Save to trip-specific drowsiness events collection
        db.collection("completed_trips")
            .document(currentTripId)
            .collection("drowsinessEvents")
            .add(eventData)
            .addOnSuccessListener {
                Log.d("TripMonitoring", "Drowsiness event saved to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("TripMonitoring", "Failed to save drowsiness event to Firestore", e)
            }

        // Also update the main trip document with latest drowsiness count
        db.collection("completed_trips")
            .document(currentTripId)
            .update(mapOf(
                "drowsyEventCount" to drowsyEventCount,
                "safetyScore" to safetyScore,
                "lastDrowsinessEvent" to eventData["timestamp"]
            ))
            .addOnFailureListener { e ->
                Log.e("TripMonitoring", "Failed to update trip document", e)
            }

        val currentTime = System.currentTimeMillis()

        // Save to user's overall safety statistics
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentTotalDrowsinessEvents = document.getLong("totalDrowsinessEvents") ?: 0
                    val currentSafetyScore = document.getLong("safetyScore") ?: 100

                    db.collection("users").document(user.uid)
                        .update(mapOf(
                            "totalDrowsinessEvents" to currentTotalDrowsinessEvents + 1,
                            "safetyScore" to minOf(currentSafetyScore, safetyScore.toLong()),
                            "lastDrowsinessEventDate" to currentTime
                        ))
                }
            }
            .addOnFailureListener { e ->
                Log.e("TripMonitoring", "Failed to update user safety statistics", e)
            }
    }

    private fun resetTripData() {
        totalDistance = 0.0
        currentSpeed = 0.0
        topSpeed = 0.0
        avgSpeed = 0.0
        altitude = 0.0
        safetyScore = 100
        tripPath.clear()
        pathPolyline?.setPoints(tripPath)
        osmMap.invalidate()
        safetyEvents.clear()
        binding.tvCurrentSpeed.text = "0"
        binding.tvTopSpeed.text = "0"
        binding.tvAvgSpeed.text = "0"
        binding.tvDistance.text = "0"
        binding.tvAltitude.text = "0"
        binding.tvSafetyScore.text = "100%"
        binding.tvDuration.text = "0:00"
        drowsyEventCount = 0
        drowsyLocations.clear()
        lastDrowsinessTime = 0L
        currentTripId = "trip_${System.currentTimeMillis()}"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted - you can now start your trip", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions denied. Safety features may not work properly.", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted && pendingSendEmergency) {
                pendingSendEmergency = false
                sendEmergencyAlert()
            } else {
                pendingSendEmergency = false
                Toast.makeText(this, "SMS permission denied - cannot auto-send emergency messages.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { sensorManager.unregisterListener(sensorEventListener) } catch (_: Exception) {}
        stopDrowsinessDetection()
        stopUIUpdates()
        stopSOSAlert()
        cameraExecutor.shutdown()
        faceDetector.close()
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        // Don't stop drowsiness detection when app goes to background during trip
        if (!isActive) {
            stopDrowsinessDetection()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun onResume() {
        super.onResume()
        // Resume drowsiness detection if trip is active
        if (isActive && !isPaused) {
            startDrowsinessDetection()
        }
    }

    // The other LocationListener required overrides
    override fun onProviderEnabled(provider: String) {
        Log.d("TripMonitoring", "Location provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.w("TripMonitoring", "Location provider disabled: $provider")
        if (provider == LocationManager.GPS_PROVIDER) {
            Toast.makeText(this, "GPS disabled - please enable for accurate tracking", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d("TripMonitoring", "Location provider status changed: $provider, status: $status")
    }
}