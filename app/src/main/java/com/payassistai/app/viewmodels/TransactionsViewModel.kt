package com.payassistai.app.viewmodels

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payassistai.app.data.Merchant
import com.payassistai.app.data.MerchantRepository
import com.payassistai.app.data.TransactionEntity
import com.payassistai.app.data.TransactionRepository
import com.payassistai.app.models.PaymentRequest
import com.payassistai.app.network.ApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val merchantRepository: MerchantRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var transactionsJob: Job? = null
    private var hasSeededSampleTransactions = false

    init {
        observeMerchant()
        seedSampleTransactionsIfNeeded()
    }

    private fun observeMerchant() {
        viewModelScope.launch {
            merchantRepository.currentMerchant.collect { merchant ->
                if (merchant != null) {
                    loadTransactions()
                } else {
                    _transactions.value = emptyList()
                }
            }
        }
    }

    fun loadTransactions() {
        transactionsJob?.cancel()
        transactionsJob = viewModelScope.launch {
            transactionRepository.getAllTransactions().collect { all ->
                val merchant = merchantRepository.currentMerchant.value
                val filtered = if (merchant != null && merchant.role != "admin") {
                    all.filter { it.merchantId == merchant.id }
                } else {
                    all
                }
                _transactions.value = filtered
            }
        }
    }

    fun addTransaction(amount: Double, merchant: String, status: String, declineReason: String? = null) {
        viewModelScope.launch {
            try {
                // Use the logged-in merchant's own category directly instead
                // of guessing it back out of the merchant name — the correct
                // category is already sitting on the Merchant record.
                val current = merchantRepository.currentMerchant.value
                val category = current?.category ?: "Other"
                val merchantId = current?.id ?: 0
                val transaction = TransactionEntity(
                    amount = amount,
                    merchant = merchant,
                    status = status,
                    category = category,
                    declineReason = declineReason,
                    merchantId = merchantId
                )
                transactionRepository.insertTransaction(transaction)
            } catch (e: Exception) {
                _error.value = "Failed to add transaction: ${e.message}"
            }
        }
    }

    fun voidTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            try {
                transactionRepository.voidTransaction(transaction)
                loadTransactions()
            } catch (e: Exception) {
                _error.value = "Failed to void transaction: ${e.message}"
            }
        }
    }

    fun processPayment(
        amount: Double,
        merchant: String,
        pin: String,
        onResult: (status: String?, declineReason: String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = apiService.processPayment(
                    PaymentRequest(
                        card_number = "1234567890123456", // fixed for demo
                        pin = pin,
                        amount = amount,
                        merchant = merchant
                    )
                )
                val status = response.status
                val reason = response.decline_reason
                if (status == "Approved") {
                    addTransaction(amount, merchant, "Approved")
                } else {
                    addTransaction(amount, merchant, "Declined", reason)
                }
                onResult(status, reason)
            } catch (e: Exception) {
                onResult(null, e.message)
            }
        }
    }


    private fun seedSampleTransactionsIfNeeded() {
        viewModelScope.launch {
            merchantRepository.getAllMerchants().collect { merchants ->
                if (hasSeededSampleTransactions) return@collect

                val realMerchants = merchants.filter { it.role == "merchant" }
                if (realMerchants.isEmpty()) return@collect // merchants not seeded yet — wait for the next emission

                val existingTransactions = transactionRepository.getAllTransactions().firstOrNull()
                if (existingTransactions.isNullOrEmpty()) {
                    createSampleTransactionsForAllMerchants(realMerchants)
                }
                hasSeededSampleTransactions = true
            }
        }
    }


    private suspend fun createSampleTransactionsForAllMerchants(merchants: List<Merchant>) {
        val statuses = listOf("Approved", "Declined", "Voided")
        val random = Random()
        val now = System.currentTimeMillis()

        merchants.forEach { merchant ->
            val count = random.nextInt(4) + 3 // 3-6 transactions per merchant
            repeat(count) {
                val amount = (random.nextDouble() * 490 + 10).let { String.format("%.2f", it).toDouble() }
                val status = if (random.nextDouble() < 0.7) "Approved" else statuses.random()
                val declineReason = if (status == "Declined") {
                    listOf("Insufficient funds", "Exceeds daily limit", "Incorrect PIN", "Card expired").random()
                } else null
                val date = now - (random.nextInt(30) * 24 * 60 * 60 * 1000L)

                val transaction = TransactionEntity(
                    amount = amount,
                    merchant = merchant.name,
                    status = status,
                    category = merchant.category,
                    declineReason = declineReason,
                    date = date,
                    merchantId = merchant.id
                )
                transactionRepository.insertTransaction(transaction)
            }
        }
    }

    // CSV Export
    fun exportTransactionsAsCSV(): String {
        val transactions = _transactions.value
        val sb = StringBuilder()
        sb.append("ID,Merchant,Amount,Status,Category,DeclineReason,Date,MerchantId\n")
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        transactions.forEach { tx ->
            val date = dateFormat.format(Date(tx.date))
            sb.append("${tx.id},${tx.merchant},${tx.amount},${tx.status},${tx.category},${tx.declineReason ?: ""},$date,${tx.merchantId}\n")
        }
        return sb.toString()
    }

    fun saveCSVToDownloads(context: Context, csvContent: String) {
        try {
            val fileName = "transactions.csv"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray())
                        Toast.makeText(context, "✅ CSV saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                    }
                } ?: run {
                    Toast.makeText(context, "❌ Failed to save CSV to Downloads", Toast.LENGTH_SHORT).show()
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                file.writeText(csvContent)
                Toast.makeText(context, "✅ CSV saved to Downloads: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun readCSVFromDownloads(context: Context): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH
            )
            val cursor = resolver.query(uri, projection, null, null, null)
            var foundFile: File? = null
            cursor?.use {
                val idIndex = it.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (idIndex == -1 || nameIndex == -1) return@use
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex)
                    if (name == "transactions.csv") {
                        val id = it.getLong(idIndex)
                        val contentUri = ContentUris.withAppendedId(uri, id)
                        val inputStream = resolver.openInputStream(contentUri)
                        if (inputStream != null) {
                            foundFile = File(context.cacheDir, "temp_transactions.csv")
                            inputStream.use { input ->
                                foundFile?.outputStream()?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        break
                    }
                }
            }
            foundFile
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadsDir, "transactions.csv").takeIf { it.exists() }
        }
    }

    suspend fun importTransactionsFromCSV(context: Context): String {
        try {
            val file = readCSVFromDownloads(context)
            if (file == null || !file.exists()) {
                return "❌ No CSV file found in Downloads."
            }
            val lines = file.readLines()
            if (lines.size < 2) {
                return "❌ CSV file is empty or has no data."
            }
            var count = 0
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

            for (i in 1 until lines.size) {
                val line = lines[i]
                val parts = line.split(",")
                if (parts.size >= 8) {
                    try {
                        val id = parts[0].toIntOrNull() ?: 0
                        val merchant = parts[1]
                        val amount = parts[2].toDoubleOrNull() ?: 0.0
                        val status = parts[3]
                        val category = parts[4]
                        val declineReason = parts[5].ifEmpty { null }
                        val date: Long = try {
                            parts[6].toLongOrNull() ?: dateFormat.parse(parts[6])?.time ?: System.currentTimeMillis()
                        } catch (_: Exception) {
                            System.currentTimeMillis()
                        }
                        val merchantId = parts.getOrNull(7)?.toIntOrNull() ?: 0

                        val existing = transactionRepository.getTransactionById(id)
                        if (existing == null) {
                            val transaction = TransactionEntity(
                                id = id,
                                amount = amount,
                                merchant = merchant,
                                status = status,
                                category = category,
                                declineReason = declineReason,
                                date = date,
                                merchantId = merchantId
                            )
                            transactionRepository.insertTransaction(transaction)
                            count++
                        }
                    } catch (_: Exception) {
                        // Skip invalid lines
                    }
                }
            }
            loadTransactions()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && file.exists()) {
                file.delete()
            }
            return "✅ Imported $count transactions from CSV!"
        } catch (e: Exception) {
            return "❌ Error importing CSV: ${e.message}"
        }
    }
}