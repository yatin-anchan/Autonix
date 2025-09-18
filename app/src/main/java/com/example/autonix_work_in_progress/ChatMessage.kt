package com.example.autonix_work_in_progress

data class ChatMessage(
    val messageId: String,
    val userId: String,
    val username: String,
    val message: String,
    val timestamp: Long,
    val type: String
)