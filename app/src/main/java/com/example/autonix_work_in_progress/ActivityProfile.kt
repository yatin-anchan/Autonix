package com.example.autonix_work_in_progress

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.autonix_work_in_progress.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.example.autonix_work_in_progress.R

class ActivityProfile : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var binding: ActivityProfileBinding

    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase init
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupUI()
        loadUserProfile()
    }

    override fun onResume() {
        super.onResume()
        // Reload profile when returning from edit activity
        loadUserProfile()
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Edit Profile button (matching XML id)
        binding.editProfile.setOnClickListener {
            startActivity(Intent(this, ActivityProfileEdit::class.java))
        }

        // Emergency Call button
        binding.btnCallEmergency.setOnClickListener {
            callEmergencyContact()
        }

        // Profile picture edit button
        binding.btnEditProfilePic.setOnClickListener {
            startActivity(Intent(this, ActivityProfileEdit::class.java))
        }
    }

    private fun callEmergencyContact() {
        val emergencyText = binding.tvEmergencyContact.text.toString()

        // Extract phone number from text like "Emergency Contact: +91 9876543210"
        val phone = emergencyText.replace("Emergency Contact: ", "").trim()

        if (phone.isNotEmpty() && phone != "N/A") {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to make call", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No emergency contact available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Show loading state
        showLoadingState(true)

        // Load main user profile
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    populateUserProfile(document.data ?: emptyMap())
                } else {
                    showProfileNotFoundDialog()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading profile", e)
                showFallbackProfile()
            }

        // Load emergency contacts separately
        db.collection("emergency_contacts")
            .document(currentUser.uid)
            .collection("contacts")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val primary = snapshot.documents.find { it.id == "primary" }?.getString("phone") ?: "N/A"
                    val secondary = snapshot.documents.find { it.id == "secondary" }?.getString("phone") ?: "N/A"

                    binding.tvEmergencyContact.text = "Emergency Contact: $primary"
                    binding.tvSecondaryEmergencyContact.text = "Secondary Contact: $secondary"
                } else {
                    binding.tvEmergencyContact.text = "Emergency Contact: Not set"
                    binding.tvSecondaryEmergencyContact.text = "Secondary Contact: Not set"
                }
            }
            .addOnFailureListener {
                binding.tvEmergencyContact.text = "Emergency Contact: Not available"
                binding.tvSecondaryEmergencyContact.text = "Secondary Contact: Not available"
            }
    }


    private fun populateUserProfile(userData: Map<String, Any>) {
        // Personal Details
        binding.tvFullName.text = userData["fullName"] as? String ?: "Name not set"

        // Format DOB with age calculation
        formatAndDisplayDOB(userData["dateOfBirth"] as? Long)

        binding.tvLocation.text = userData["location"] as? String ?: "Location not set"

        // Medical Details
        val bloodGroup = userData["bloodGroup"] as? String
        binding.tvBloodGroup.text = if (!bloodGroup.isNullOrEmpty()) {
            "Blood Group: $bloodGroup"
        } else {
            "Blood Group: Not specified"
        }

        // Emergency Contacts
        val emergencyContact = userData["emergencyContact"] as? String
        binding.tvEmergencyContact.text = if (!emergencyContact.isNullOrEmpty()) {
            "Emergency Contact: +91 $emergencyContact"
        } else {
            "Emergency Contact: Not set"
        }

        val secondaryContact = userData["secondaryEmergencyContact"] as? String
        binding.tvSecondaryEmergencyContact.text = if (!secondaryContact.isNullOrEmpty()) {
            "Secondary Contact: +91 $secondaryContact"
        } else {
            "Secondary Contact: Not set"
        }

        // Allergies
        val allergies = userData["allergies"] as? String
        binding.tvAllergies.text = if (!allergies.isNullOrEmpty()) {
            allergies
        } else {
            "None"
        }

        // Profile Picture
        loadProfilePicture(userData["profilePicUrl"] as? String)

        // Additional user info (for logging/debugging)
        logUserStats(userData)
    }

    private fun formatAndDisplayDOB(dobTimestamp: Long?) {
        if (dobTimestamp != null) {
            try {
                val dobDate = Date(dobTimestamp)
                val formattedDate = dateFormatter.format(dobDate)
                val age = calculateAge(dobDate)
                binding.tvDOB.text = "DOB: $formattedDate ($age years)"
            } catch (e: Exception) {
                binding.tvDOB.text = "DOB: Invalid date"
                Log.e("ActivityProfile", "Error formatting DOB", e)
            }
        } else {
            binding.tvDOB.text = "DOB: Not set"
        }
    }

    private fun calculateAge(birthDate: Date): Int {
        val birth = Calendar.getInstance()
        birth.time = birthDate

        val today = Calendar.getInstance()

        var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)

        if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
            age--
        }

        return maxOf(age, 0) // Ensure age is not negative
    }

    private fun loadProfilePicture(profileUrl: String?) {
        if (!profileUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(profileUrl)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .skipMemoryCache(true)          // Force reload from URL
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(binding.ivProfilePic)
        } else {
            binding.ivProfilePic.setImageResource(R.drawable.ic_profile)
        }
    }


    private fun logUserStats(userData: Map<String, Any>) {
        // Log additional user statistics for debugging
        val totalTrips = userData["totalTrips"] as? Number
        val safetyScore = userData["safetyScore"] as? Number
        val isActive = userData["isActive"] as? Boolean

        Log.d("ActivityProfile", "User Stats - Trips: $totalTrips, Safety Score: $safetyScore, Active: $isActive")
    }

    private fun showLoadingState(isLoading: Boolean) {
        if (isLoading) {
            // You could show a progress bar or loading indicator here
            binding.tvFullName.text = "Loading..."
            binding.tvDOB.text = "Loading..."
            binding.tvLocation.text = "Loading..."
            binding.tvBloodGroup.text = "Loading..."
            binding.tvEmergencyContact.text = "Loading..."
            binding.tvSecondaryEmergencyContact.text = "Loading..."
            binding.tvAllergies.text = "Loading..."
        }
    }

    private fun showFallbackProfile() {
        // Show fallback data when profile fails to load
        val currentUser = auth.currentUser

        binding.tvFullName.text = currentUser?.displayName ?: "Profile Error"
        binding.tvDOB.text = "DOB: Unable to load"
        binding.tvLocation.text = "Location: Unable to load"
        binding.tvBloodGroup.text = "Blood Group: Unable to load"
        binding.tvEmergencyContact.text = "Emergency Contact: Unable to load"
        binding.tvSecondaryEmergencyContact.text = "Secondary Contact: Unable to load"
        binding.tvAllergies.text = "Unable to load"

        // Show default profile picture

        binding.ivProfilePic.setImageResource(R.drawable.ic_profile)
    }

    private fun showProfileNotFoundDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Profile Not Found")
            .setMessage("Your profile hasn't been set up yet. Would you like to create it now?")
            .setPositiveButton("Create Profile") { _, _ ->
                startActivity(Intent(this, ActivityProfileEdit::class.java))
                finish()
            }
            .setNegativeButton("Later") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        private const val TAG = "ActivityProfile"
    }
}