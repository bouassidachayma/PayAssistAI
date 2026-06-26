package com.payassistai.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchants")
data class Merchant(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val mallId: Int = 1, // default mall id (we can have one mall for now)
    val name: String,
    val email: String,
    val passwordHash: String, // we'll store plain text for simplicity in Phase 1
    val category: String, // e.g., "Food & Drinks", "Shopping", etc.
    val role: String, // "admin" or "merchant"
    val isActive: Boolean = true
)
