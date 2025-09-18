package com.example.autonix_work_in_progress

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var cbTerms: CheckBox
    private lateinit var btnSignUp: Button
    private lateinit var tvTerms: TextView
    private lateinit var tvSignIn: TextView
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupClickListeners()
        setupBackPressedHandler()
    }

    private fun initializeViews() {
        etFullName = findViewById(R.id.et_full_name)
        etEmail = findViewById(R.id.et_email)
        etPhone = findViewById(R.id.et_phone)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        cbTerms = findViewById(R.id.cb_terms)
        btnSignUp = findViewById(R.id.btn_sign_up_submit)
        tvTerms = findViewById(R.id.tv_terms)
        tvSignIn = findViewById(R.id.tv_sign_in)
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
        btnSignUp.setOnClickListener { registerUser() }
        tvTerms.setOnClickListener { openTermsAndConditions() }
        tvSignIn.setOnClickListener { navigateToLogin() }
        btnBack.setOnClickListener { navigateToWelcome() }
    }

    private fun registerUser() {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        if (!validateInput(fullName, email, phone, password, confirmPassword)) return

        showProgress(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(fullName)
                        .build()

                    user?.updateProfile(profileUpdates)?.addOnCompleteListener { profileTask ->
                        if (profileTask.isSuccessful) {
                            val userId = user.uid
                            val username = generateUsername(fullName)

                            // Save user data
                            saveUserDataToFirestore(userId, username, fullName, email, phone)
                            saveUserDataLocally(fullName, email, phone, username)

                            showProgress(false)
                            Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                            navigateToMainActivity()
                        } else {
                            showProgress(false)
                            Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    showProgress(false)
                    handleRegistrationError(task.exception)
                }
            }
    }

    private fun generateUsername(fullName: String): String {
        return fullName.trim().lowercase().replace("\\s+".toRegex(), "_")
    }

    private fun saveUserDataToFirestore(userId: String, username: String, fullName: String, email: String, phone: String) {
        val userData = mapOf(
            "userId" to userId,
            "username" to username,
            "fullName" to fullName,
            "email" to email,
            "phone" to phone,
            "createdAt" to System.currentTimeMillis(),
            "totalTrips" to 0,
            "safetyScore" to 100,
            "isActive" to true
        )

        db.collection("users").document(userId).set(userData)
            .addOnSuccessListener {
                // Create empty emergency contacts collection for this user
                val emptyContact = mapOf(
                    "name" to "",
                    "phone" to "",
                    "relationship" to "",
                    "priority" to 1,
                    "isActive" to false,
                    "createdAt" to System.currentTimeMillis()
                )
                db.collection("emergency_contacts")
                    .document(userId)
                    .collection("contacts")
                    .document("default")
                    .set(emptyContact)
            }

        val usernameData = mapOf(
            "userId" to userId,
            "email" to email,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("usernames").document(username).set(usernameData)
    }

    private fun saveUserDataLocally(fullName: String, email: String, phone: String, username: String) {
        val sharedPref = getSharedPreferences("autonix_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user_name", fullName)
            putString("user_email", email)
            putString("username", username)
            putString("user_phone", phone)
            putBoolean("is_logged_in", true)
            putInt("total_trips", 0)
            putInt("safety_score", 100)
            putLong("registration_date", System.currentTimeMillis())
            apply()
        }
    }

    private fun handleRegistrationError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthUserCollisionException -> "An account with this email already exists."
            is FirebaseAuthWeakPasswordException -> "Password is too weak."
            else -> exception?.message ?: "Registration failed. Please try again."
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        etPassword.setText("")
        etConfirmPassword.setText("")
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun navigateToWelcome() {
        startActivity(Intent(this, WelcomeActivity::class.java))
        finish()
    }

    private fun openTermsAndConditions() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Terms and Conditions")
            .setMessage("By using AUTONIX Rider Safety app, you agree to our terms of service and privacy policy.\n\n" +
                    "• We collect location data for safety monitoring\n" +
                    "• Emergency contacts may be notified in case of incidents\n" +
                    "• Trip data is stored securely\n" +
                    "• You can delete your account anytime\n" +
                    "• Data is processed according to privacy regulations")
            .setPositiveButton("I Agree") { dialog, _ ->
                cbTerms.isChecked = true
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun validateInput(fullName: String, email: String, phone: String, password: String, confirmPassword: String): Boolean {
        var isValid = true
        clearErrors()

        if (fullName.isEmpty() || fullName.length < 2 || !fullName.matches(Regex("^[a-zA-Z\\s]+$"))) {
            etFullName.error = "Enter a valid name"
            isValid = false
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Enter a valid email"
            isValid = false
        }
        if (phone.isEmpty() || !phone.matches(Regex("^[+]?[0-9]{10,15}$"))) {
            etPhone.error = "Enter a valid phone number"
            isValid = false
        }
        if (password.isEmpty() || password.length < 6 || !password.matches(Regex(".*[A-Za-z].*"))) {
            etPassword.error = "Password must be at least 6 chars with letters"
            isValid = false
        }
        if (confirmPassword.isEmpty() || password != confirmPassword) {
            etConfirmPassword.error = "Passwords do not match"
            isValid = false
        }
        if (!cbTerms.isChecked) {
            Toast.makeText(this, "Please accept Terms and Conditions", Toast.LENGTH_LONG).show()
            isValid = false
        }

        if (!isValid) focusFirstErrorField()
        return isValid
    }

    private fun clearErrors() {
        etFullName.error = null
        etEmail.error = null
        etPhone.error = null
        etPassword.error = null
        etConfirmPassword.error = null
    }

    private fun focusFirstErrorField() {
        when {
            etFullName.error != null -> etFullName.requestFocus()
            etEmail.error != null -> etEmail.requestFocus()
            etPhone.error != null -> etPhone.requestFocus()
            etPassword.error != null -> etPassword.requestFocus()
            etConfirmPassword.error != null -> etConfirmPassword.requestFocus()
        }
    }

    private fun showProgress(show: Boolean) {
        btnSignUp.isEnabled = !show
        btnSignUp.text = if (show) "Creating Account..." else "Sign Up"
        setInputFieldsEnabled(!show)
    }

    private fun setInputFieldsEnabled(enabled: Boolean) {
        etFullName.isEnabled = enabled
        etEmail.isEnabled = enabled
        etPhone.isEnabled = enabled
        etPassword.isEnabled = enabled
        etConfirmPassword.isEnabled = enabled
        cbTerms.isEnabled = enabled
        tvTerms.isEnabled = enabled
        tvSignIn.isEnabled = enabled
        btnBack.isEnabled = enabled
    }
}
