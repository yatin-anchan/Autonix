package com.example.autonix_work_in_progress

import android.graphics.PointF
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Helper that implements EAR-based eye-closure, yawn ratio and head-nod detection logic.
 * - It expects ML Kit Face objects (from camera frames). It DOES NOT use ML Kit's eye-open probability.
 * - It emits callbacks via DrowsinessListener when a condition is met.
 */
class DrowsinessDetector(
    private val listener: DrowsinessListener
) {
    companion object {
        private const val TAG = "DrowsinessDetector"
    }

    interface DrowsinessListener {
        fun onEyesClosedDetected(closedMs: Long, description: String)
        fun onYawnDetected(ratio: Float, description: String)
        fun onHeadNodDetected(angleX: Float, angleZ: Float, description: String)
        fun onNormal() // called when face present and no condition
    }

    // --- Tunable thresholds ---
    private val EAR_THRESHOLD = 0.20f               // Eye Aspect Ratio below -> eye considered closed
    private val CLOSED_TIME_THRESHOLD_MS = 1500L    // how long eyes must remain closed to trigger event
    private val YAWN_RATIO_THRESHOLD = 0.55f        // vertical/horizontal mouth ratio for a yawn
    private val HEAD_NOD_ANGLE_THRESHOLD_X = 18f    // head nod (pitch) threshold degrees (x-axis)
    private val HEAD_TILT_ANGLE_THRESHOLD_Z = 20f   // head tilt threshold degrees (z-axis)

    // state
    private var eyesClosedSince: Long = -1L
    private var isAlerting = false

    /**
     * Call this with each ML Kit Face detected (first face if multiple)
     * This method computes EAR, mouth ratio and evaluates head pose.
     */
    fun processFace(face: com.google.mlkit.vision.face.Face) {
        val now = SystemClock.elapsedRealtime()

        // ------------- EAR (eye aspect ratio) -------------
        val leftEyeContour = face.getContour(com.google.mlkit.vision.face.FaceContour.LEFT_EYE)?.points
        val rightEyeContour = face.getContour(com.google.mlkit.vision.face.FaceContour.RIGHT_EYE)?.points

        val leftEAR = if (!leftEyeContour.isNullOrEmpty()) computeEARFromContour(leftEyeContour) else -1f
        val rightEAR = if (!rightEyeContour.isNullOrEmpty()) computeEARFromContour(rightEyeContour) else -1f

        val avgEAR = when {
            leftEAR > 0 && rightEAR > 0 -> (leftEAR + rightEAR) / 2f
            leftEAR > 0 -> leftEAR
            rightEAR > 0 -> rightEAR
            else -> -1f
        }

        // ------------- Mouth ratio (yawn) -------------
        val upperLipTop = face.getContour(com.google.mlkit.vision.face.FaceContour.UPPER_LIP_TOP)?.points
        val lowerLipBottom = face.getContour(com.google.mlkit.vision.face.FaceContour.LOWER_LIP_BOTTOM)?.points
        val mouthRatio = if (!upperLipTop.isNullOrEmpty() && !lowerLipBottom.isNullOrEmpty()) {
            computeMouthRatio(upperLipTop, lowerLipBottom)
        } else 0f

        // ------------- Head pose (use ML Kit provided angles) -------------
        val headX = face.headEulerAngleX // pitch (nodding)
        val headY = face.headEulerAngleY // yaw (left/right)
        val headZ = face.headEulerAngleZ // roll (tilt)

        // ---------- Evaluate eye closure using EAR ----------
        if (avgEAR > 0f && avgEAR < EAR_THRESHOLD) {
            if (eyesClosedSince < 0L) eyesClosedSince = now
            val closedFor = now - eyesClosedSince
            if (closedFor >= CLOSED_TIME_THRESHOLD_MS && !isAlerting) {
                isAlerting = true
                listener.onEyesClosedDetected(closedFor, "Eyes closed for ${closedFor}ms (EAR=${"%.2f".format(avgEAR)})")
            }
        } else {
            // eyes open
            if (isAlerting) {
                // clear alert state once reopened
                isAlerting = false
            }
            eyesClosedSince = -1L
        }

        // ---------- Evaluate yawn ----------
        if (mouthRatio > YAWN_RATIO_THRESHOLD) {
            listener.onYawnDetected(mouthRatio, "Yawn detected (ratio=${"%.2f".format(mouthRatio)})")
        }

        // ---------- Head nod/tilt detection ----------
        if (abs(headX) > HEAD_NOD_ANGLE_THRESHOLD_X || abs(headZ) > HEAD_TILT_ANGLE_THRESHOLD_Z) {
            listener.onHeadNodDetected(headX, headZ, "Head nod/tilt detected (X=${"%.1f".format(headX)}°, Z=${"%.1f".format(headZ)}°)")
        }

        // If none triggered and face present, inform normal
        if (!isAlerting && !(mouthRatio > YAWN_RATIO_THRESHOLD) &&
            abs(headX) <= HEAD_NOD_ANGLE_THRESHOLD_X && abs(headZ) <= HEAD_TILT_ANGLE_THRESHOLD_Z) {
            listener.onNormal()
        }

        Log.d(TAG, "EAR L=${"%.2f".format(leftEAR)} R=${"%.2f".format(rightEAR)} avg=${"%.2f".format(if (avgEAR>0) avgEAR else 0f)} mouthRatio=${"%.2f".format(mouthRatio)} headX=${"%.1f".format(headX)} headZ=${"%.1f".format(headZ)}")
    }

    // -------------------------
    // Utility math / heuristics
    // -------------------------
    private fun computeEARFromContour(eye: List<PointF>): Float {
        // ML Kit eye contour contains many points around the eye; strategy:
        // - find left-most and right-most points for width (horizontal)
        // - estimate top average and bottom average vertical positions for height (vertical)
        if (eye.size < 4) return -1f

        // leftmost and rightmost by x
        val left = eye.minByOrNull { it.x }!!
        val right = eye.maxByOrNull { it.x }!!

        // top average (points with y < median y)
        val sortedByY = eye.sortedBy { it.y }
        val topAvg = averagePoint(sortedByY.take(eye.size / 3))
        val bottomAvg = averagePoint(sortedByY.takeLast(eye.size / 3))

        val vert = distance(topAvg.x, topAvg.y, bottomAvg.x, bottomAvg.y)
        val hor = distance(left.x, left.y, right.x, right.y)
        if (hor <= 0f) return -1f
        return (vert / hor)
    }

    private fun computeMouthRatio(upper: List<PointF>, lower: List<PointF>): Float {
        val top = averagePoint(upper)
        val bottom = averagePoint(lower)
        // width estimate: find leftmost & rightmost from combined lower points (often lower lip bottom contains full width)
        val combined = upper + lower
        val left = combined.minByOrNull { it.x } ?: top
        val right = combined.maxByOrNull { it.x } ?: bottom
        val vert = distance(top.x, top.y, bottom.x, bottom.y)
        val hor = distance(left.x, left.y, right.x, right.y)
        if (hor <= 0f) return 0f
        return (vert / hor)
    }

    private fun averagePoint(points: List<PointF>): PointF {
        if (points.isEmpty()) return PointF(0f, 0f)
        var sx = 0f
        var sy = 0f
        for (p in points) { sx += p.x; sy += p.y }
        return PointF(sx / points.size, sy / points.size)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
    }
}
