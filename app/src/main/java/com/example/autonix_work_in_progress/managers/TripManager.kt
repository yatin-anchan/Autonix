package com.example.autonix_work_in_progress.managers

import com.example.autonix_work_in_progress.models.LatLngPoint
import com.example.autonix_work_in_progress.models.TripData
import org.osmdroid.util.GeoPoint
import java.util.*

class TripManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: TripManager? = null

        fun getInstance(): TripManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TripManager().also { INSTANCE = it }
            }
        }
    }

    private var currentTrip: TripData? = null
    private val tripHistory = mutableListOf<TripData>()
    private var isRecording = false
    private var tripStartTime: Long = 0L

    /** Start a new trip */
    fun startTrip(): String {
        val tripId = UUID.randomUUID().toString()
        tripStartTime = System.currentTimeMillis()
        currentTrip = TripData(
            tripId = tripId,
            timestamp = tripStartTime,
            duration = 0L,
            distance = 0.0,
            avgSpeed = 0.0,
            topSpeed = 0.0,
            safetyScore = 100,
            path = emptyList()
        )
        isRecording = true
        return tripId
    }

    /** Update trip info with distance, speed, safety score, and path points */
    fun updateTrip(
        distance: Double = 0.0,
        avgSpeed: Double = 0.0,
        topSpeed: Double = 0.0,
        safetyScore: Int = 100,
        newPathPoint: GeoPoint? = null
    ) {
        currentTrip?.let { trip ->
            val updatedPath = if (newPathPoint != null) {
                trip.path + LatLngPoint(newPathPoint.latitude, newPathPoint.longitude)
            } else {
                trip.path
            }

            currentTrip = trip.copy(
                distance = distance,
                avgSpeed = avgSpeed,
                topSpeed = topSpeed,
                safetyScore = safetyScore,
                path = updatedPath
            )
        }
    }

    /** End the current trip and return it */
    fun endTrip(): TripData? {
        return currentTrip?.let { trip ->
            val durationMs = System.currentTimeMillis() - tripStartTime
            val endedTrip = trip.copy(duration = durationMs)
            tripHistory.add(endedTrip)
            currentTrip = null
            isRecording = false
            endedTrip
        }
    }

    /** Get current trip */
    fun getCurrentTrip(): TripData? = currentTrip

    /** Check if recording */
    fun isRecording(): Boolean = isRecording

    /** Get all recorded trips */
    fun getTripHistory(): List<TripData> = tripHistory.toList()

    /** Clear trip history */
    fun clearHistory() {
        tripHistory.clear()
    }

    /** Calculate total stats: total distance, total duration, average safety score */
    fun getTotalStats(): Triple<Double, Long, Double> {
        val totalDistance = tripHistory.sumOf { it.distance }
        val totalDuration = tripHistory.sumOf { it.duration }  // now Long
        val avgSafetyScore = if (tripHistory.isNotEmpty()) {
            tripHistory.map { it.safetyScore }.average()
        } else {
            100.0
        }

        return Triple(totalDistance, totalDuration, avgSafetyScore)
    }
}
