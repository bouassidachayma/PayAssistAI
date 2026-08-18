package com.payassistai.app.ui.screens

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payassistai.app.data.TransactionEntity
import com.payassistai.app.ui.screens.components.TransactionCard
import com.payassistai.app.viewmodels.AuthViewModel
import com.payassistai.app.viewmodels.TransactionsViewModel
import java.text.SimpleDateFormat
import java.util.*

// Private formatAmount – only used in this file
private fun formatAmount(amount: Double): String {
    return String.format("%.3f", amount).trimEnd('0').trimEnd('.')
}

private fun normalizeToLocalStartOfDay(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMillis
    }
    return Calendar.getInstance().apply {
        set(
            utcCal.get(Calendar.YEAR),
            utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH),
            0, 0, 0
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

enum class FilterMode { ALL, DATE, MERCHANT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    transactionsViewModel: TransactionsViewModel,
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val transactions by transactionsViewModel.transactions.collectAsState()
    val currentMerchant by authViewModel.currentMerchant.collectAsState()
    val isAdmin = currentMerchant?.role == "admin"

    var filterMode by remember { mutableStateOf(FilterMode.ALL) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var merchantFilter by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var selectedMerchant by remember { mutableStateOf<String?>(null) }
    var showDropdown by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    // Date filter logic (inclusive)
    val filteredTransactions = transactions.filter { tx ->
        var matches = true
        when (filterMode) {
            FilterMode.ALL -> {}
            FilterMode.DATE -> {
                fun endOfDay(date: Long): Long = date + (24 * 60 * 60 * 1000) - 1
                if (startDate != null && endDate != null) {
                    matches = matches && tx.date >= startDate!! && tx.date <= endOfDay(endDate!!)
                } else if (startDate != null) {
                    matches = matches && tx.date >= startDate!! && tx.date <= endOfDay(startDate!!)
                } else if (endDate != null) {
                    matches = matches && tx.date <= endOfDay(endDate!!)
                }
            }
            FilterMode.MERCHANT -> {
                if (merchantFilter.isNotBlank()) {
                    matches = matches && tx.merchant.contains(merchantFilter, ignoreCase = true)
                }
                if (selectedMerchant != null) {
                    matches = matches && tx.merchant.equals(selectedMerchant, ignoreCase = true)
                }
            }
        }
        matches
    }

    val sortedTransactions = filteredTransactions.sortedByDescending { it.date }
    val displayedTransactions = if (filterMode == FilterMode.ALL) sortedTransactions.take(10) else sortedTransactions

    val filteredApproved = filteredTransactions.filter { it.status == "Approved" }
    val filteredVoided = filteredTransactions.filter { it.status == "Voided" }
    val filteredDeclined = filteredTransactions.filter { it.status == "Declined" }
    val totalSales = filteredApproved.sumOf { it.amount }

    // Merchant: status breakdown
    val merchantStatusData = listOf(
        "Approved" to filteredApproved.sumOf { it.amount },
        "Voided" to filteredVoided.sumOf { it.amount },
        "Declined" to filteredDeclined.sumOf { it.amount }
    ).filter { it.second > 0 }

    // Admin: category breakdown
    val categoryMap = if (isAdmin) {
        filteredApproved
            .groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .filter { it.value > 0 }
    } else {
        emptyMap()
    }

    // Admin: merchant breakdown (for MERCHANT filter)
    val statusData = if (isAdmin && filterMode == FilterMode.MERCHANT && selectedMerchant != null) {
        val merchantTxs = filteredTransactions.filter { it.merchant.equals(selectedMerchant, ignoreCase = true) }
        listOf(
            "Approved" to merchantTxs.filter { it.status == "Approved" }.sumOf { it.amount },
            "Voided" to merchantTxs.filter { it.status == "Voided" }.sumOf { it.amount },
            "Declined" to merchantTxs.filter { it.status == "Declined" }.sumOf { it.amount }
        ).filter { it.second > 0 }
    } else {
        emptyList()
    }

    val categoryColors = listOf(
        Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF2196F3),
        Color(0xFF9C27B0), Color(0xFFFF5722), Color(0xFF009688),
        Color(0xFFE91E63), Color(0xFF673AB7)
    )
    val statusColors = listOf(
        Color(0xFF00C853), // Approved
        Color(0xFFFFA000), // Voided
        Color(0xFFFF1744)  // Declined
    )

    val chartData = if (isAdmin) {
        when (filterMode) {
            FilterMode.MERCHANT -> statusData.map { it.second }
            else -> categoryMap.entries.map { it.value }
        }
    } else {
        merchantStatusData.map { it.second }
    }

    val chartColors = if (isAdmin) {
        when (filterMode) {
            FilterMode.MERCHANT -> statusColors.take(statusData.size)
            else -> categoryColors.take(categoryMap.size)
        }
    } else {
        statusColors.take(merchantStatusData.size)
    }

    val chartLabels = if (isAdmin) {
        when (filterMode) {
            FilterMode.MERCHANT -> statusData.map { it.first }
            else -> categoryMap.keys.toList()
        }
    } else {
        merchantStatusData.map { it.first }
    }

    val chartTotal = chartData.sum()
    val centerTotal = filteredApproved.sumOf { it.amount }

    fun handleVoid(transaction: TransactionEntity) {
        transactionsViewModel.voidTransaction(transaction)
        showDetailsDialog = false
        selectedTransaction = null
    }

    fun isToday(timestamp: Long): Boolean {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val txCal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return txCal.timeInMillis == cal.timeInMillis
    }

    val isFilterActive = when (filterMode) {
        FilterMode.DATE -> (startDate != null || endDate != null)
        FilterMode.MERCHANT -> (selectedMerchant != null)
        else -> true
    }

    // ---------- UI ----------
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sales Dashboard", style = MaterialTheme.typography.headlineLarge)
        }
        Spacer(Modifier.height(8.dp))

        // Stats Row – with formatted amount
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = Color(0xFF4CAF50))
                    Text("Total Sales", style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatAmount(totalSales) + " TND",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00C853))
                    Text("Approved", style = MaterialTheme.typography.bodySmall, color = Color(0xFF00C853))
                    Text(filteredApproved.size.toString(), style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PauseCircle, contentDescription = null, tint = Color(0xFFFFA000))
                    Text("Voided", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFA000))
                    Text(filteredVoided.size.toString(), style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFFF1744))
                    Text("Declined", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF1744))
                    Text(filteredDeclined.size.toString(), style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        // Filters
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔍 Filters", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showFilters = !showFilters }) {
                        Text(if (showFilters) "Hide" else "Show")
                    }
                }

                if (showFilters) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterMode == FilterMode.ALL,
                            onClick = {
                                filterMode = FilterMode.ALL
                                startDate = null
                                endDate = null
                                merchantFilter = ""
                                selectedMerchant = null
                                showDropdown = false
                            },
                            label = { Text("All") },
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(if (isAdmin) 1f else 1f)
                        )
                        FilterChip(
                            selected = filterMode == FilterMode.DATE,
                            onClick = {
                                filterMode = FilterMode.DATE
                                merchantFilter = ""
                                selectedMerchant = null
                                showDropdown = false
                            },
                            label = { Text("Date") },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(if (isAdmin) 1f else 1f)
                        )
                        if (isAdmin) {
                            FilterChip(
                                selected = filterMode == FilterMode.MERCHANT,
                                onClick = {
                                    filterMode = FilterMode.MERCHANT
                                    startDate = null
                                    endDate = null
                                },
                                label = { Text("Merchant") },
                                leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    when (filterMode) {
                        FilterMode.DATE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showStartDatePicker = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (startDate != null) {
                                            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(startDate!!))
                                        } else "Start Date"
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showEndDatePicker = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (endDate != null) {
                                            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(endDate!!))
                                        } else "End Date"
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { startDate = null; endDate = null },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Clear Dates")
                            }
                        }

                        FilterMode.MERCHANT -> {
                            if (isAdmin) {
                                OutlinedTextField(
                                    value = merchantFilter,
                                    onValueChange = {
                                        merchantFilter = it
                                        showDropdown = it.isNotBlank()
                                        if (selectedMerchant != null && !it.equals(selectedMerchant, ignoreCase = true)) {
                                            selectedMerchant = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Search merchant...") },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                                )
                                val matchingMerchants = transactions
                                    .map { it.merchant }
                                    .distinct()
                                    .filter { it.contains(merchantFilter, ignoreCase = true) && it != selectedMerchant }
                                    .take(5)
                                if (showDropdown && merchantFilter.isNotBlank() && matchingMerchants.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .padding(4.dp)
                                            .heightIn(max = 150.dp)
                                    ) {
                                        matchingMerchants.forEach { merchant ->
                                            Text(
                                                text = merchant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedMerchant = merchant
                                                        merchantFilter = merchant
                                                        showDropdown = false
                                                    }
                                                    .padding(8.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Donut Chart
        if (chartData.isNotEmpty()) {
            val chartTitle = if (isAdmin && filterMode == FilterMode.MERCHANT) {
                "Status Breakdown"
            } else if (isAdmin) {
                "Spending by Category"
            } else {
                "Sales Breakdown"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(chartTitle, style = MaterialTheme.typography.titleLarge)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DonutChartWithTotal(
                        data = chartData,
                        colors = chartColors,
                        total = centerTotal
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chartLabels.zip(chartColors).forEach { (label, color) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(color, RoundedCornerShape(2.dp))
                                )
                                val percentage = if (chartTotal > 0) (chartData[chartLabels.indexOf(label)] / chartTotal * 100).toInt() else 0
                                Text(
                                    text = "$label ($percentage%)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        } else {
            val message = when (filterMode) {
                FilterMode.ALL -> "No sales data to display."
                FilterMode.DATE -> if (startDate == null && endDate == null) "Select dates to see data." else "No data for the selected date range."
                FilterMode.MERCHANT -> if (selectedMerchant == null) "Select a merchant to see data." else "No data for the selected merchant."
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }

        // Transaction List
        val showList = when (filterMode) {
            FilterMode.ALL -> displayedTransactions.isNotEmpty()
            else -> isFilterActive && filteredTransactions.isNotEmpty()
        }
        if (showList) {
            val title = when (filterMode) {
                FilterMode.ALL -> if (isAdmin) "Recent Transactions" else "Your Recent Transactions"
                FilterMode.DATE -> "Transactions (${filteredTransactions.size})"
                FilterMode.MERCHANT -> "Transactions for $selectedMerchant (${filteredTransactions.size})"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayedTransactions) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        onClick = {
                            selectedTransaction = transaction
                            showDetailsDialog = true
                        }
                    )
                }
            }
        } else if (filterMode != FilterMode.ALL && isFilterActive && filteredTransactions.isEmpty()) {
            Text("No transactions found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Transaction details dialog
    if (showDetailsDialog && selectedTransaction != null) {
        val transaction = selectedTransaction!!
        val isApproved = transaction.status == "Approved"
        val isDeclined = transaction.status == "Declined"
        val isVoided = transaction.status == "Voided"
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
                        Text("✕", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column {
                    Text("Merchant: ${transaction.merchant}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Amount: ${transaction.amount} TND", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Reason: ${transaction.declineReason}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF1744)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    Text(
                        text = "Date: ${dateFormat.format(Date(transaction.date))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canVoid) {
                        TextButton(
                            onClick = {
                                handleVoid(transaction)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6F00))
                        ) {
                            Text("Void")
                        }
                    }
                }
            }
        )
    }

    // Date pickers
    if (showStartDatePicker) {
        FullScreenDatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            onDateSelected = { dateInMillis: Long? ->
                if (dateInMillis != null) {
                    startDate = normalizeToLocalStartOfDay(dateInMillis)
                    showStartDatePicker = false
                }
            }
        )
    }
    if (showEndDatePicker) {
        FullScreenDatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            onDateSelected = { dateInMillis: Long? ->
                if (dateInMillis != null) {
                    endDate = normalizeToLocalStartOfDay(dateInMillis)
                    showEndDatePicker = false
                }
            }
        )
    }
}

// =============================================================
// Donut Chart with formatted total
// =============================================================
@Composable
fun DonutChartWithTotal(
    data: List<Double>,
    colors: List<Color>,
    total: Double
) {
    if (data.isEmpty()) return
    val surfaceColor = MaterialTheme.colorScheme.surface
    val sum = data.sum()
    Canvas(modifier = Modifier.size(150.dp)) {
        val radius = size.minDimension / 2 * 0.8f
        val center = Offset(size.width / 2, size.height / 2)
        var startAngle = -90f
        data.forEachIndexed { index, value ->
            val sweepAngle = (value / sum * 360).toFloat()
            if (sweepAngle > 0) {
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }
            startAngle += sweepAngle
        }
        val holeRadius = radius * 0.5f
        drawCircle(color = surfaceColor, radius = holeRadius, center = center)
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        }
        val displayText = formatAmount(total) + " TND"
        nativeCanvas.drawText(displayText, center.x, center.y + 10, textPaint)
    }
}

// =============================================================
// Date Picker Dialog (unchanged)
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenDatePickerDialog(
    onDismissRequest: () -> Unit,
    onDateSelected: (Long?) -> Unit
) {
    val datePickerState = rememberDatePickerState()
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Select Date", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                DatePicker(
                    state = datePickerState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                        },
                        enabled = datePickerState.selectedDateMillis != null
                    ) { Text("OK") }
                }
            }
        }
    }
}