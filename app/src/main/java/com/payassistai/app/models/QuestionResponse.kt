package com.payassistai.app.models

data class QuestionResponse(
    val answer: String,
    val sources: List<String> = emptyList(),
    val relevance: Double? = null,
    val error: String? = null
)