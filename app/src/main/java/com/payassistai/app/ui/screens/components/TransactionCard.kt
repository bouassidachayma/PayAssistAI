package com.payassistai.app.ui.screens.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.payassistai.app.data.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatAmount(amount: Double): String {
    return String.format("%.3f", amount).trimEnd('0').trimEnd('.')
}

/**
 * Shared transaction row used by both TransactionsScreen and
 * DashboardScreen. Previously each screen had its own near-identical
 * copy (TransactionItem / DashboardTransactionItem) — consolidated
 * here so status colors/icons/formatting only need to be updated in
 * one place.
 */
@Composable
fun TransactionCard(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    val isApproved = transaction.status.equals("Approved", ignoreCase = true)
    val isDeclined = transaction.status.equals("Declined", ignoreCase = true)
    val isVoided = transaction.status.equals("Voided", ignoreCase = true)

    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(transaction.date))

    val statusColor = when {
        isVoided -> Color(0xFFFFA000)
        isApproved -> Color(0xFF00C853)
        isDeclined -> Color(0xFFFF1744)
        else -> Color.Gray
    }
    val statusIcon = when {
        isVoided -> Icons.Default.PauseCircle
        isApproved -> Icons.Default.CheckCircle
        isDeclined -> Icons.Default.Cancel
        else -> Icons.Default.Help
    }
    val statusLabel = when {
        isVoided -> "Voided"
        isApproved -> "Approved"
        isDeclined -> "Declined"
        else -> transaction.status
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isVoided -> Color(0xFFFFE082)
                isApproved -> MaterialTheme.colorScheme.primaryContainer
                isDeclined -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.merchant, style = MaterialTheme.typography.titleMedium)
                Text(formattedDate, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = statusColor
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatAmount(transaction.amount) + " TND",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Tap to view",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}