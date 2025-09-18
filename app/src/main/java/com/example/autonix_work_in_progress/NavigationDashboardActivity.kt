package com.example.autonix_work_in_progress

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlin.random.Random

class NavigationDashboardActivity : AppCompatActivity() {

    // Main Views
    private lateinit var btnBack: ImageView
    private lateinit var cardMap: LinearLayout
    private lateinit var cardNavPlanner: LinearLayout
    private lateinit var cardFleetControl: LinearLayout

    // Fleet Control Views
    private lateinit var fleetOptionsLayout: LinearLayout
    private lateinit var btnJoin: LinearLayout
    private lateinit var btnCreate: LinearLayout
    private lateinit var joinLayout: LinearLayout
    private lateinit var createLayout: LinearLayout

    // Join Views
    private lateinit var etJoinCode: EditText
    private lateinit var btnConfirmJoin: LinearLayout

    // Create Views
    private lateinit var tvGeneratedCode: TextView
    private lateinit var btnCopyCode: ImageView
    private lateinit var btnShareCode: ImageView
    private lateinit var switchLock: Switch
    private lateinit var etFleetPassword: EditText
    private lateinit var etDriverCount: EditText
    private lateinit var btnConfirmCreate: LinearLayout

    // State Variables
    private var isFleetExpanded = false
    private var isJoinMode = false
    private var isCreateMode = false
    private var generatedFleetCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nav_dashboard)

        initViews()
        setupClickListeners()
        generateFleetCode()

        // Back press handling
        onBackPressedDispatcher.addCallback(this) {
            if (isFleetExpanded) {
                toggleFleetExpansion()
            } else {
                finish()
            }
        }
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        cardMap = findViewById(R.id.cardMap)
        cardNavPlanner = findViewById(R.id.cardNavPlanner)
        cardFleetControl = findViewById(R.id.cardFleetControl)

        fleetOptionsLayout = findViewById(R.id.fleetOptionsLayout)
        btnJoin = findViewById(R.id.btnJoin)
        btnCreate = findViewById(R.id.btnCreate)
        joinLayout = findViewById(R.id.joinLayout)
        createLayout = findViewById(R.id.createLayout)

        etJoinCode = findViewById(R.id.etJoinCode)
        btnConfirmJoin = findViewById(R.id.btnConfirmJoin)

        tvGeneratedCode = findViewById(R.id.tvGeneratedCode)
        btnCopyCode = findViewById(R.id.btnCopyCode)
        btnShareCode = findViewById(R.id.btnShareCode)
        switchLock = findViewById(R.id.switchLock)
        etFleetPassword = findViewById(R.id.etFleetPassword)
        etDriverCount = findViewById(R.id.etDriverCount)
        btnConfirmCreate = findViewById(R.id.btnConfirmCreate)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }

        cardMap.setOnClickListener {
            animateCardClick(cardMap)
            Toast.makeText(this, "Opening Interactive Map...", Toast.LENGTH_SHORT).show()
            // TODO: Launch MapActivity
        }

        cardNavPlanner.setOnClickListener {
            animateCardClick(cardNavPlanner)
            startActivity(Intent(this, NavigationPlannerActivity::class.java))
        }

        cardFleetControl.setOnClickListener { toggleFleetExpansion() }

        btnJoin.setOnClickListener { showJoinMode() }
        btnCreate.setOnClickListener { showCreateMode() }

        btnConfirmJoin.setOnClickListener { handleJoinFleet() }

        btnCopyCode.setOnClickListener { copyFleetCode() }
        btnShareCode.setOnClickListener { shareFleetCode() }

        switchLock.setOnCheckedChangeListener { _, isChecked -> handleLockToggle(isChecked) }

        btnConfirmCreate.setOnClickListener { handleCreateFleet() }
    }

    private fun animateCardClick(view: View) {
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 0.95f),
                ObjectAnimator.ofFloat(view, "scaleY", 0.95f)
            )
            duration = 100
        }

        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f)
            )
            duration = 100
        }

        scaleDown.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                scaleUp.start()
            }
        })
        scaleDown.start()
    }

    private fun toggleFleetExpansion() {
        isFleetExpanded = !isFleetExpanded
        if (isFleetExpanded) expandFleetOptions() else collapseFleetOptions()
    }

    private fun expandFleetOptions() {
        fleetOptionsLayout.isVisible = true

        val slideDown = ObjectAnimator.ofFloat(fleetOptionsLayout, "translationY", -50f, 0f)
        val fadeIn = ObjectAnimator.ofFloat(fleetOptionsLayout, "alpha", 0f, 1f)
        AnimatorSet().apply { playTogether(slideDown, fadeIn); duration = 300; start() }

        val expandIcon = cardFleetControl.findViewById<ImageView>(R.id.expandIcon)
        expandIcon?.let { ObjectAnimator.ofFloat(it, "rotation", 0f, 180f).apply { duration = 300; start() } }
    }

    private fun collapseFleetOptions() {
        val slideUp = ObjectAnimator.ofFloat(fleetOptionsLayout, "translationY", 0f, -50f)
        val fadeOut = ObjectAnimator.ofFloat(fleetOptionsLayout, "alpha", 1f, 0f)
        AnimatorSet().apply {
            playTogether(slideUp, fadeOut)
            duration = 300
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    fleetOptionsLayout.isVisible = false
                    joinLayout.isVisible = false
                    createLayout.isVisible = false
                    isJoinMode = false
                    isCreateMode = false
                }
            })
            start()
        }

        val expandIcon = cardFleetControl.findViewById<ImageView>(R.id.expandIcon)
        expandIcon?.let { ObjectAnimator.ofFloat(it, "rotation", 180f, 0f).apply { duration = 300; start() } }
    }

    private fun showJoinMode() {
        if (isJoinMode) return
        isJoinMode = true
        isCreateMode = false
        createLayout.isVisible = false
        joinLayout.isVisible = true
        animateLayoutAppearance(joinLayout)
        etJoinCode.requestFocus()
    }

    private fun showCreateMode() {
        if (isCreateMode) return
        isCreateMode = true
        isJoinMode = false
        joinLayout.isVisible = false
        createLayout.isVisible = true
        animateLayoutAppearance(createLayout)
        generateFleetCode()
        etDriverCount.requestFocus()
    }

    private fun animateLayoutAppearance(view: View) {
        val slideDown = ObjectAnimator.ofFloat(view, "translationY", -30f, 0f)
        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        AnimatorSet().apply { playTogether(slideDown, fadeIn); duration = 250; start() }
    }

    private fun handleJoinFleet() {
        val code = etJoinCode.text.toString().trim().uppercase()
        if (code.isEmpty()) { etJoinCode.error = "Enter fleet code"; return }
        if (code.length != 6) { etJoinCode.error = "Fleet code must be 6 characters"; return }

        animateCardClick(btnConfirmJoin)
        Toast.makeText(this, "Joining fleet: $code", Toast.LENGTH_SHORT).show()

        // Simulate API response
        btnConfirmJoin.postDelayed({
            Toast.makeText(this, "Joined fleet successfully!", Toast.LENGTH_LONG).show()
            val intent = Intent(this, FleetControlActivity::class.java)
            intent.putExtra("fleet_code", code)
            startActivity(intent)
            finish()
        }, 1500)
    }

    private fun handleCreateFleet() {
        val driverCount = etDriverCount.text.toString().toIntOrNull()
        val password = if (switchLock.isChecked) etFleetPassword.text.toString() else null

        if (driverCount == null || driverCount <= 0) { etDriverCount.error = "Enter valid driver count"; return }
        if (switchLock.isChecked && password.isNullOrEmpty()) { etFleetPassword.error = "Enter password"; return }

        animateCardClick(btnConfirmCreate)
        Toast.makeText(this, "Creating fleet...", Toast.LENGTH_SHORT).show()

        val fleetData = FleetData(
            code = generatedFleetCode,
            maxDrivers = driverCount,
            isPasswordProtected = switchLock.isChecked,
            password = password
        )

        // Simulate API
        btnConfirmCreate.postDelayed({
            Toast.makeText(this, "Fleet created successfully!", Toast.LENGTH_LONG).show()
            val intent = Intent(this, FleetControlActivity::class.java)
            intent.putExtra("fleet_data", fleetData)
            startActivity(intent)
            finish()
        }, 1500)
    }

    private fun handleLockToggle(isChecked: Boolean) {
        etFleetPassword.isVisible = isChecked
        if (isChecked) {
            val fadeIn = ObjectAnimator.ofFloat(etFleetPassword, "alpha", 0f, 1f).apply { duration = 200 }
            fadeIn.start()
            etFleetPassword.requestFocus()
        } else {
            etFleetPassword.text.clear()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etFleetPassword.windowToken, 0)
        }
    }

    private fun generateFleetCode() {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        generatedFleetCode = (1..6).map { chars.random() }.joinToString("")
        tvGeneratedCode.text = generatedFleetCode
    }

    private fun copyFleetCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Fleet Code", generatedFleetCode)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Fleet code copied", Toast.LENGTH_SHORT).show()
        animateCardClick(btnCopyCode)
    }

    private fun shareFleetCode() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AUTONIX Fleet Invitation")
            putExtra(Intent.EXTRA_TEXT, "Join my AUTONIX fleet with code: $generatedFleetCode")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Fleet Code"))
        animateCardClick(btnShareCode)
    }

    // FleetData class for real-time usage
    data class FleetData(
        val code: String,
        val maxDrivers: Int,
        val isPasswordProtected: Boolean,
        val password: String?
    ) : java.io.Serializable
}
