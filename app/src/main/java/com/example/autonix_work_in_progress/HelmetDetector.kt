package com.example.autonix_work_in_progress

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class HelmetDetector(context: Context) {

    private val detector: ObjectDetector

    init {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(1)
            .setScoreThreshold(0.5f) // confidence threshold
            .build()

        detector = ObjectDetector.createFromFileAndOptions(
            context,
            "helmet_detector.tflite",
            options
        )
    }

    fun detectHelmet(bitmap: Bitmap): Boolean {
        val image = TensorImage.fromBitmap(bitmap)
        val results: List<Detection> = detector.detect(image)

        for (result in results) {
            val category = result.categories.firstOrNull()
            if (category != null) {
                if (category.label.equals("helmet", ignoreCase = true) &&
                    category.score >= 0.5f
                ) {
                    return true
                }
            }
        }
        return false
    }
}