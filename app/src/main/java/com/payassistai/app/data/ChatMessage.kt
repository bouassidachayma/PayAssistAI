package com.payassistai.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    val sender: String,  // "user" or "bot"
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: Int
)