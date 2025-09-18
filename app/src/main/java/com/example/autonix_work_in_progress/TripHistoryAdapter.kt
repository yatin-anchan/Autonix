package com.example.autonix_work_in_progress

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.autonix_work_in_progress.databinding.ItemTripHistoryBinding
import com.example.autonix_work_in_progress.models.TripData
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class TripHistoryAdapter(
    private var trips: List<TripData>,
    private val onTripClick: (TripData) -> Unit
) : RecyclerView.Adapter<TripHistoryAdapter.TripViewHolder>() {

    class TripViewHolder(val binding: ItemTripHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemTripHistoryBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false
        )
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val trip = trips[position]

        with(holder.binding) {
            // Set trip title
            tvTripTitle.text = trip.tripTitle.ifEmpty { "Trip #${position + 1}" }

            // Format date and time
            tvTripDate.text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(Date(trip.timestamp))
            tvTripTime.text = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(trip.timestamp))

            // Format duration from milliseconds
            val minutes = trip.duration / 60000
            val seconds = (trip.duration / 1000) % 60
            tvDuration.text = String.format("%d:%02d", minutes, seconds)

            // Distance, speed, safety
            tvDistance.text = String.format("%.2f km", trip.distance)
            tvMaxSpeed.text = String.format("%.0f km/h", trip.topSpeed)
            tvSafetyScore.text = "${trip.safetyScore}%"

            // Safety score color
            val scoreColor = when {
                trip.safetyScore >= 80 -> android.R.color.holo_green_light
                trip.safetyScore >= 60 -> android.R.color.holo_orange_light
                else -> android.R.color.holo_red_light
            }
            tvSafetyScore.setTextColor(root.context.getColor(scoreColor))

            // Hide GPS/helmet/drowsiness icons (optional)
            iconDrowsiness.visibility = View.GONE
            iconHelmet.visibility = View.GONE

            // Click listener
            root.setOnClickListener { onTripClick(trip) }
        }
    }

    override fun getItemCount() = trips.size

    fun updateList(newTrips: List<TripData>) {
        trips = newTrips
        notifyDataSetChanged()
    }
}
