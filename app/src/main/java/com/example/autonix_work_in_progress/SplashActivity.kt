package com.example.autonix_work_in_progress

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val SPLASH_DELAY = 2500L // 2.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide action bar and status bar for full screen splash
        supportActionBar?.hide()

        // Delay then check if user is logged in
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatus()
        }, SPLASH_DELAY)
    }

    private fun checkUserStatus() {
        val sharedPref = getSharedPreferences("autonix_prefs", MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)

        val intent = if (isLoggedIn) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, WelcomeActivity::class.java)
        }

        startActivity(intent)
        finish()
    }
}