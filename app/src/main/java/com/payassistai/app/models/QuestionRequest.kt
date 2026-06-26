package com.payassistai.app.models

data class QuestionRequest(
    val question: String,
    val transaction_id: String? = null
)