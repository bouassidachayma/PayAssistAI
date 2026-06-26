package com.payassistai.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {
    @Insert
    suspend fun insert(merchant: Merchant): Long

    @Insert
    suspend fun insertAll(merchants: List<Merchant>)

    @Update
    suspend fun update(merchant: Merchant)

    @Delete
    suspend fun delete(merchant: Merchant)

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun getMerchantById(id: Int): Merchant?

    @Query("SELECT * FROM merchants WHERE email = :email")
    suspend fun getMerchantByEmail(email: String): Merchant?

    @Query("SELECT * FROM merchants ORDER BY name ASC")
    fun getAllMerchants(): Flow<List<Merchant>>

    @Query("SELECT * FROM merchants WHERE role = 'admin' LIMIT 1")
    suspend fun getAdmin(): Merchant?

    // NEW: used by ChatViewModel.changePassword()
    @Query("UPDATE merchants SET passwordHash = :newHash WHERE id = :merchantId")
    suspend fun updatePassword(merchantId: Int, newHash: String)
}