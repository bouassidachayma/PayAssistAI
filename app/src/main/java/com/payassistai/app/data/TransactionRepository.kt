package com.payassistai.app.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val dao: TransactionDao
) {

    suspend fun insertTransaction(transaction: TransactionEntity) {
        dao.insert(transaction)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        dao.delete(transaction)
    }

    suspend fun deleteAllTransactions() {
        dao.deleteAll()
    }

    suspend fun deleteTransactionsForMerchant(merchantId: Int) {
        dao.deleteByMerchantId(merchantId)
    }

    fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return dao.getAllTransactions()
    }

    suspend fun getTransactionById(id: Int): TransactionEntity? {
        return dao.getTransactionById(id)
    }

    /**
     * Void a transaction (change status to "Voided")
     * This replaces the original transaction with a voided one
     */
    suspend fun voidTransaction(transaction: TransactionEntity) {
        // Delete the original transaction
        dao.delete(transaction)

        // Create a voided version
        val voidedTransaction = transaction.copy(
            status = "Voided",
            declineReason = "Voided by merchant"
        )

        // Insert the voided version
        dao.insert(voidedTransaction)
    }
}