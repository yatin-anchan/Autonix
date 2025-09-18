package com.example.autonix_work_in_progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.autonix_work_in_progress.databinding.ItemSafetyEventBinding
import com.example.autonix_work_in_progress.models.SafetyEvent
import com.example.autonix_work_in_progress.models.SafetyEventType
import java.text.SimpleDateFormat
import java.util.*

class SafetyEventsAdapter(
    private val events: List<SafetyEvent>,
    private val onEventClick: ((SafetyEvent) -> Unit)? = null
) : RecyclerView.Adapter<SafetyEventsAdapter.SafetyEventViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SafetyEventViewHolder {
        val binding = ItemSafetyEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SafetyEventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SafetyEventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount(): Int = events.size

    inner class SafetyEventViewHolder(
        private val binding: ItemSafetyEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: SafetyEvent) {
            binding.apply {
                // Set event type and icon
                when (event.type) {
                    SafetyEventType.DROWSINESS_DETECTED -> {
                        ivEventIcon.setImageResource(R.drawable.ic_sleepy)
                        tvEventType.text = "Drowsiness Event"
                        tvEventType.setTextColor(ContextCompat.getColor(root.context, R.color.warning_orange))
                    }
                    SafetyEventType.DISTRACTION_DETECTED -> {
                        ivEventIcon.setImageResource(R.drawable.ic_distracted)
                        tvEventType.text = "Distraction Event"
                        tvEventType.setTextColor(ContextCompat.getColor(root.context, R.color.error_red))
                    }
                    SafetyEventType.SPEEDING -> {
                        ivEventIcon.setImageResource(R.drawable.ic_speed_max)
                        tvEventType.text = "Speed Violation"
                        tvEventType.setTextColor(ContextCompat.getColor(root.context, R.color.error_red))
                    }
                    SafetyEventType.HARD_BRAKING -> {
                        ivEventIcon.setImageResource(R.drawable.ic_event_brake)
                        tvEventType.text = "Harsh Braking"
                        tvEventType.setTextColor(ContextCompat.getColor(root.context, R.color.warning_orange))
                    }
                    SafetyEventType.HELMET_NOT_DETECTED -> {
                        ivEventIcon.setImageResource(R.drawable.ic_no_helmet)
                        tvEventType.text = "Helmet Not Detected"
                        tvEventType.setTextColor(ContextCompat.getColor(root.context, R.color.error_red))
                    }
                    SafetyEventType.SHARP_TURN -> {
                        ivEventIcon.setImageResource(R.drawable.ic_sharp_turn)
                        tvEventType.text = "Sharp Turn"
                        tvEventType.setTextColor(ContextCompat.getColor(root.context, R.color.warning_orange))
                    }
                }


                // Set timestamp
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                tvEventTime.text = timeFormat.format(Date(event.timestamp))

                // Set location if available
                if (event.latitude != 0.0 && event.longitude != 0.0) {
                    tvEventLocation.text = "Lat: ${String.format("%.6f", event.latitude)}, " +
                            "Lng: ${String.format("%.6f", event.longitude)}"
                } else {
                    tvEventLocation.text = "Location not available"
                }

                // Set severity
                tvEventSeverity.text = "Severity: ${event.severity}"
                when (event.severity.lowercase()) {
                    "high" -> tvEventSeverity.setTextColor(root.context.getColor(R.color.error_red))
                    "medium" -> tvEventSeverity.setTextColor(root.context.getColor(R.color.warning_orange))
                    else -> tvEventSeverity.setTextColor(root.context.getColor(R.color.success_green))
                }

                // Click listener
                root.setOnClickListener {
                    onEventClick?.invoke(event)
                }
            }
        }
    }
}