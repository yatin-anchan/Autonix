package com.example.autonix_work_in_progress

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.autonix_work_in_progress.databinding.ActivityTripHistoryDetailedBinding
import com.example.autonix_work_in_progress.models.SafetyEvent
import com.example.autonix_work_in_progress.models.SafetyEventType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.views.overlay.IconOverlay.ANCHOR_BOTTOM
import org.osmdroid.views.overlay.IconOverlay.ANCHOR_CENTER
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripHistoryDetailedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripHistoryDetailedBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var tripData: DetailedTripData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripHistoryDetailedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val tripId = intent.getStringExtra("trip_id") ?: ""

        setupUI()
        loadTripDetails(tripId)
        loadAllSafetyEvents(tripId)

    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnDeleteTrip.setOnClickListener {
            showDeleteConfirmation()
        }

        binding.btnShareTrip.setOnClickListener {
            shareTrip()
        }
    }

    private fun loadTripDetails(tripId: String) {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("completed_trips")
            .document(tripId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    try {
                        tripData = DetailedTripData(
                            tripId = document.id,
                            startTime = document.getLong("startTime") ?: 0L,
                            endTime = document.getLong("endTime") ?: 0L,
                            duration = formatDuration(document.getLong("duration") ?: 0L),
                            distanceKm = document.getDouble("distance") ?: 0.0,
                            maxSpeedKmh = document.getDouble("topSpeed") ?: 0.0,
                            avgSpeedKmh = document.getDouble("avgSpeed") ?: 0.0,
                            safetyScore = (document.getLong("safetyScore") ?: 100L).toInt(),
                            tripTitle = document.getString("tripTitle") ?: "Trip",
                            drowsinessEvents = (document.getLong("drowsinessEvents") ?: 0L).toInt(),
                            distractedEvents = (document.getLong("distractionEvents") ?: 0L).toInt(),
                            helmetDetected = document.getBoolean("helmetDetected") ?: true,
                            altitudeMaxM = document.getDouble("maxAltitude") ?: 0.0,
                            locationPoints = parseLocationPoints(document.get("path"))
                        )

                        displayTripDetails()
                        setupMap()
                        loadAllSafetyEvents(tripId)

                    } catch (e: Exception) {
                        android.util.Log.e("TripDetails", "Error parsing trip data", e)
                        showError("Error loading trip details")
                    }
                } else {
                    showError("Trip not found")
                }

                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                android.util.Log.w("TripDetails", "Error getting trip", exception)
                showError("Failed to load trip details")
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        val remainingSeconds = seconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
        } else {
            String.format("%d:%02d", remainingMinutes, remainingSeconds)
        }
    }

    private fun parseLocationPoints(locationData: Any?): List<LocationPoint> {
        val points = mutableListOf<LocationPoint>()

        if (locationData is List<*>) {
            locationData.forEach { point ->
                if (point is Map<*, *>) {
                    val lat = point["latitude"] as? Double ?: 0.0
                    val lng = point["longitude"] as? Double ?: 0.0
                    val timestamp = point["timestamp"] as? Long ?: 0L
                    points.add(LocationPoint(lat, lng, timestamp))
                }
            }
        }

        return points
    }

    private fun displayTripDetails() {
        val trip = tripData ?: return

        // Trip title and times in header
        binding.tvTripTitle.text = trip.tripTitle

        val startTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val endTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        binding.tvStartTime.text = startTimeFormat.format(Date(trip.startTime))
        binding.tvEndTime.text = endTimeFormat.format(Date(trip.endTime))

        // Trip date
        binding.tvTripDate.text = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            .format(Date(trip.startTime))

        // Trip statistics
        binding.tvDuration.text = trip.duration
        binding.tvDistance.text = String.format("%.1f km", trip.distanceKm)
        binding.tvAvgSpeed.text = String.format("%.1f km/h", trip.avgSpeedKmh)
        binding.tvMaxSpeed.text = String.format("%.1f km/h", trip.maxSpeedKmh)

        // Safety score with color coding
        binding.tvSafetyScore.text = "${trip.safetyScore}%"
        updateSafetyScoreColor(trip.safetyScore)

        // Safety details
        binding.tvDrowsinessEvents.text = "Drowsiness Events: ${trip.drowsinessEvents}"
        binding.tvDistractedEvents.text = "Distraction Events: ${trip.distractedEvents}"
        binding.tvHelmetStatus.text = if (trip.helmetDetected) {
            "Helmet: Detected ✓"
        } else {
            "Helmet: Not Detected ✗"
        }

        binding.tvMaxAltitude.text = if (trip.altitudeMaxM > 0) {
            "Max Altitude: ${trip.altitudeMaxM.toInt()}m"
        } else {
            "Max Altitude: Not Available"
        }

        // Update safety events visibility
        val totalSafetyEvents = trip.drowsinessEvents + trip.distractedEvents
        if (totalSafetyEvents == 0) {
            binding.tvNoSafetyEvents.visibility = View.VISIBLE
            binding.recyclerSafetyEvents.visibility = View.GONE
        } else {
            binding.tvNoSafetyEvents.visibility = View.GONE
            binding.recyclerSafetyEvents.visibility = View.VISIBLE
        }
    }

    private fun updateSafetyScoreColor(score: Int) {
        val scoreColor = when {
            score >= 90 -> R.color.success_green
            score >= 75 -> R.color.warning_orange
            score >= 60 -> R.color.warning_orange
            else -> R.color.error_red
        }
        binding.tvSafetyScore.setTextColor(ContextCompat.getColor(this, scoreColor))
    }

    private fun setupMap() {
        val trip = tripData ?: return

        org.osmdroid.config.Configuration.getInstance()
            .load(this, android.preference.PreferenceManager.getDefaultSharedPreferences(this))

        val map = binding.mapView
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        if (trip.locationPoints.isNotEmpty()) {
            val pathPoints = trip.locationPoints.map {
                org.osmdroid.util.GeoPoint(it.latitude, it.longitude)
            }

            // Draw trip polyline with gradient effect
            val polyline = org.osmdroid.views.overlay.Polyline().apply {
                setPoints(pathPoints)
                color = ContextCompat.getColor(this@TripHistoryDetailedActivity, R.color.primary_blue)
                width = 12f
            }
            map.overlays.add(polyline)

            // Enhanced start marker
            val startMarker = org.osmdroid.views.overlay.Marker(map).apply {
                position = pathPoints.first()
                title = "Trip Start"
                snippet = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(trip.startTime))
                icon = ContextCompat.getDrawable(this@TripHistoryDetailedActivity, R.drawable.ic_start_flag)
                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(startMarker)

            // Enhanced end marker
            val endMarker = org.osmdroid.views.overlay.Marker(map).apply {
                position = pathPoints.last()
                title = "Trip End"
                snippet = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(trip.endTime))
                icon = ContextCompat.getDrawable(this@TripHistoryDetailedActivity, R.drawable.ic_finish_flag)
                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(endMarker)

            // Zoom to fit entire trip with padding
            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(pathPoints)
            map.post {
                map.zoomToBoundingBox(boundingBox, true, 100)
            }
        }

        map.invalidate()
    }


    private fun loadDistractionEvents(tripId: String) {
        firestore.collection("completed_trips")
            .document(tripId)
            .collection("distractionEvents")
            .get()
            .addOnSuccessListener { documents ->
                val map = binding.mapView

                for (document in documents) {
                    val lat = document.getDouble("latitude") ?: continue
                    val lng = document.getDouble("longitude") ?: continue
                    val ts = document.getLong("timestamp") ?: 0L

                    val marker = org.osmdroid.views.overlay.Marker(map).apply {
                        position = org.osmdroid.util.GeoPoint(lat, lng)
                        title = "📱 Distraction Event"
                        snippet = "Time: ${
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
                        }"
                        icon = ContextCompat.getDrawable(
                            this@TripHistoryDetailedActivity,
                            R.drawable.ic_phone_warning
                        )
                        setAnchor(ANCHOR_CENTER, ANCHOR_BOTTOM)
                    }
                    map.overlays.add(marker)
                }

                map.invalidate()
            }
    }

    private fun displaySafetyEvents(events: List<SafetyEvent>) {
        val adapter = SafetyEventsAdapter(events) { event ->
            // Handle safety event click - zoom to location
            val map = binding.mapView
            val geoPoint = org.osmdroid.util.GeoPoint(event.latitude, event.longitude)
            map.controller.animateTo(geoPoint)
            map.controller.setZoom(18.0)
        }

        binding.recyclerSafetyEvents.apply {
            layoutManager = LinearLayoutManager(this@TripHistoryDetailedActivity)
            this.adapter = adapter
        }

        if (events.isEmpty()) {
            binding.tvNoSafetyEvents.visibility = View.VISIBLE
            binding.recyclerSafetyEvents.visibility = View.GONE
        } else {
            binding.tvNoSafetyEvents.visibility = View.GONE
            binding.recyclerSafetyEvents.visibility = View.VISIBLE
        }
    }

    private fun showDeleteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Delete Trip")
            .setMessage("Are you sure you want to delete this trip? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteTrip() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deleteTrip() {
        val trip = tripData ?: return
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("completed_trips")
            .document(trip.tripId)
            .delete()
            .addOnSuccessListener {
                android.widget.Toast.makeText(this, "Trip deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                android.util.Log.w("TripDetails", "Error deleting trip", e)
                showError("Failed to delete trip")
                binding.progressBar.visibility = View.GONE
            }
    }

    private fun shareTrip() {
        val trip = tripData ?: return

        val shareText = """
            🏍️ ${trip.tripTitle}
            📅 ${SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(trip.startTime))}
            ⏰ ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(trip.startTime))} - ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(trip.endTime))}
            ⏱️ Duration: ${trip.duration}
            📍 Distance: ${String.format("%.1f km", trip.distanceKm)}
            🏁 Max Speed: ${String.format("%.1f km/h", trip.maxSpeedKmh)}
            🛡️ Safety Score: ${trip.safetyScore}%
            
            Shared from AUTONIX Rider Safety App 🚀
        """.trimIndent()

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "My ${trip.tripTitle} - AUTONIX")
            type = "text/plain"
        }

        startActivity(Intent.createChooser(shareIntent, "Share Trip"))
    }

    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDetach()
    }

    private fun loadAllSafetyEvents(tripId: String) {
        val events = mutableListOf<SafetyEvent>()

        val drowsinessTask = firestore.collection("completed_trips")
            .document(tripId)
            .collection("drowsinessEvents")
            .get()

        val distractionTask = firestore.collection("completed_trips")
            .document(tripId)
            .collection("distractionEvents")
            .get()

        val helmetTask = firestore.collection("completed_trips")
            .document(tripId)
            .collection("helmetEvents")
            .get() // if you store helmet events separately

        // Combine all tasks
        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(
            drowsinessTask, distractionTask, helmetTask
        ).addOnSuccessListener { results ->
            // results[0] -> drowsiness
            results[0].documents.forEach { doc ->
                events.add(
                    SafetyEvent(
                        id = doc.id,
                        tripId = tripId,
                        type = SafetyEventType.DROWSINESS_DETECTED,
                        description = "Drowsiness detected",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                )
            }

            // results[1] -> distraction
            results[1].documents.forEach { doc ->
                events.add(
                    SafetyEvent(
                        id = doc.id,
                        tripId = tripId,
                        type = SafetyEventType.DISTRACTION_DETECTED,
                        description = "Phone distraction detected",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                )
            }

            // results[2] -> helmet
            results[2].documents.forEach { doc ->
                val detected = doc.getBoolean("helmetDetected") ?: true
                if (!detected) {
                    events.add(
                        SafetyEvent(
                            id = doc.id,
                            tripId = tripId,
                            type = SafetyEventType.HELMET_NOT_DETECTED,
                            description = "Helmet not detected",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    )
                }
            }

            // Pass to RecyclerView
            displaySafetyEvents(events)
        }
    }

}

// ------------------ DATA CLASSES ------------------

data class DetailedTripData(
    val tripTitle: String,
    val tripId: String,
    val startTime: Long,
    val endTime: Long,
    val duration: String,
    val distanceKm: Double,
    val maxSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val safetyScore: Int,
    val drowsinessEvents: Int,
    val distractedEvents: Int,
    val helmetDetected: Boolean,
    val altitudeMaxM: Double,
    val locationPoints: List<LocationPoint>
)

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)