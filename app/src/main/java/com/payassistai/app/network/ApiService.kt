package com.payassistai.app.network

import retrofit2.http.Body
import retrofit2.http.POST
import com.payassistai.app.models.*

interface ApiService {
    @POST("ask")
    suspend fun askQuestion(
        @Body request: QuestionRequest
    ): QuestionResponse

    @POST("transactions")
    suspend fun getTransaction(
        @Body request: TransactionRequest
    ): TransactionResponse

    @POST("process_payment")
    suspend fun processPayment(
        @Body request: PaymentRequest
    ): PaymentResponse
}