package com.example.autonix_work_in_progress

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.example.autonix_work_in_progress.databinding.ActivityTripSummaryBinding
import com.example.autonix_work_in_progress.models.LatLngPoint
import com.example.autonix_work_in_progress.models.TripData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline


class TripSummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripSummaryBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var tripId: String = ""

    private var StartTime: Long = 0L

    private var EndTime: Long = 0L
    private var tripDuration: String = "0:00"
    private var tripDistance: Double = 0.0
    private var tripAvgSpeed: Double = 0.0
    private var tripTopSpeed: Double = 0.0
    private var tripSafetyScore: Int = 100
    private var tripTimestamp: Long = 0L
    private var tripPath: List<GeoPoint> = emptyList()

    // NEW: Drowsiness events
    private var drowsyEventCount: Int = 0
    private var drowsyEventPoints: MutableList<GeoPoint> = mutableListOf()

    private var distractionEvents: Int = 0
    private var helmetDetected: Boolean = true

    private var isEditingRouteTitle = false

    companion object {
        private const val TAG = "TripSummaryActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance()
            .load(this, PreferenceManager.getDefaultSharedPreferences(this))

        binding = ActivityTripSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractTripDataFromIntent()
        displayTripData()
        setupMapWithPolyline()
        setupButtonListeners()
        binding.btnEditRoute.setOnClickListener {
            if (!isEditingRouteTitle) {
                // Enable editing
                binding.routeTitle.isEnabled = true
                binding.routeTitle.requestFocus()
                binding.routeTitle.setSelection(binding.routeTitle.text.length)
                binding.btnEditRoute.setImageResource(R.drawable.ic_save) // swap icon to save
                isEditingRouteTitle = true
            } else {
                // Disable editing and save title
                binding.routeTitle.isEnabled = false
                binding.btnEditRoute.setImageResource(R.drawable.ic_edit) // swap icon back to edit
                isEditingRouteTitle = false
            }
        }
    }

    private fun extractTripDataFromIntent() {
        StartTime = intent.getLongExtra("startTime",0L)
        EndTime = intent.getLongExtra("endTime", 0L)
        tripId = intent.getStringExtra("trip_id") ?: "trip_${System.currentTimeMillis()}"
        tripDuration = intent.getStringExtra("duration") ?: "0:00"
        tripDistance = intent.getDoubleExtra("distance", 0.0)
        tripAvgSpeed = intent.getDoubleExtra("avgSpeed", 0.0)
        tripTopSpeed = intent.getDoubleExtra("topSpeed", 0.0)
        tripSafetyScore = intent.getIntExtra("safetyScore", 100)
        tripTimestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
        distractionEvents = intent.getIntExtra("distractionEvents", 0)
        helmetDetected = intent.getBooleanExtra("helmetDetected", true)

        // Trip path
        val latitudes = intent.getDoubleArrayExtra("latitudes") ?: doubleArrayOf()
        val longitudes = intent.getDoubleArrayExtra("longitudes") ?: doubleArrayOf()
        tripPath = if (latitudes.size == longitudes.size) {
            latitudes.indices.map { i -> GeoPoint(latitudes[i], longitudes[i]) }
        } else emptyList()

        // Drowsiness events: COUNT and LOCATIONS
        drowsyEventCount = intent.getIntExtra("drowsy_event_count", 0)

        val drowsyLocationsList =
            intent.getSerializableExtra("drowsy_locations") as? ArrayList<HashMap<String, Any>>
        drowsyEventPoints.clear()
        drowsyLocationsList?.forEach { event ->
            val lat = (event["latitude"] as? Double) ?: 0.0
            val lng = (event["longitude"] as? Double) ?: 0.0
            drowsyEventPoints.add(GeoPoint(lat, lng))
        }

        Log.d(
            TAG,
            "Trip extracted: pathPoints=${tripPath.size}, drowsyEvents=$drowsyEventCount"
        )
    }

    private fun displayTripData() {
        binding.tvQuickDistance.text = "%.2f km".format(tripDistance)
        binding.tvQuickDuration.text = tripDuration
        binding.tvSummaryDuration.text = tripDuration
        binding.tvSummaryDistance.text = "%.2f km".format(tripDistance)
        binding.tvSummaryAvgSpeed.text = "%.1f km/h".format(tripAvgSpeed)
        binding.tvSummaryTopSpeed.text = "%.1f km/h".format(tripTopSpeed)
        binding.tvSummarySafetyScore.text = "$tripSafetyScore%"
        binding.safetyProgress.progress = tripSafetyScore

        val scoreColor = when {
            tripSafetyScore >= 80 -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
            tripSafetyScore >= 60 -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            else -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
        }
        binding.tvSummarySafetyScore.setTextColor(scoreColor)
    }

    private fun setupMapWithPolyline() {
        val map = binding.summaryMap
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)

        if (tripPath.isNotEmpty()) {
            val polyline = Polyline().apply {
                setPoints(tripPath)
                color = ContextCompat.getColor(this@TripSummaryActivity, android.R.color.holo_blue_bright)
                width = 8f
                isGeodesic = true
            }
            map.overlays.add(polyline)

            addStartEndMarkers(map)
            addDrowsinessEventMarkers(map)

            val allPoints = tripPath + drowsyEventPoints
            if (allPoints.isNotEmpty()) {
                try {
                    val bbox = BoundingBox.fromGeoPoints(allPoints)
                    map.post { map.zoomToBoundingBox(bbox, true, 100) }
                } catch (e: Exception) {
                    map.controller.setCenter(tripPath.first())
                }
            }
        } else {
            map.controller.setCenter(GeoPoint(19.0760, 72.8777))
        }
        map.invalidate()
    }

    private fun addStartEndMarkers(map: org.osmdroid.views.MapView) {
        if (tripPath.isEmpty()) return
        val startMarker = org.osmdroid.views.overlay.Marker(map).apply {
            position = tripPath.first()
            title = "Trip Start"
            snippet = "Started at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(tripTimestamp)}"
            icon = ContextCompat.getDrawable(this@TripSummaryActivity, R.drawable.ic_start_flag)
            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(startMarker)

        if (tripPath.size > 1) {
            val endMarker = org.osmdroid.views.overlay.Marker(map).apply {
                position = tripPath.last()
                title = "Trip End"
                snippet = "Distance: %.2f km".format(tripDistance)
                icon = ContextCompat.getDrawable(this@TripSummaryActivity, R.drawable.ic_finish_flag)
                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(endMarker)
        }
    }

    private fun addDrowsinessEventMarkers(map: org.osmdroid.views.MapView) {
        drowsyEventPoints.forEachIndexed { index, point ->
            val marker = org.osmdroid.views.overlay.Marker(map).apply {
                position = point
                title = "Drowsiness Alert #${index + 1}"
                snippet = "Safety event detected at this location"
                icon = ContextCompat.getDrawable(this@TripSummaryActivity, R.drawable.ic_warning)
                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(marker)
        }
    }

    private fun setupButtonListeners() {
        binding.btnBackToHome.setOnClickListener { navigateToMainActivity() }
        binding.btnSaveBackToHome.setOnClickListener { saveTripToFirestore() }
    }

    private fun saveTripToFirestore() {
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Debug logs to verify data
        Log.d(TAG, "Current authenticated user ID: $userId")
        Log.d(TAG, "Trip ID: $tripId")

        val tripPoints = tripPath.map { LatLngPoint(it.latitude, it.longitude) }

        val trip = TripData(
            tripId = tripId,
            userId = userId,  // This should match auth.currentUser.uid
            startTime = StartTime,
            endTime = EndTime,
            timestamp = tripTimestamp,
            duration = convertDurationToMilliseconds(tripDuration),
            distance = tripDistance,
            avgSpeed = tripAvgSpeed,
            topSpeed = tripTopSpeed,
            safetyScore = tripSafetyScore,
            drowsinessEvents = drowsyEventCount,
            distractionEvents = distractionEvents,
            helmetDetected = helmetDetected,
            tripTitle = binding.routeTitle.text.toString(),
            path = tripPoints
        )

        // Debug: Log the trip data being saved
        Log.d(TAG, "Trip data userId: ${trip.userId}")
        Log.d(TAG, "Auth user matches trip userId: ${userId == trip.userId}")

        firestore.collection("completed_trips").document(tripId)
            .set(trip)
            .addOnSuccessListener {
                Log.d(TAG, "Trip saved successfully to Firestore")
                saveDrowsinessEventsToFirestore()
                Toast.makeText(this, "Trip saved successfully!", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save trip to Firestore", e)
                Log.e(TAG, "Error code: ${e.message}")

                // Check if it's a permission error specifically
                if (e.message?.contains("PERMISSION_DENIED") == true) {
                    Log.e(TAG, "Permission denied - check Firestore rules")
                    Log.e(TAG, "Authenticated user: $userId")
                    Log.e(TAG, "Trip userId field: ${trip.userId}")
                }

                Toast.makeText(this, "Failed to save trip: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Also add this helper function to test your Firestore connection
    private fun testFirestoreConnection() {
        val userId = auth.currentUser?.uid ?: return

        // Try a simple read operation first
        firestore.collection("completed_trips")
            .whereEqualTo("userId", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Firestore read test successful. Found ${documents.size()} documents")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore read test failed", e)
            }
    }


    private fun saveDrowsinessEventsToFirestore() {
        if (drowsyEventPoints.isEmpty()) return
        val drowsyRef = firestore.collection("completed_trips")
            .document(tripId)
            .collection("drowsinessEvents")

        drowsyEventPoints.forEachIndexed { index, point ->
            val eventData = mapOf(
                "latitude" to point.latitude,
                "longitude" to point.longitude,
                "eventNumber" to index + 1,
                "timestamp" to (tripTimestamp + (index * 60000)) // example timestamp spacing
            )
            drowsyRef.add(eventData)
        }
    }

    private fun convertDurationToMilliseconds(durationStr: String): Long {
        val parts = durationStr.split(":")
        val minutes = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        return (minutes * 60 + seconds) * 1000L
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }
}
