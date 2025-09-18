package com.example.autonix_work_in_progress.models

import com.example.autonix_work_in_progress.models.LatLngPoint

data class TripData(
    val tripId: String = "",
    val userId: String = "",
    val startTime: Long = 0,
    val endTime: Long = 0,
    val timestamp: Long = 0,
    val duration: Long = 0, // in milliseconds
    val distance: Double = 0.0, // in kilometers
    val avgSpeed: Double = 0.0, // in km/h
    val topSpeed: Double = 0.0, // in km/h
    val safetyScore: Int = 100, // 0-100 percentage
    val drowsinessEvents: Int = 0, // count of drowsiness events
    val distractionEvents: Int = 0, // count of distraction events
    val helmetDetected: Boolean = true, // whether helmet was worn
    val tripTitle: String = "", // user-defined trip name
    val path: List<LatLngPoint> = emptyList() // trip route coordinates
)