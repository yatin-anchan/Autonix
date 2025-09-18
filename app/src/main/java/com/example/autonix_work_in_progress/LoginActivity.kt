package com.example.autonix_work_in_progress

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // UI Elements
    private lateinit var etUsernameOrEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var cbRememberMe: CheckBox
    private lateinit var btnSignIn: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvSignUp: TextView
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase
        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupClickListeners()
        setupBackPressedHandler()
        loadSavedCredentials()
    }

    private fun initializeViews() {
        etUsernameOrEmail = findViewById(R.id.et_username_or_email) // Updated ID
        etPassword = findViewById(R.id.et_password)
        cbRememberMe = findViewById(R.id.cb_remember_me)
        btnSignIn = findViewById(R.id.btn_sign_in_submit)
        tvForgotPassword = findViewById(R.id.tv_forgot_password)
        tvSignUp = findViewById(R.id.tv_sign_up)
        btnBack = findViewById(R.id.btn_back)
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToWelcome()
            }
        })
    }

    private fun setupClickListeners() {
        btnSignIn.setOnClickListener {
            signInUser()
        }

        tvForgotPassword.setOnClickListener {
            forgotPassword()
        }

        tvSignUp.setOnClickListener {
            navigateToRegister()
        }

        btnBack.setOnClickListener {
            navigateToWelcome()
        }
    }

    private fun loadSavedCredentials() {
        val sharedPref = getSharedPreferences("autonix_prefs", MODE_PRIVATE)
        val rememberMe = sharedPref.getBoolean("remember_me", false)
        val savedCredential = sharedPref.getString("saved_credential", "")

        if (rememberMe && !savedCredential.isNullOrEmpty()) {
            etUsernameOrEmail.setText(savedCredential)
            cbRememberMe.isChecked = true
        }
    }

    private fun signInUser() {
        val input = etUsernameOrEmail.text.toString().trim()

        if (!validateInput(input, etPassword.text.toString().trim())) return

        showProgress(true)

        if (isEmail(input)) {
            performFirebaseLogin(input, etPassword.text.toString().trim(), input)
        } else {
            val usernameLower = input.lowercase()
            convertUsernameToEmail(usernameLower) { email ->
                if (email != null) {
                    performFirebaseLogin(email, etPassword.text.toString().trim(), input)
                } else {
                    showProgress(false)
                    Toast.makeText(this, "Username not found. Please check your username or create an account.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun isEmail(input: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(input).matches()
    }

    private fun convertUsernameToEmail(username: String, callback: (String?) -> Unit) {
        db.collection("usernames")
            .document(username)
            .get()
            .addOnSuccessListener { document ->
                callback(document.getString("email"))
            }
            .addOnFailureListener { callback(null) }
    }

    private fun performFirebaseLogin(email: String, password: String, originalInput: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                showProgress(false)

                if (task.isSuccessful) {
                    val user = auth.currentUser

                    // Handle remember me functionality
                    handleRememberMe(originalInput)

                    // Load and save user data locally
                    loadUserDataFromFirestore(user?.uid ?: "") { userData ->
                        if (userData != null) {
                            saveUserDataLocally(userData)
                            Toast.makeText(this, "Welcome back, ${userData["fullName"]}!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Fallback to basic data
                            saveUserDataLocally(mapOf(
                                "fullName" to (user?.displayName ?: "Driver"),
                                "email" to email,
                                "username" to if (isEmail(originalInput)) "" else originalInput
                            ))
                            Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                        }
                        navigateToMainActivity()
                    }
                } else {
                    handleLoginError(task.exception)
                }
            }
    }

    private fun loadUserDataFromFirestore(userId: String, callback: (Map<String, Any>?) -> Unit) {
        if (userId.isEmpty()) {
            callback(null)
            return
        }

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    callback(document.data)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    private fun handleRememberMe(credential: String) {
        val sharedPref = getSharedPreferences("autonix_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("remember_me", cbRememberMe.isChecked)
            if (cbRememberMe.isChecked) {
                putString("saved_credential", credential)
            } else {
                remove("saved_credential")
            }
            apply()
        }
    }

    private fun saveUserDataLocally(userData: Map<String, Any>) {
        val sharedPref = getSharedPreferences("autonix_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user_name", userData["fullName"] as? String ?: "Driver")
            putString("user_email", userData["email"] as? String ?: "")
            putString("username", userData["username"] as? String ?: "")
            putString("user_phone", userData["phone"] as? String ?: "")
            putInt("total_trips", (userData["totalTrips"] as? Long)?.toInt() ?: 0)
            putInt("safety_score", (userData["safetyScore"] as? Long)?.toInt() ?: 100)
            putBoolean("is_logged_in", true)
            putLong("last_login", System.currentTimeMillis())
            apply()
        }
    }

    private fun handleLoginError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthInvalidUserException -> {
                "No account found with this credential. Please check your username/email or create a new account."
            }
            is FirebaseAuthInvalidCredentialsException -> {
                "Invalid username/email or password. Please check your credentials and try again."
            }
            else -> {
                exception?.message ?: "Sign in failed. Please try again."
            }
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()

        // Clear password field on error for security
        etPassword.setText("")
    }

    private fun forgotPassword() {
        val input = etUsernameOrEmail.text.toString().trim()

        if (input.isEmpty()) {
            etUsernameOrEmail.error = "Please enter your username or email first"
            etUsernameOrEmail.requestFocus()
            return
        }

        if (isEmail(input)) {
            // Direct email - proceed with password reset
            showPasswordResetDialog(input)
        } else {
            // Username - convert to email first
            convertUsernameToEmail(input.lowercase()) { email ->
                if (email != null) {
                    showPasswordResetDialog(email)
                } else {
                    Toast.makeText(this, "Username not found. Please enter a valid username or email.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showPasswordResetDialog(email: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("Send password reset email to:\n$email")
            .setPositiveButton("Send") { _, _ ->
                sendPasswordResetEmail(email)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun sendPasswordResetEmail(email: String) {
        showProgress(true)

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                showProgress(false)

                if (task.isSuccessful) {
                    Toast.makeText(this, "Password reset email sent to $email\nCheck your inbox.", Toast.LENGTH_LONG).show()
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("user") == true -> {
                            "No account found with this email address."
                        }
                        else -> task.exception?.message ?: "Failed to send reset email"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun validateInput(usernameOrEmail: String, password: String): Boolean {
        var isValid = true

        // Clear previous errors
        etUsernameOrEmail.error = null
        etPassword.error = null

        // Username/Email validation
        when {
            usernameOrEmail.isEmpty() -> {
                etUsernameOrEmail.error = "Username or email is required"
                etUsernameOrEmail.requestFocus()
                isValid = false
            }
            !isEmail(usernameOrEmail) && usernameOrEmail.length < 3 -> {
                etUsernameOrEmail.error = "Username must be at least 3 characters"
                etUsernameOrEmail.requestFocus()
                isValid = false
            }
            !isEmail(usernameOrEmail) && !usernameOrEmail.matches(Regex("^[a-z0-9_]+$")) -> {
                etUsernameOrEmail.error = "Invalid username format"
                etUsernameOrEmail.requestFocus()
                isValid = false
            }
        }

        // Password validation
        when {
            password.isEmpty() -> {
                etPassword.error = "Password is required"
                if (isValid) etPassword.requestFocus()
                isValid = false
            }
            password.length < 6 -> {
                etPassword.error = "Password must be at least 6 characters"
                if (isValid) etPassword.requestFocus()
                isValid = false
            }
        }

        return isValid
    }

    private fun showProgress(show: Boolean) {
        btnSignIn.isEnabled = !show

        if (show) {
            btnSignIn.text = "Signing In..."
            setInputFieldsEnabled(false)
        } else {
            btnSignIn.text = "Sign In"
            setInputFieldsEnabled(true)
        }
    }

    private fun setInputFieldsEnabled(enabled: Boolean) {
        etUsernameOrEmail.isEnabled = enabled
        etPassword.isEnabled = enabled
        cbRememberMe.isEnabled = enabled
        tvForgotPassword.isEnabled = enabled
        tvSignUp.isEnabled = enabled
        btnBack.isEnabled = enabled
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToRegister() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToWelcome() {
        val intent = Intent(this, WelcomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}