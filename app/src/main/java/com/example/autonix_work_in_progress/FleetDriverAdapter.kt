package com.example.autonix_work_in_progress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class FleetDriverAdapter(
    private val drivers: List<FleetDriver>,
    private val onDriverClick: (FleetDriver) -> Unit
) : RecyclerView.Adapter<FleetDriverAdapter.DriverViewHolder>() {

    class DriverViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivStatusIndicator: ImageView = view.findViewById(R.id.ivStatusIndicator)
        val tvDriverName: TextView = view.findViewById(R.id.tvDriverName)
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvSpeed: TextView = view.findViewById(R.id.tvSpeed)
        val tvLastUpdate: TextView = view.findViewById(R.id.tvLastUpdate)
        val ivLocationIcon: ImageView = view.findViewById(R.id.ivLocationIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DriverViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fleet_driver, parent, false)
        return DriverViewHolder(view)
    }

    override fun onBindViewHolder(holder: DriverViewHolder, position: Int) {
        val driver = drivers[position]

        holder.tvDriverName.text = driver.fullName
        holder.tvUsername.text = "@${driver.username}"
        holder.tvSpeed.text = "${driver.speed} km/h"

        // Format last update time
        val lastUpdateTime = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(driver.lastUpdate))
        holder.tvLastUpdate.text = "Last update: $lastUpdateTime"

        // Set status indicator color
        val statusColor = when (driver.status) {
            "online" -> ContextCompat.getColor(holder.itemView.context, R.color.success_green)
            "offline" -> ContextCompat.getColor(holder.itemView.context, R.color.red_primary)
            else -> ContextCompat.getColor(holder.itemView.context, R.color.warning_orange)
        }
        holder.ivStatusIndicator.setColorFilter(statusColor)

        // Set location icon based on status
        val locationIcon = when (driver.status) {
            "online" -> R.drawable.ic_location_on
            "offline" -> R.drawable.ic_location_off
            else -> R.drawable.ic_location_on
        }
        holder.ivLocationIcon.setImageResource(locationIcon)

        // Click listener
        holder.itemView.setOnClickListener {
            onDriverClick(driver)
        }
    }

    override fun getItemCount() = drivers.size
}