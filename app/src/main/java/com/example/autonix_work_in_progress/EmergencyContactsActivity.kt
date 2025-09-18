package com.example.autonix_work_in_progress

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.autonix_work_in_progress.databinding.ActivityEmergencyContactsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class EmergencyContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmergencyContactsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupClickListeners()
        loadContactsFromFirestore()
    }

    private fun setupClickListeners() {
        // Back button
        binding.backIcon.setOnClickListener {
            onBackPressed()
        }

        // Save contacts card
        binding.saveContactsCard.setOnClickListener {
            saveContacts()
        }

        // Test alert card
        binding.testAlertCard.setOnClickListener {
            showTestAlertDialog()
        }
    }

    private fun showTestAlertDialog() {
        val primary = binding.etPrimaryContact.text.toString().trim()
        val secondary = binding.etSecondaryContact.text.toString().trim()

        if (primary.isEmpty()) {
            Toast.makeText(this, "Please add a primary contact first", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Test Emergency Alert")
            .setMessage("Send a test emergency alert to your contacts?\n\nPrimary: $primary" +
                    if (secondary.isNotEmpty()) "\nSecondary: $secondary" else "")
            .setPositiveButton("Send Test") { _, _ ->
                sendTestAlert(primary, secondary)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendTestAlert(primary: String, secondary: String) {
        // Check SMS permission first
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {

            // Request SMS permission
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.SEND_SMS),
                SMS_PERMISSION_REQUEST_CODE)
            return
        }

        val smsManager = SmsManager.getDefault()
        val testMessage = "AUTONIX Emergency Test Alert: This is a test message from your emergency contact system. Your safety monitoring is active."

        val sentContacts = mutableListOf<String>()
        val failedContacts = mutableListOf<String>()

        try {
            // Send to primary contact
            smsManager.sendTextMessage(primary, null, testMessage, null, null)
            sentContacts.add(primary)

            // Send to secondary contact if provided
            if (secondary.isNotEmpty()) {
                smsManager.sendTextMessage(secondary, null, testMessage, null, null)
                sentContacts.add(secondary)
            }

            // Show success dialog
            showTestResultDialog(sentContacts, failedContacts)

        } catch (e: Exception) {
            // Handle SMS sending failure
            failedContacts.add(primary)
            if (secondary.isNotEmpty()) {
                failedContacts.add(secondary)
            }

            showTestResultDialog(sentContacts, failedContacts)
        }
    }

    private fun showTestResultDialog(sent: List<String>, failed: List<String>) {
        val message = StringBuilder()

        if (sent.isNotEmpty()) {
            message.append("Test alert sent successfully to:\n")
            sent.forEach { contact ->
                message.append("✓ $contact\n")
            }
        }

        if (failed.isNotEmpty()) {
            if (sent.isNotEmpty()) message.append("\n")
            message.append("Failed to send to:\n")
            failed.forEach { contact ->
                message.append("✗ $contact\n")
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (failed.isEmpty()) "Test Alert Sent" else "Test Alert Results")
            .setMessage(message.toString().trim())
            .setPositiveButton("OK", null)
            .show()
    }

    // Handle permission request result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            SMS_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, retry sending test alert
                    val primary = binding.etPrimaryContact.text.toString().trim()
                    val secondary = binding.etSecondaryContact.text.toString().trim()
                    sendTestAlert(primary, secondary)
                } else {
                    Toast.makeText(this, "SMS permission required to send test alerts", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val SMS_PERMISSION_REQUEST_CODE = 123
    }

    private fun loadContactsFromFirestore() {
        val currentUser = auth.currentUser ?: return
        val sharedPref = getSharedPreferences("autonix_prefs", Context.MODE_PRIVATE)

        firestore.collection("emergency_contacts").document(currentUser.uid)
            .collection("contacts")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val contacts = snapshot.mapNotNull { it.getString("phone") }
                        .filter { it.isNotBlank() && isValidPhone(it) }

                    // Load primary and secondary
                    val primary = contacts.getOrNull(0) ?: ""
                    val secondary = contacts.getOrNull(1) ?: ""

                    binding.etPrimaryContact.setText(primary)
                    binding.etSecondaryContact.setText(secondary)

                    // Cache in SharedPreferences
                    with(sharedPref.edit()) {
                        putString("primary_emergency_contact", primary)
                        putString("secondary_emergency_contact", secondary)
                        apply()
                    }

                } else {
                    // Load from SharedPreferences if Firestore empty
                    val primary = sharedPref.getString("primary_emergency_contact", "") ?: ""
                    val secondary = sharedPref.getString("secondary_emergency_contact", "") ?: ""
                    binding.etPrimaryContact.setText(primary)
                    binding.etSecondaryContact.setText(secondary)
                }
            }
            .addOnFailureListener {
                // Fallback to SharedPreferences on failure
                val primary = sharedPref.getString("primary_emergency_contact", "") ?: ""
                val secondary = sharedPref.getString("secondary_emergency_contact", "") ?: ""
                binding.etPrimaryContact.setText(primary)
                binding.etSecondaryContact.setText(secondary)
                Toast.makeText(this, "Failed to load contacts from server", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveContacts() {
        val primary = binding.etPrimaryContact.text.toString().trim()
        val secondary = binding.etSecondaryContact.text.toString().trim()

        if (!isValidPhone(primary)) {
            binding.etPrimaryContact.error = "Invalid phone number"
            return
        }

        if (secondary.isNotEmpty() && !isValidPhone(secondary)) {
            binding.etSecondaryContact.error = "Invalid phone number"
            return
        }

        val currentUser = auth.currentUser ?: return
        val sharedPref = getSharedPreferences("autonix_prefs", Context.MODE_PRIVATE)

        val contactsCollection = firestore.collection("emergency_contacts")
            .document(currentUser.uid)
            .collection("contacts")

        // Create/update primary contact
        val primaryData = hashMapOf(
            "name" to "Primary",
            "phone" to primary,
            "relationship" to "Self",
            "priority" to 1,
            "isActive" to true,
            "createdAt" to System.currentTimeMillis()
        )

        contactsCollection.document("primary").set(primaryData)
            .addOnSuccessListener {
                // Secondary contact (optional)
                if (secondary.isNotEmpty()) {
                    val secondaryData = hashMapOf(
                        "name" to "Secondary",
                        "phone" to secondary,
                        "relationship" to "Other",
                        "priority" to 2,
                        "isActive" to true,
                        "createdAt" to System.currentTimeMillis()
                    )
                    contactsCollection.document("secondary").set(secondaryData)
                        .addOnSuccessListener {
                            updateSharedPreferences(primary, secondary)
                            showSaveSuccessDialog(primary, secondary)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to save secondary contact: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    updateSharedPreferences(primary, "")
                    showSaveSuccessDialog(primary, "")
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save primary contact: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showSaveSuccessDialog(primary: String, secondary: String) {
        AlertDialog.Builder(this)
            .setTitle("Contacts Saved Successfully!")
            .setMessage("Your emergency contacts have been updated:\n\n✓ Primary: $primary" +
                    if (secondary.isNotEmpty()) "\n✓ Secondary: $secondary" else "")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun updateSharedPreferences(primary: String, secondary: String) {
        val sharedPref = getSharedPreferences("autonix_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("primary_emergency_contact", primary)
            putString("secondary_emergency_contact", secondary)
            apply()
        }
    }

    private fun isValidPhone(phone: String): Boolean {
        val regex = "^[+]?[0-9]{10,15}$".toRegex()
        return phone.matches(regex)
    }
}