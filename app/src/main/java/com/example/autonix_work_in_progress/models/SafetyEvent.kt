package com.example.autonix_work_in_progress.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class SafetyEventType {
    DROWSINESS_DETECTED,
    HELMET_NOT_DETECTED,
    DISTRACTION_DETECTED,
    SPEEDING,
    HARD_BRAKING,
    SHARP_TURN
}

@Parcelize
data class SafetyEvent(
    val id: String = "",
    val tripId: String = "",
    val type: SafetyEventType = SafetyEventType.DROWSINESS_DETECTED,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val severity: String = "low"
) : Parcelable
