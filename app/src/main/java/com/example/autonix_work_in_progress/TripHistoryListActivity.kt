package com.example.autonix_work_in_progress

import com.example.autonix_work_in_progress.models.TripData
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.autonix_work_in_progress.databinding.ActivityTripHistoryListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class TripHistoryListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripHistoryListBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var tripAdapter: TripHistoryAdapter
    private val tripsList = mutableListOf<TripData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripHistoryListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupUI()
        setupRecyclerView()
        loadTripHistory()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.swipeRefresh.setOnRefreshListener { loadTripHistory() }

        binding.btnFilterAll.setOnClickListener {
            filterTrips("all")
            updateFilterButtons("all")
        }
        binding.btnFilterWeek.setOnClickListener {
            filterTrips("week")
            updateFilterButtons("week")
        }
        binding.btnFilterMonth.setOnClickListener {
            filterTrips("month")
            updateFilterButtons("month")
        }
    }

    private fun setupRecyclerView() {
        tripAdapter = TripHistoryAdapter(tripsList) { trip ->
            val intent = Intent(this, TripHistoryDetailedActivity::class.java)
            intent.putExtra("trip_id", trip.tripId)
            startActivity(intent)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@TripHistoryListActivity)
            adapter = tripAdapter
        }
    }

    private fun loadTripHistory() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showError("Please log in to view trip history")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.swipeRefresh.isRefreshing = true

        firestore.collection("completed_trips")
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                tripsList.clear()
                for (document in documents) {
                    try {
                        val trip = TripData(
                            tripId = document.id,
                            userId = document.getString("userId") ?: "",
                            timestamp = document.getLong("timestamp") ?: 0L,
                            duration = document.getLong("duration") ?: 0L,
                            distance = document.getDouble("distance") ?: 0.0,
                            topSpeed = document.getDouble("topSpeed") ?: 0.0,
                            avgSpeed = document.getDouble("avgSpeed") ?: 0.0,
                            safetyScore = (document.getLong("safetyScore") ?: 100L).toInt(),
                            tripTitle = document.getString("tripTitle") ?: "", // <- new
                            path = emptyList()
                        )
                        tripsList.add(trip)
                    } catch (e: Exception) {
                        android.util.Log.w("TripHistory", "Error parsing trip document", e)
                    }
                }

                updateUI()
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                if (tripsList.isEmpty()) showEmptyState()
            }
            .addOnFailureListener { exception ->
                android.util.Log.w("TripHistory", "Error getting trips", exception)
                showError("Failed to load trip history")
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
    }

    private fun filterTrips(filter: String) {
        val currentTime = System.currentTimeMillis()
        val oneWeekAgo = currentTime - 7L * 24 * 60 * 60 * 1000
        val oneMonthAgo = currentTime - 30L * 24 * 60 * 60 * 1000

        val filteredList = when (filter) {
            "week" -> tripsList.filter { it.timestamp >= oneWeekAgo }
            "month" -> tripsList.filter { it.timestamp >= oneMonthAgo }
            else -> tripsList
        }

        tripAdapter.updateList(filteredList)
        updateStatsDisplay(filteredList)
    }

    private fun updateFilterButtons(activeFilter: String) {
        binding.btnFilterAll.setBackgroundResource(R.drawable.btn_filter_inactive)
        binding.btnFilterWeek.setBackgroundResource(R.drawable.btn_filter_inactive)
        binding.btnFilterMonth.setBackgroundResource(R.drawable.btn_filter_inactive)

        when (activeFilter) {
            "all" -> binding.btnFilterAll.setBackgroundResource(R.drawable.btn_filter_active)
            "week" -> binding.btnFilterWeek.setBackgroundResource(R.drawable.btn_filter_active)
            "month" -> binding.btnFilterMonth.setBackgroundResource(R.drawable.btn_filter_active)
        }
    }

    private fun updateUI() {
        tripAdapter.notifyDataSetChanged()
        updateStatsDisplay(tripsList)
    }

    private fun updateStatsDisplay(trips: List<TripData>) {
        val totalTrips = trips.size
        val totalDistance = trips.sumOf { it.distance }
        val avgSafetyScore = if (trips.isNotEmpty()) trips.map { it.safetyScore }.average() else 0.0

        binding.tvTotalTrips.text = totalTrips.toString()
        binding.tvTotalDistance.text = String.format("%.1f km", totalDistance)
        binding.tvAvgSafetyScore.text = String.format("%.0f%%", avgSafetyScore)
    }

    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
