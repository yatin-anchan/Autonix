package com.example.autonix_work_in_progress

data class FleetDriver(
    val userId: String,
    val username: String,
    val fullName: String,
    val status: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Int,
    val bearing: Double,
    val lastUpdate: Long
)