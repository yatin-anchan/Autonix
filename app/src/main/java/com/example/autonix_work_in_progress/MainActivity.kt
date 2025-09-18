package com.example.autonix_work_in_progress

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.autonix_ridersafety.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource


private val FirebaseUser.fullname: String
    get() = displayName ?: ""

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // UI Components
    private lateinit var tvWelcome: TextView
    private lateinit var tvTotalTrips: TextView
    private lateinit var tvSafetyScore: TextView
    private lateinit var startTripCard: LinearLayout
    private lateinit var tripHistoryCard: LinearLayout
    private lateinit var emergencyCard: LinearLayout
    private lateinit var settingsCard: LinearLayout

    // Bottom nav
    private lateinit var icHome: ImageView
    private lateinit var navIcon: ImageView
    private lateinit var sosIcon: ImageView
    private lateinit var profileIcon: ImageView

    // Top bar
    private lateinit var menuIcon: ImageView
    private lateinit var notificationIcon: ImageView

    // Sidebar
    private lateinit var sidebarMenu: LinearLayout
    private lateinit var sidebarOverlay: View
    private lateinit var sidebarUserName: TextView
    private lateinit var sidebarUserEmail: TextView
    private lateinit var mainContent: androidx.constraintlayout.widget.ConstraintLayout

    // Sidebar menu items
    private lateinit var menuProfile: LinearLayout
    private lateinit var menuTripHistory: LinearLayout
    private lateinit var menuEmergency: LinearLayout
    private lateinit var menuSettings: LinearLayout
    private lateinit var menuHelp: LinearLayout
    private lateinit var menuLogout: LinearLayout

    private var isSidebarOpen = false

    //Tracking and sos
    private val SMS_PERMISSION_REQUEST_CODE = 1001
    private val LOCATION_PERMISSION_REQUEST_CODE = 1002

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Flags so we can resume after permission granted
    private var pendingSendEmergency = false
    private var pendingTestPrimary: String? = null
    private var pendingTestSecondary: String? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            redirectToWelcome()
            return
        }

        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        initViews()
        fetchUserDataFromFirestore()
        setupUserData()
        setupClickListeners()
    }

    private fun redirectToWelcome() {
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun getLastLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            // we return null now; caller should handle null fallback or retry
            callback(null)
            return
        }

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    callback(loc)
                } else {
                    // fallback: request a single fresh location
                    val cts = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(
                        com.google.android.gms.location.LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    ).addOnSuccessListener { fresh ->
                        callback(fresh)
                    }.addOnFailureListener {
                        callback(null)
                    }
                }
            }.addOnFailureListener {
                callback(null)
            }
        } catch (e: SecurityException) {
            callback(null)
        }
    }


    private fun initViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        tvTotalTrips = findViewById(R.id.tvTotalTrips)
        tvSafetyScore = findViewById(R.id.tvSafetyScore)
        startTripCard = findViewById(R.id.startTripCard)
        tripHistoryCard = findViewById(R.id.tripHistoryCard)
        emergencyCard = findViewById(R.id.emergencyCard)
        settingsCard = findViewById(R.id.settingsCard)

        icHome = findViewById(R.id.ic_home)
        navIcon = findViewById(R.id.navIcon)
        sosIcon = findViewById(R.id.sosIcon)
        profileIcon = findViewById(R.id.profileIcon)

        menuIcon = findViewById(R.id.menuIcon)
        notificationIcon = findViewById(R.id.notificationIcon)

        sidebarMenu = findViewById(R.id.sidebarMenu)
        sidebarOverlay = findViewById(R.id.sidebarOverlay)
        sidebarUserName = findViewById(R.id.sidebarUserName)
        sidebarUserEmail = findViewById(R.id.sidebarUserEmail)
        mainContent = findViewById(R.id.mainContent)

        menuProfile = findViewById(R.id.menuProfile)
        menuTripHistory = findViewById(R.id.menuTripHistory)
        menuEmergency = findViewById(R.id.menuEmergency)
        menuSettings = findViewById(R.id.menuSettings)
        menuHelp = findViewById(R.id.menuHelp)
        menuLogout = findViewById(R.id.menuLogout)
    }

    /** Fetches user info from Firestore "users" collection and updates UI */
    private fun fetchUserDataFromFirestore() {
        val user = auth.currentUser ?: return
        val userRef = db.collection("users").document(user.uid)

        userRef.get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val fullName = document.getString("fullName") ?: user.fullname
                val email = document.getString("email") ?: user.email

                // Update UI
                tvWelcome.text = "Welcome, $fullName!"
                sidebarUserName.text = fullName
                sidebarUserEmail.text = email

                // Save locally
                val sharedPref = getSharedPreferences("autonix_prefs", MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("user_name", fullName)
                    putString("user_email", email)
                    apply()
                }
            }
        }.addOnFailureListener {
            tvWelcome.text = "Welcome, ${user.fullname}"
            sidebarUserName.text = user.fullname
            sidebarUserEmail.text = user.email
        }
    }

    private fun setupUserData() {
        val user = auth.currentUser ?: return
        db.collection("completed_trips")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                val totalTrips = documents.size()
                val avgSafetyScore = if (totalTrips > 0) {
                    documents.map { it.getLong("safetyScore")?.toInt() ?: 100 }.average()
                } else 100.0

                tvTotalTrips.text = totalTrips.toString()
                tvSafetyScore.text = "${avgSafetyScore.toInt()}%"
            }
            .addOnFailureListener {
                tvTotalTrips.text = "0"
                tvSafetyScore.text = "100%"
            }
    }

    // ===== EMERGENCY CONTACT CHECK =====
    private fun hasEmergencyContacts(callback: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return
        val contactsRef = db.collection("emergency_contacts").document(user.uid)
            .collection("contacts")

        contactsRef.get().addOnSuccessListener { snapshot ->
            // Only count contacts with a non-empty phone
            val validContacts = snapshot.mapNotNull { it.getString("phone") }.filter { it.isNotBlank() }
            callback(validContacts.isNotEmpty())
        }.addOnFailureListener {
            callback(false)
        }
    }


    private fun setupClickListeners() {
        startTripCard.setOnClickListener {
            closeSidebar()
            hasEmergencyContacts { hasContacts ->
                if (!hasContacts) {
                    Toast.makeText(this, "Please add at least one emergency contact to start a trip", Toast.LENGTH_LONG).show()
                    return@hasEmergencyContacts
                }
                startNewTrip()
            }
        }

        tripHistoryCard.setOnClickListener {
            closeSidebar()
            openTripHistory()
        }

        emergencyCard.setOnClickListener {
            closeSidebar()
            openEmergencyContacts()
        }

        settingsCard.setOnClickListener {
            closeSidebar()
            openSettings()
        }

        // Bottom navigation
        icHome.setOnClickListener { closeSidebar() }
        navIcon.setOnClickListener { closeSidebar(); openNavigation() }
        sosIcon.setOnClickListener { closeSidebar(); triggerSOS() }
        profileIcon.setOnClickListener { closeSidebar(); openProfile() }

        // Top bar
        menuIcon.setOnClickListener { toggleSidebar() }
        notificationIcon.setOnClickListener { closeSidebar(); openNotifications() }

        // Sidebar menu items
        menuProfile.setOnClickListener { closeSidebar(); openProfile() }
        menuTripHistory.setOnClickListener { closeSidebar(); openTripHistory() }
        menuEmergency.setOnClickListener { closeSidebar(); openEmergencyContacts() }
        menuSettings.setOnClickListener { closeSidebar(); openSettings() }
        menuHelp.setOnClickListener { closeSidebar(); openHelp() }
        menuLogout.setOnClickListener { performFirebaseLogout() }

        sidebarOverlay.setOnClickListener { closeSidebar() }
    }

    private fun triggerSOS() {
        hasEmergencyContacts { hasContacts ->
            if (!hasContacts) {
                Toast.makeText(this, "Please add at least one emergency contact to use SOS", Toast.LENGTH_LONG).show()
                return@hasEmergencyContacts
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Emergency SOS")
                .setMessage("Are you in an emergency situation?\n\nThis will alert your emergency contacts.")
                .setPositiveButton("YES - SEND ALERT") { _, _ -> sendEmergencyAlert() }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setIcon(R.drawable.ic_sos)
                .show()
        }
    }

    private fun sendEmergencyAlert() {
        val user = auth.currentUser ?: return
        val contactsRef = db.collection("emergency_contacts").document(user.uid)
            .collection("contacts")

        contactsRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                Toast.makeText(this, "No emergency contacts available!", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            val contacts = snapshot.mapNotNull { it.getString("phone") }.filter { it.isNotBlank() }
            if (contacts.isEmpty()) {
                Toast.makeText(this, "Emergency contacts do not have phone numbers!", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            // Get location first, then send
            getLastLocation { location ->
                val smsBody = if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val osmLink = "https://www.google.com/maps?q=$lat,$lon&z=18\n"
                    "EMERGENCY! Please help. My approximate location: $osmLink"
                } else {
                    "EMERGENCY! Please help. Location not available. Please call me."
                }

                // If we have SEND_SMS permission -> send directly; otherwise ask and open composer fallback
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED) {

                    val sent = mutableListOf<String>()
                    val failed = mutableListOf<String>()
                    val smsManager = SmsManager.getDefault()

                    contacts.forEach { phone ->
                        try {
                            val parts = smsManager.divideMessage(smsBody)
                            if (parts.size > 1) {
                                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                            } else {
                                smsManager.sendTextMessage(phone, null, smsBody, null, null)
                            }
                            sent.add(phone)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            failed.add(phone)
                        }
                    }
                    showResultDialog(sent, failed, isEmergency = true)
                } else {
                    // request permission and set pending flag to resume after grant
                    pendingSendEmergency = true
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(android.Manifest.permission.SEND_SMS),
                        SMS_PERMISSION_REQUEST_CODE
                    )

                    // fallback: open SMS composer for each contact so user can manually send
                    contacts.forEach { phone ->
                        val smsUri = Uri.parse("smsto:$phone")
                        val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                            putExtra("sms_body", smsBody)
                        }
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                        }
                    }
                    Toast.makeText(this, "SMS permission requested. Composer opened as fallback.", Toast.LENGTH_LONG).show()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch emergency contacts!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResultDialog(sent: List<String>, failed: List<String>, isEmergency: Boolean) {
        val title = if (isEmergency) "Emergency Alert Results" else "Test Alert Results"
        val message = buildString {
            if (sent.isEmpty()){
            append("Failed: ${if (failed.isEmpty()) "None" else failed.joinToString(", ")}")}
            else{
            append("Sent to: ${if (sent.isEmpty()) "None" else sent.joinToString(", ")}\n\n")}

        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }


    // Feature navigation methods
    private fun startNewTrip() {
        val intent = Intent(this, TripMonitoring::class.java)
        startActivity(intent)
    }

    private fun openTripHistory() {
        val intent = Intent(this, TripHistoryListActivity::class.java)
        startActivity(intent)
    }

    private fun openEmergencyContacts() {
        val intent = Intent( this, EmergencyContactsActivity::class.java)
        startActivity(intent)
    }

    private fun openSettings() {
        Toast.makeText(this, "Opening settings...", Toast.LENGTH_SHORT).show()
        // TODO: Navigate to settings activity
    }

    private fun openNavigation() {
        val intent = Intent(this, NavigationDashboardActivity::class.java)
        startActivity(intent)
    }

    private fun openProfile() {
        val intent = Intent(this, ActivityProfile::class.java)
        startActivity(intent)
    }

    private fun openNotifications() {
        Toast.makeText(this, "No new notifications", Toast.LENGTH_SHORT).show()
        // TODO: Navigate to notifications activity
    }

    private fun openHelp() {
        Toast.makeText(this, "Opening help & support...", Toast.LENGTH_SHORT).show()
        // TODO: Navigate to help activity
    }


    private fun toggleSidebar() {
        if (isSidebarOpen) {
            closeSidebar()
        } else {
            openSidebar()
        }
    }

    private fun openSidebar() {
        if (isSidebarOpen) return

        isSidebarOpen = true

        // Show sidebar and overlay
        sidebarMenu.visibility = View.VISIBLE
        sidebarOverlay.visibility = View.VISIBLE

        // Animate sidebar sliding in from left
        ObjectAnimator.ofFloat(sidebarMenu, "translationX", -280f * resources.displayMetrics.density, 0f).apply {
            duration = 300
            start()
        }

        // Animate overlay fade in
        ObjectAnimator.ofFloat(sidebarOverlay, "alpha", 0f, 1f).apply {
            duration = 300
            start()
        }

        // Animate main content sliding to right
        ObjectAnimator.ofFloat(mainContent, "translationX", 0f, 100f).apply {
            duration = 300
            start()
        }

        // Rotate menu icon
        ObjectAnimator.ofFloat(menuIcon, "rotation", 0f, 90f).apply {
            duration = 300
            start()
        }
    }

    private fun closeSidebar() {
        if (!isSidebarOpen) return

        isSidebarOpen = false

        // Animate sidebar sliding out to left
        ObjectAnimator.ofFloat(sidebarMenu, "translationX", 0f, -280f * resources.displayMetrics.density).apply {
            duration = 300
            start()
        }

        // Animate overlay fade out
        ObjectAnimator.ofFloat(sidebarOverlay, "alpha", 1f, 0f).apply {
            duration = 300
            start()
        }

        // Animate main content back to original position
        ObjectAnimator.ofFloat(mainContent, "translationX", 100f, 0f).apply {
            duration = 300
            start()
        }

        // Rotate menu icon back
        ObjectAnimator.ofFloat(menuIcon, "rotation", 90f, 0f).apply {
            duration = 300
            start()
        }

        // Hide sidebar and overlay after animation
        sidebarMenu.postDelayed({
            if (!isSidebarOpen) {
                sidebarMenu.visibility = View.GONE
                sidebarOverlay.visibility = View.GONE
            }
        }, 300)
    }

    private fun performFirebaseLogout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { _, _ ->
                executeLogout()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun executeLogout() {
        // Sign out from Firebase
        auth.signOut()

        // Clear local session data
        val sharedPref = getSharedPreferences("autonix_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("is_logged_in", false)
            remove("user_name")
            remove("user_email")
            putLong("logout_time", System.currentTimeMillis())
            apply()
        }

        Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()

        // Navigate to welcome screen
        redirectToWelcome()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isSidebarOpen) {
            val sidebarRect = Rect()
            sidebarMenu.getGlobalVisibleRect(sidebarRect)

            // If tap is outside the sidebar, close it
            if (!sidebarRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                closeSidebar()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // NO MORE AUTH STATE LISTENERS OR CONFLICTING CHECKS
    // Only refresh data when resuming, don't check auth again
    override fun onResume() {
        super.onResume()
        setupUserData()
    }

    // Utility methods
    fun refreshUserData() {
        setupUserData()
    }

    fun updateTripCount(increment: Boolean = true) {
        val sharedPref = getSharedPreferences("autonix_prefs", MODE_PRIVATE)
        val currentTrips = sharedPref.getInt("total_trips", 0)
        val newCount = if (increment) currentTrips + 1 else maxOf(0, currentTrips - 1)

        with(sharedPref.edit()) {
            putInt("total_trips", newCount)
            apply()
        }

        tvTotalTrips.text = newCount.toString()
    }

    fun updateSafetyScore(score: Int) {
        val sharedPref = getSharedPreferences("autonix_prefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("safety_score", score)
            apply()
        }

        tvSafetyScore.text = "$score%"
    }
}