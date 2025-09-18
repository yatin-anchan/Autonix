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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

class FleetControlActivity : AppCompatActivity() {

    private lateinit var tvFleetCode: TextView
    private lateinit var tvDriverCount: TextView
    private lateinit var btnAddDriver: LinearLayout
    private lateinit var btnRemoveFleet: LinearLayout
    private lateinit var btnCopyFleetCode: ImageView
    private lateinit var btnShareFleetCode: ImageView
    private lateinit var recyclerDrivers: RecyclerView
    private lateinit var driverAdapter: DriverAdapter

    private lateinit var fleetData: NavigationDashboardActivity.FleetData
    private val drivers = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fleet_control)

        initViews()
        getFleetDataFromIntent()
        setupDriverRecycler()
        setupClickListeners()

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
    }

    private fun initViews() {
        tvFleetCode = findViewById(R.id.tvFleetCode)
        tvDriverCount = findViewById(R.id.tvDriverCount)
        btnAddDriver = findViewById(R.id.btnAddDriver)
        btnRemoveFleet = findViewById(R.id.btnRemoveFleet)
        btnCopyFleetCode = findViewById(R.id.btnCopyFleetCode)
        btnShareFleetCode = findViewById(R.id.btnShareCode)
        recyclerDrivers = findViewById(R.id.recyclerDrivers)
    }

    private fun getFleetDataFromIntent() {
        fleetData = intent.getSerializableExtra("fleet_data") as? NavigationDashboardActivity.FleetData
            ?: intent.getStringExtra("fleet_code")?.let {
                NavigationDashboardActivity.FleetData(it, 5, false, null)
            } ?: throw Exception("No Fleet Data Found")

        tvFleetCode.text = fleetData.code
        tvDriverCount.text = "${drivers.size}/${fleetData.maxDrivers}"
    }

    private fun setupDriverRecycler() {
        driverAdapter = DriverAdapter(drivers)
        recyclerDrivers.layoutManager = LinearLayoutManager(this)
        recyclerDrivers.adapter = driverAdapter
    }

    private fun setupClickListeners() {
        btnAddDriver.setOnClickListener {
            if (drivers.size >= fleetData.maxDrivers) {
                Toast.makeText(this, "Max drivers reached", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addNewDriver()
        }

        btnRemoveFleet.setOnClickListener {
            removeFleet()
        }

        btnCopyFleetCode.setOnClickListener { copyFleetCode() }
        btnShareFleetCode.setOnClickListener { shareFleetCode() }
    }

    private fun addNewDriver() {
        val newDriver = "Driver${Random.nextInt(1000,9999)}"
        drivers.add(newDriver)
        driverAdapter.notifyItemInserted(drivers.size - 1)
        updateDriverCount()
        Toast.makeText(this, "$newDriver added", Toast.LENGTH_SHORT).show()
    }

    private fun removeFleet() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Remove Fleet")
        builder.setMessage("Are you sure you want to remove this fleet?")
        builder.setPositiveButton("Yes") { _, _ ->
            Toast.makeText(this, "Fleet removed", Toast.LENGTH_SHORT).show()
            finish()
        }
        builder.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun copyFleetCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Fleet Code", fleetData.code)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Fleet code copied", Toast.LENGTH_SHORT).show()
        animateClick(btnCopyFleetCode)
    }

    private fun shareFleetCode() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AUTONIX Fleet Invitation")
            putExtra(Intent.EXTRA_TEXT, "Join my AUTONIX fleet with code: ${fleetData.code}")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Fleet Code"))
        animateClick(btnShareFleetCode)
    }

    private fun animateClick(view: View) {
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
            override fun onAnimationEnd(animation: android.animation.Animator) { scaleUp.start() }
        })
        scaleDown.start()
    }

    private fun updateDriverCount() {
        tvDriverCount.text = "${drivers.size}/${fleetData.maxDrivers}"
    }

    // --- Recycler Adapter for Drivers ---
    inner class DriverAdapter(private val driverList: List<String>) : RecyclerView.Adapter<DriverAdapter.DriverViewHolder>() {

        inner class DriverViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvDriverName: TextView = itemView.findViewById(R.id.tvDriverName)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): DriverViewHolder {
            val view = layoutInflater.inflate(R.layout.item_fleet_driver, parent, false)
            return DriverViewHolder(view)
        }

        override fun onBindViewHolder(holder: DriverViewHolder, position: Int) {
            holder.tvDriverName.text = driverList[position]
        }

        override fun getItemCount(): Int = driverList.size
    }
}
