package com.payassistai.app.models

data class PaymentRequest(
    val card_number: String,
    val pin: String,
    val amount: Double,
    val merchant: String
)
