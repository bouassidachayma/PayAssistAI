package com.payassistai.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.payassistai.app.data.TransactionEntity
import com.payassistai.app.ui.screens.components.TransactionCard
import com.payassistai.app.viewmodels.AuthViewModel
import com.payassistai.app.viewmodels.TransactionsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private fun formatAmount(amount: Double): String {
    return String.format("%.3f", amount).trimEnd('0').trimEnd('.')
}

fun isToday(timestamp: Long): Boolean {
    val calendar = Calendar.getInstance()
    val today = calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val txDate = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return txDate == today
}

private const val FIXED_CARD_NUMBER = "1234567890123456"
private const val FIXED_PIN = "1234"
private const val CONTACTLESS_LIMIT = 10.0

private enum class PaymentStep {
    DETAILS,
    CARD_PRESENTATION,
    PIN,
    PROCESSING,
    RESULT
}

@Composable
fun TransactionsScreen(
    transactionsViewModel: TransactionsViewModel,
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val transactions by transactionsViewModel.transactions.collectAsState()
    val currentMerchant by authViewModel.currentMerchant.collectAsState()
    val isAdmin = currentMerchant?.role == "admin"
    var searchQuery by remember { mutableStateOf("") }

    var showList by remember { mutableStateOf(false) }

    // Admin sees transactions across every merchant, so searching by
    // merchant name is useful there. A merchant's own list is already
    // scoped to just their transactions, so merchant-name search is
    // meaningless for them - only status search applies.
    val filteredTransactions = transactions.filter {
        if (isAdmin) {
            it.merchant.contains(searchQuery, ignoreCase = true) ||
                    it.status.contains(searchQuery, ignoreCase = true)
        } else {
            it.status.contains(searchQuery, ignoreCase = true)
        }
    }

    var showDetailsDialog by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentStep by remember { mutableStateOf(PaymentStep.DETAILS) }
    var paymentMerchant by remember { mutableStateOf("") }
    var paymentAmount by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var paymentResult by remember { mutableStateOf<String?>(null) }
    var declineReason by remember { mutableStateOf<String?>(null) }
    var backendError by remember { mutableStateOf<String?>(null) }

    var showImportDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf("") }

    var showAddMerchantDialog by remember { mutableStateOf(false) }

    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingStorageAction?.invoke()
        } else {
            Toast.makeText(context, "Storage permission is needed to export/import files.", Toast.LENGTH_SHORT).show()
        }
        pendingStorageAction = null
    }

    fun withStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingStorageAction = action
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        action()
    }

    fun resetPaymentDialog() {
        paymentStep = PaymentStep.DETAILS
        paymentMerchant = ""
        paymentAmount = ""
        pinInput = ""
        pinError = false
        paymentResult = null
        declineReason = null
        backendError = null
    }

    fun processPayment(amount: Double, merchant: String, pin: String) {
        paymentStep = PaymentStep.PROCESSING
        transactionsViewModel.processPayment(amount, merchant, pin) { status, reason ->
            paymentResult = status
            declineReason = reason
            if (status == null) {
                backendError = reason ?: "Unknown error"
            } else {
                backendError = null
            }
            paymentStep = PaymentStep.RESULT
        }
    }

    fun handleVoid(transaction: TransactionEntity) {
        transactionsViewModel.voidTransaction(transaction)
        showDetailsDialog = false
        selectedTransaction = null
    }

    fun exportCSV() {
        withStoragePermission {
            val csv = transactionsViewModel.exportTransactionsAsCSV()
            transactionsViewModel.saveCSVToDownloads(context, csv)
        }
    }

    fun importCSV() {
        withStoragePermission {
            coroutineScope.launch {
                val result = transactionsViewModel.importTransactionsFromCSV(context)
                importResult = result
                showImportDialog = true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💳 Transactions", style = MaterialTheme.typography.titleLarge)

            Row {
                IconButton(
                    onClick = { exportCSV() },
                    enabled = transactions.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Export CSV",
                        tint = if (transactions.isNotEmpty())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                IconButton(
                    onClick = { importCSV() },
                    enabled = true
                ) {
                    Icon(
                        Icons.Default.Upload,
                        contentDescription = "Import CSV",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (currentMerchant?.role == "admin") {
                Button(
                    onClick = { showAddMerchantDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("🏪 Add Merchant")
                }
            } else {
                Button(
                    onClick = {
                        resetPaymentDialog()
                        showPaymentDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) {
                    Text("💳 Pay")
                }
            }

            Button(
                onClick = { showList = !showList },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showList) MaterialTheme.colorScheme.primary else Color(0xFF2196F3)
                )
            ) {
                Text(if (showList) "Hide List" else "Show List")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (showList) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (isAdmin) "Search by merchant or status..." else "Search by status...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "🔍 No results found" else "📭 No transactions yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (currentMerchant?.role == "admin")
                                    "🏪 Add merchants to start" else "💳 Pay with card to add transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTransactions) { t ->
                        TransactionCard(
                            transaction = t,
                            onClick = {
                                selectedTransaction = t
                                showDetailsDialog = true
                            }
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📋 Tap 'Show List' to view transactions",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (currentMerchant?.role == "admin")
                            "🏪 Tap 'Add Merchant' to add a new shop" else "💳 Pay to process new transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDetailsDialog && selectedTransaction != null) {
        val transaction = selectedTransaction!!
        val isApproved = transaction.status == "Approved"
        val isDeclined = transaction.status == "Declined"
        val isVoided = transaction.status == "Voided"
        // Only the merchant can void their own transaction - admin is
        // view-only here and never sees the Void action.
        val canVoid = isApproved && isToday(transaction.date) && !isAdmin

        AlertDialog(
            onDismissRequest = {
                showDetailsDialog = false
                selectedTransaction = null
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transaction Details")
                    IconButton(
                        onClick = {
                            showDetailsDialog = false
                            selectedTransaction = null
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            text = "✕",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column {
                    Text("Merchant: ${transaction.merchant}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Amount: ${transaction.amount} TND", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    val statusColor = when {
                        isVoided -> Color(0xFFFFA000)
                        isApproved -> Color(0xFF00C853)
                        isDeclined -> Color(0xFFFF1744)
                        else -> Color.Gray
                    }
                    Text(
                        text = "Status: ${transaction.status}",
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor
                    )
                    if (transaction.declineReason != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reason: ${transaction.declineReason}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF1744)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    Text(
                        text = "Date: ${dateFormat.format(Date(transaction.date))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canVoid) {
                        TextButton(
                            onClick = {
                                handleVoid(transaction)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFFFF6F00)
                            )
                        ) {
                            Text("Void")
                        }
                    }
                }
            }
        )
    }

    if (showPaymentDialog && currentMerchant?.role != "admin") {
        val amount = paymentAmount.toDoubleOrNull() ?: 0.0
        val pinRequired = amount > CONTACTLESS_LIMIT

        AlertDialog(
            onDismissRequest = {
                if (paymentStep != PaymentStep.PROCESSING && paymentStep != PaymentStep.RESULT) {
                    showPaymentDialog = false
                    resetPaymentDialog()
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color(0xFF00C853))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (paymentStep) {
                            PaymentStep.DETAILS -> "Payment Details"
                            PaymentStep.CARD_PRESENTATION -> "Present Card"
                            PaymentStep.PIN -> "Enter PIN"
                            PaymentStep.PROCESSING -> "Processing..."
                            PaymentStep.RESULT -> "Payment Result"
                        }
                    )
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (paymentStep) {
                        PaymentStep.DETAILS -> {
                            OutlinedTextField(
                                value = paymentAmount,
                                onValueChange = { paymentAmount = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Amount (e.g., 99.999)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Processing payment for ${currentMerchant?.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        PaymentStep.CARD_PRESENTATION -> {
                            Text(
                                text = "Merchant: ${currentMerchant?.name}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Amount: $paymentAmount TND",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF00C853)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CreditCard,
                                    contentDescription = "Card",
                                    modifier = Modifier.size(72.dp),
                                    tint = Color(0xFF00C853)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "💳 Tap, Insert, or Swipe",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Present the card to the terminal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        PaymentStep.PIN -> {
                            Text(
                                text = "Enter your PIN",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { pinInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("PIN (4 digits)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                isError = pinError,
                                supportingText = {
                                    if (pinError) {
                                        Text(
                                            text = "Incorrect PIN. Try again.",
                                            color = Color.Red
                                        )
                                    }
                                }
                            )
                        }

                        PaymentStep.PROCESSING -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Processing payment...", style = MaterialTheme.typography.bodyMedium)
                        }

                        PaymentStep.RESULT -> {
                            val isApproved = paymentResult == "Approved"
                            val isNetworkFailure = paymentResult == null
                            val boxColor = when {
                                isApproved -> Color(0xFFE8F5E9)
                                isNetworkFailure -> Color(0xFFFFF3E0)
                                else -> Color(0xFFFFEBEE)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(boxColor, RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = when {
                                            isApproved -> "✅ Approved"
                                            isNetworkFailure -> "⚠️ Couldn't confirm payment"
                                            else -> "❌ Declined"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = when {
                                            isApproved -> Color(0xFF00C853)
                                            isNetworkFailure -> Color(0xFFE65100)
                                            else -> Color(0xFFFF1744)
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when {
                                            isApproved -> "Payment successful"
                                            isNetworkFailure -> "We couldn't reach the payment server, so nothing was charged. Check your connection and try again."
                                            else -> declineReason ?: "Payment declined"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (backendError != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "⚠️ Details: $backendError",
                                            color = Color.Red,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when (paymentStep) {
                    PaymentStep.DETAILS -> {
                        TextButton(
                            onClick = {
                                val amountValue = paymentAmount.toDoubleOrNull()
                                if (amountValue != null && amountValue > 0) {
                                    paymentStep = PaymentStep.CARD_PRESENTATION
                                }
                            },
                            enabled = paymentAmount.isNotBlank() && paymentAmount.toDoubleOrNull() != null
                        ) {
                            Text("Pay")
                        }
                    }
                    PaymentStep.CARD_PRESENTATION -> {
                        TextButton(
                            onClick = {
                                if (pinRequired) {
                                    paymentStep = PaymentStep.PIN
                                } else {
                                    val amountValue = paymentAmount.toDoubleOrNull() ?: 0.0
                                    processPayment(
                                        amountValue,
                                        currentMerchant?.name ?: "Unknown",
                                        FIXED_PIN
                                    )
                                }
                            }
                        ) {
                            Text("Card Presented")
                        }
                    }
                    PaymentStep.PIN -> {
                        TextButton(
                            onClick = {
                                if (pinInput == FIXED_PIN) {
                                    pinError = false
                                    val amountValue = paymentAmount.toDoubleOrNull() ?: 0.0
                                    processPayment(
                                        amountValue,
                                        currentMerchant?.name ?: "Unknown",
                                        pinInput
                                    )
                                } else {
                                    pinError = true
                                    paymentResult = "Declined"
                                    declineReason = "Incorrect PIN"
                                    transactionsViewModel.addTransaction(
                                        amount = paymentAmount.toDoubleOrNull() ?: 0.0,
                                        merchant = currentMerchant?.name ?: "Unknown",
                                        status = "Declined",
                                        declineReason = "Incorrect PIN"
                                    )
                                    paymentStep = PaymentStep.RESULT
                                }
                            },
                            enabled = pinInput.length >= 4
                        ) {
                            Text("Confirm PIN")
                        }
                    }
                    PaymentStep.PROCESSING -> {
                        TextButton(onClick = {}, enabled = false) {
                            Text("Processing...")
                        }
                    }
                    PaymentStep.RESULT -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (paymentResult == null) {
                                TextButton(
                                    onClick = {
                                        backendError = null
                                        paymentStep = PaymentStep.CARD_PRESENTATION
                                    }
                                ) {
                                    Text("Retry")
                                }
                            }
                            TextButton(
                                onClick = {
                                    showPaymentDialog = false
                                    resetPaymentDialog()
                                }
                            ) {
                                Text(if (paymentResult == null) "Cancel" else "Done")
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (paymentStep != PaymentStep.PROCESSING && paymentStep != PaymentStep.RESULT) {
                    TextButton(
                        onClick = {
                            showPaymentDialog = false
                            resetPaymentDialog()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showAddMerchantDialog) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddMerchantDialog = false },
            title = { Text("Add Merchant") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Merchant Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (e.g. Food, Shopping)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank() && email.isNotBlank() && password.isNotBlank() && category.isNotBlank()) {
                            authViewModel.addMerchant(name, email, password, category)
                            showAddMerchantDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMerchantDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                importResult = ""
            },
            title = { Text("📥 CSV Import") },
            text = { Text(importResult) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        importResult = ""
                        transactionsViewModel.loadTransactions()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}