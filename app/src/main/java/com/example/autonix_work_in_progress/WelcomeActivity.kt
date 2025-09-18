package com.example.autonix_work_in_progress

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        setupButtons()
    }

    private fun setupButtons() {
        val btnCreateAccount = findViewById<Button>(R.id.btn_create_account)
        val btnSignIn = findViewById<Button>(R.id.btn_sign_in)

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnSignIn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}