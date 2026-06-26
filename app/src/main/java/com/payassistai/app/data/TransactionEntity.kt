package com.payassistai.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Double,
    val merchant: String,
    val date: Long = System.currentTimeMillis(),
    val status: String,
    val category: String = "Other",
    val declineReason: String? = null,
    val merchantId: Int = 0
)