package com.example.autonix_work_in_progress

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CalibrateActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvHelmetWarning: TextView
    private lateinit var btnStartAnyway: Button
    private lateinit var cameraExecutor: ExecutorService

    private var helmetDetected = false
    private var isProcessing = false
    private var consecutiveDetections = 0

    private lateinit var helmetDetector: HelmetDetector
    private val requiredConsecutiveDetections = 3 // for stable detection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.calibrate)

        previewView = findViewById(R.id.previewView)
        tvHelmetWarning = findViewById(R.id.tvHelmetWarning)
        btnStartAnyway = findViewById(R.id.btnStartAnyway)

        cameraExecutor = Executors.newSingleThreadExecutor()
        helmetDetector = HelmetDetector(this) // load model

        updateHelmetWarning()
        startCamera()

        btnStartAnyway.setOnClickListener {
            startTripMonitoring()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(previewView.display.rotation)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (!isProcessing) {
                                processImage(imageProxy)
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e("CalibrateActivity", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        isProcessing = true
        try {
            val bitmap = imageProxy.toBitmap()
            if (bitmap != null) {
                val detected = helmetDetector.detectHelmet(bitmap)
                updateHelmetStatus(detected)
            }
        } catch (e: Exception) {
            Log.e("CalibrateActivity", "Image processing failed", e)
        } finally {
            imageProxy.close()
            isProcessing = false
        }
    }

    private fun updateHelmetStatus(detected: Boolean) {
        if (detected) {
            consecutiveDetections++
            if (consecutiveDetections >= requiredConsecutiveDetections) {
                helmetDetected = true
            }
        } else {
            consecutiveDetections = 0
            helmetDetected = false
        }

        runOnUiThread {
            Log.d("HelmetCheck", "Detected=$helmetDetected")
            Toast.makeText(
                this,
                if (helmetDetected) "Helmet detected" else "No helmet",
                Toast.LENGTH_SHORT
            ).show()
            updateHelmetWarning()
        }
    }

    private fun updateHelmetWarning() {
        if (helmetDetected) {
            tvHelmetWarning.text = "✓ Helmet Detected"
            tvHelmetWarning.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnStartAnyway.text = "Start Trip"
        } else {
            tvHelmetWarning.text = "⚠ No Helmet Detected"
            tvHelmetWarning.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnStartAnyway.text = "Start Anyway"
        }
    }

    private fun startTripMonitoring() {
        val intent = Intent(this, TripMonitoring::class.java)
        intent.putExtra("helmetDetected", helmetDetected)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // Convert ImageProxy → Bitmap
    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 75, out)
            val imageBytes = out.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("ImageProxy", "toBitmap failed", e)
            null
        }
    }
}
