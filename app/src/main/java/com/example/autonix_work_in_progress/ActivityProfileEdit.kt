package com.example.autonix_work_in_progress

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.autonix_work_in_progress.databinding.ActivityProfileEditBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class ActivityProfileEdit : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private var selectedImageUri: Uri? = null
    private var userBirthDate: Date? = null
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // Blood group options
    private val bloodGroups = arrayOf("Select Blood Group", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    private var selectedBloodGroup = ""

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let { uri ->
                binding.ivProfilePic.setImageURI(uri)
                binding.ivProfilePic.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        setupUI()
        loadUserProfile()

        // Modern back handling
        onBackPressedDispatcher.addCallback(this) {
            showDiscardChangesDialog()
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { showDiscardChangesDialog() }
        binding.ivProfilePic.setOnClickListener { openImagePicker() }
        binding.tvDOB.setOnClickListener { showDatePicker() }
        setupBloodGroupSelection()

        binding.saveProfile.setOnClickListener {
            if (validateInputs()) saveUserProfile()
        }

        binding.cancelProfile.setOnClickListener { showDiscardChangesDialog() }
        configureEditTexts()
    }

    private fun configureEditTexts() {
        binding.tvFullName.hint = "Enter your full name"
        binding.tvDOB.hint = "Select date of birth"
        binding.tvLocation.hint = "Enter your location"
        binding.tvEmergencyContact.hint = "Primary emergency contact"
        binding.tvSecondaryEmergencyContact.hint = "Secondary emergency contact"
        binding.tvAllergies.hint = "Enter allergies or medical conditions"

        binding.tvFullName.setSingleLine(true)
        binding.tvDOB.setSingleLine(true)
        binding.tvLocation.setSingleLine(true)
        binding.tvEmergencyContact.setSingleLine(true)
        binding.tvSecondaryEmergencyContact.setSingleLine(true)

        binding.tvDOB.isFocusable = false
        binding.tvDOB.isClickable = true
    }

    private fun setupBloodGroupSelection() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bloodGroups)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBloodGroup.adapter = adapter

        binding.spinnerBloodGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position > 0) selectedBloodGroup = bloodGroups[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        userBirthDate?.let { calendar.time = it }

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                userBirthDate = selectedDate.time
                val age = calculateAge(userBirthDate!!)
                binding.tvDOB.setText("${dateFormatter.format(userBirthDate!!)} ($age years)")
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        val minCalendar = Calendar.getInstance()
        minCalendar.add(Calendar.YEAR, -120)
        datePickerDialog.datePicker.minDate = minCalendar.timeInMillis
        datePickerDialog.show()
    }

    private fun calculateAge(birthDate: Date): Int {
        val birth = Calendar.getInstance().apply { time = birthDate }
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
        return age
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.saveProfile.isEnabled = false
        binding.saveProfile.text = "Loading..."

        firestore.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    populateUserData(document.data ?: emptyMap())
                } else {
                    setDefaultValues()
                }
                binding.saveProfile.isEnabled = true
                binding.saveProfile.text = "Save"
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading user profile", e)
                Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_LONG).show()
                binding.saveProfile.isEnabled = true
                binding.saveProfile.text = "Save"
            }
    }

    private fun populateUserData(userData: Map<String, Any>) {
        try {
            // Safe string extraction with null/type checking
            binding.tvFullName.setText(getStringValue(userData, "fullName"))
            binding.tvLocation.setText(getStringValue(userData, "location"))
            binding.tvAllergies.setText(getStringValue(userData, "allergies", "None"))
            binding.tvEmergencyContact.setText(getStringValue(userData, "emergencyContact"))
            binding.tvSecondaryEmergencyContact.setText(getStringValue(userData, "secondaryEmergencyContact"))

            // Handle blood group
            val bloodGroup = getStringValue(userData, "bloodGroup")
            if (bloodGroup.isNotEmpty()) {
                selectedBloodGroup = bloodGroup
                val position = bloodGroups.indexOf(bloodGroup)
                if (position != -1) {
                    binding.spinnerBloodGroup.setSelection(position)
                }
            }

            // Handle date of birth - support both Long and Timestamp
            val dobValue = userData["dateOfBirth"]
            when (dobValue) {
                is Long -> {
                    userBirthDate = Date(dobValue)
                    displayDateOfBirth()
                }
                is com.google.firebase.Timestamp -> {
                    userBirthDate = dobValue.toDate()
                    displayDateOfBirth()
                }
                is Number -> {
                    userBirthDate = Date(dobValue.toLong())
                    displayDateOfBirth()
                }
            }

            // Handle profile picture URL
            val profilePicUrl = getStringValue(userData, "profilePicUrl")
            if (profilePicUrl.isNotEmpty()) {
                loadProfileImage(profilePicUrl)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error populating user data", e)
            Toast.makeText(this, "Error loading some profile data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getStringValue(data: Map<String, Any>, key: String, defaultValue: String = ""): String {
        return when (val value = data[key]) {
            is String -> value
            is Number -> value.toString()
            null -> defaultValue
            else -> defaultValue
        }
    }

    private fun displayDateOfBirth() {
        userBirthDate?.let { date ->
            val age = calculateAge(date)
            binding.tvDOB.setText("${dateFormatter.format(date)} ($age years)")
        }
    }

    private fun loadProfileImage(url: String) {
        try {
            Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .into(binding.ivProfilePic)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading profile image", e)
        }
    }

    private fun setDefaultValues() {
        val currentUser = auth.currentUser
        binding.tvFullName.setText(currentUser?.displayName ?: "")
        binding.tvAllergies.setText("None")
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        if (TextUtils.isEmpty(binding.tvFullName.text.toString().trim())) {
            binding.tvFullName.error = "Full name is required"
            isValid = false
        }

        val primaryContact = binding.tvEmergencyContact.text.toString().trim()
        if (TextUtils.isEmpty(primaryContact)) {
            binding.tvEmergencyContact.error = "Emergency contact is required"
            isValid = false
        } else if (!isValidPhone(primaryContact)) {
            binding.tvEmergencyContact.error = "Enter a valid phone number"
            isValid = false
        }

        val secondaryContact = binding.tvSecondaryEmergencyContact.text.toString().trim()
        if (secondaryContact.isNotEmpty() && !isValidPhone(secondaryContact)) {
            binding.tvSecondaryEmergencyContact.error = "Enter a valid phone number"
            isValid = false
        }

        if (TextUtils.isEmpty(binding.tvLocation.text.toString().trim())) {
            binding.tvLocation.error = "Location is required"
            isValid = false
        }

        if (selectedBloodGroup.isEmpty() || selectedBloodGroup == "Select Blood Group") {
            Toast.makeText(this, "Please select a blood group", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun isValidPhone(phone: String): Boolean {
        val phonePattern = "^[+]?[0-9]{10,15}$"
        return phone.matches(phonePattern.toRegex())
    }

    private fun saveUserProfile() {
        if (!validateInputs()) return
        val currentUser = auth.currentUser ?: return

        binding.saveProfile.isEnabled = false
        binding.saveProfile.text = "Saving..."

        // Create user data map with proper types
        val userData = mutableMapOf<String, Any>()

        userData["fullName"] = binding.tvFullName.text.toString().trim()
        userData["location"] = binding.tvLocation.text.toString().trim()
        userData["emergencyContact"] = binding.tvEmergencyContact.text.toString().trim()
        userData["secondaryEmergencyContact"] = binding.tvSecondaryEmergencyContact.text.toString().trim()
        userData["allergies"] = binding.tvAllergies.text.toString().trim().takeIf { it.isNotEmpty() } ?: "None"
        userData["bloodGroup"] = selectedBloodGroup
        userData["updatedAt"] = System.currentTimeMillis()

        // Add date of birth if selected
        userBirthDate?.let { date ->
            userData["dateOfBirth"] = date.time
        }

        saveEmergencyContacts(currentUser.uid) {
            if (selectedImageUri != null) {
                uploadProfilePicture(currentUser.uid, userData)
            } else {
                saveUserData(userData)
            }
        }
    }

    private fun saveEmergencyContacts(userId: String, onComplete: () -> Unit) {
        val primary = binding.tvEmergencyContact.text.toString().trim()
        val secondary = binding.tvSecondaryEmergencyContact.text.toString().trim()

        // Skip saving emergency contacts if primary is empty (shouldn't happen due to validation)
        if (primary.isEmpty()) {
            onComplete()
            return
        }

        val contactsCollection = firestore.collection("emergency_contacts").document(userId).collection("contacts")

        val primaryData = mapOf<String, Any>(
            "name" to "Primary",
            "phone" to primary,
            "relationship" to "Primary",
            "priority" to 1,
            "isActive" to true,
            "createdAt" to System.currentTimeMillis()
        )

        contactsCollection.document("primary").set(primaryData, SetOptions.merge())
            .addOnSuccessListener {
                if (secondary.isNotEmpty()) {
                    val secondaryData = mapOf<String, Any>(
                        "name" to "Secondary",
                        "phone" to secondary,
                        "relationship" to "Secondary",
                        "priority" to 2,
                        "isActive" to true,
                        "createdAt" to System.currentTimeMillis()
                    )
                    contactsCollection.document("secondary").set(secondaryData, SetOptions.merge())
                        .addOnCompleteListener { onComplete() }
                } else {
                    // Delete secondary contact if it exists but field is now empty
                    contactsCollection.document("secondary").delete()
                        .addOnCompleteListener { onComplete() }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed saving emergency contacts", e)
                Toast.makeText(this, "Failed to save emergency contacts: ${e.message}", Toast.LENGTH_LONG).show()
                onComplete()
            }
    }

    private fun uploadProfilePicture(userId: String, userData: MutableMap<String, Any>) {
        selectedImageUri?.let { uri ->
            try {
                val extension = contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
                val imageRef = storage.reference.child("profile_pictures/$userId.$extension")

                imageRef.putFile(uri)
                    .addOnSuccessListener {
                        imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                            userData["profilePicUrl"] = downloadUrl.toString()
                            saveUserData(userData)
                        }.addOnFailureListener {
                            saveUserData(userData)
                        }
                    }
                    .addOnFailureListener { _ ->
                        saveUserData(userData)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading profile picture", e)
                saveUserData(userData)
            }
        } ?: saveUserData(userData)
    }

    private fun saveUserData(userData: MutableMap<String, Any>) {
        val currentUser = auth.currentUser ?: return

        firestore.collection("users").document(currentUser.uid)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating user profile", e)
                Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_LONG).show()
                binding.saveProfile.isEnabled = true
                binding.saveProfile.text = "Save"
            }
    }

    private fun showDiscardChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Discard Changes?")
            .setMessage("Are you sure you want to discard your changes?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    companion object {
        private const val TAG = "ProfileEdit"
    }
}