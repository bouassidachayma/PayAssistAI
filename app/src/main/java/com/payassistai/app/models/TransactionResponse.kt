package com.payassistai.app.models

data class TransactionResponse(
    val status: String,
    val amount: Double,
    val merchant: String,
    val decline_code: String? = null,
    val decline_reason: String? = null
)