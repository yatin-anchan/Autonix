package com.example.autonix_work_in_progress

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.autonix_work_in_progress.models.SafetyEvent
import com.example.autonix_work_in_progress.models.SafetyEventType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.util.*

class DrowsinessDetectionService : Service() {

    private var running = false
    private var paused = false
    private val random = Random()
    private var lastEventTime: Long = 0L
    private val cooldownMs = 60_000 // 1 min cooldown between alerts
    private var currentTripId: String? = null
    private var detectionThread: Thread? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "DrowsinessDetectionService"
        private const val CHANNEL_ID = "drowsiness_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.getStringExtra("action")}")

        when (intent?.getStringExtra("action")) {
            "start" -> {
                currentTripId = intent.getStringExtra("tripId")
                Log.d(TAG, "Starting detection for trip: $currentTripId")
                startDetection()
            }
            "pause" -> {
                Log.d(TAG, "Pausing detection")
                paused = true
            }
            "resume" -> {
                Log.d(TAG, "Resuming detection")
                paused = false
            }
            "stop" -> {
                Log.d(TAG, "Stopping detection")
                stopDetection()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopDetection()
        super.onDestroy()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Drowsiness Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors driver drowsiness during trips"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Safety Monitoring Active")
            .setContentText("Drowsiness detection is running")
            .setSmallIcon(R.drawable.ic_car)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startDetection() {
        if (running) {
            Log.d(TAG, "Detection already running")
            return
        }

        if (currentTripId == null) {
            Log.w(TAG, "Cannot start detection without trip ID")
            return
        }

        running = true
        paused = false

        detectionThread = Thread {
            Log.d(TAG, "Detection thread started")
            while (running) {
                try {
                    SystemClock.sleep(15_000) // Check every 15 seconds

                    if (!paused && running) {
                        // Simulate drowsiness detection (replace with actual ML model)
                        val detected = random.nextFloat() < 0.5f // 10% chance every 15 seconds
                        val now = System.currentTimeMillis()

                        if (detected && (now - lastEventTime) > cooldownMs) {
                            lastEventTime = now
                            Log.d(TAG, "Drowsiness detected!")
                            sendDrowsinessEvent("Driver drowsiness detected - please take a break")
                        }
                    }
                } catch (e: InterruptedException) {
                    Log.d(TAG, "Detection thread interrupted")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in detection thread", e)
                }
            }
            Log.d(TAG, "Detection thread ended")
        }

        detectionThread?.start()
    }

    private fun stopDetection() {
        running = false
        detectionThread?.let { thread ->
            thread.interrupt()
            try {
                thread.join(1000) // Wait up to 1 second for thread to finish
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while waiting for detection thread to finish")
            }
        }
        detectionThread = null
    }

    private fun sendDrowsinessEvent(description: String) {
        val userId = auth.currentUser?.uid
        val tripId = currentTripId

        if (userId == null || tripId == null) {
            Log.w(TAG, "Cannot send drowsiness event - missing user ID or trip ID")
            return
        }

        val eventId = UUID.randomUUID().toString()

        // Get last known GPS location
        val location = getLastKnownLocation()
        val lat = location?.latitude ?: 0.0
        val lng = location?.longitude ?: 0.0

        // Create safety event data
        val eventData = mapOf(
            "id" to eventId,
            "tripId" to tripId,
            "type" to SafetyEventType.DROWSINESS_DETECTED.name,
            "description" to description,
            "timestamp" to System.currentTimeMillis(),
            "latitude" to lat,
            "longitude" to lng
        )

        // Save to Firestore under the trip's safety events subcollection
        firestore.collection("completed_trips")
            .document(tripId)
            .collection("safetyEvents")
            .document(eventId)
            .set(eventData)
            .addOnSuccessListener {
                Log.d(TAG, "Drowsiness event saved to Firestore: $eventId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save drowsiness event to Firestore", e)
            }

        // Broadcast to TripMonitoring activity
        val broadcastIntent = Intent("DROWSINESS_EVENT").apply {
            putExtra("desc", description)
            putExtra("latitude", lat)
            putExtra("longitude", lng)
            putExtra("eventId", eventId)
            putExtra("timestamp", System.currentTimeMillis())
        }

        sendBroadcast(broadcastIntent)
        Log.d(TAG, "Drowsiness event broadcast sent")
    }

    private fun getLastKnownLocation(): android.location.Location? {
        return try {
            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

            // Try GPS first, then Network
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            // Return the more recent location
            when {
                gpsLocation != null && networkLocation != null -> {
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                }
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
            null
        }
    }
}