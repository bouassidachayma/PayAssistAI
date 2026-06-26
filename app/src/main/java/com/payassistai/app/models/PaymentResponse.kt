package com.payassistai.app.models

data class PaymentResponse(
    val status: String,
    val amount: Double,
    val merchant: String,
    val decline_reason: String? = null,
    val new_balance: Double? = null,
    val daily_remaining: Double? = null,
    val monthly_remaining: Double? = null
)
