package com.example.autonix_work_in_progress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchResultsAdapter(
    private val results: List<NavigationPlannerActivity.SearchResult>,
    private val onItemClick: (NavigationPlannerActivity.SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.SearchResultViewHolder>() {

    inner class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconLocation: ImageView = itemView.findViewById(R.id.iconLocation)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)

        fun bind(result: NavigationPlannerActivity.SearchResult) {
            tvName.text = result.name
            tvAddress.text = result.address

            itemView.setOnClickListener {
                onItemClick(result)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size
}