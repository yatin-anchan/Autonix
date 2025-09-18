package com.example.autonix_work_in_progress

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class FleetChatAdapter(
    private val messages: List<ChatMessage>,
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_SENT = 1
        const val VIEW_TYPE_RECEIVED = 2
        const val VIEW_TYPE_SYSTEM = 3
    }

    class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    class SystemMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSystemMessage: TextView = view.findViewById(R.id.tvSystemMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.type == "system" -> VIEW_TYPE_SYSTEM
            message.userId == currentUserId -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = inflater.inflate(R.layout.item_chat_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = inflater.inflate(R.layout.item_chat_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
            VIEW_TYPE_SYSTEM -> {
                val view = inflater.inflate(R.layout.item_chat_message_system, parent, false)
                SystemMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeString = timeFormat.format(Date(message.timestamp))

        when (holder) {
            is SentMessageViewHolder -> {
                holder.tvMessage.text = message.message
                holder.tvTime.text = timeString
            }
            is ReceivedMessageViewHolder -> {
                holder.tvUsername.text = message.username
                holder.tvMessage.text = message.message
                holder.tvTime.text = timeString
            }
            is SystemMessageViewHolder -> {
                holder.tvSystemMessage.text = message.message
                holder.tvTime.text = timeString
            }
        }
    }

    override fun getItemCount() = messages.size
}