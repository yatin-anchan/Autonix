package com.example.autonix_work_in_progress.models

data class Trip(
    val tripId: String = "",
    val timestamp: Long = 0,
    val duration: String = "",
    val distance: Double = 0.0,
    val avgSpeed: Double = 0.0,
    val topSpeed: Double = 0.0,
    val safetyScore: Int = 100,
    val path: List<LatLngPoint> = emptyList()
)
