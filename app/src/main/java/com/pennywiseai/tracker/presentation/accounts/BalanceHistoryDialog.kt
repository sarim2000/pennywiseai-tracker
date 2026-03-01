package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Suppress("DEPRECATION")
@Composable
fun BalanceHistoryDialog(
    bankName: String,
    accountLast4: String,
    balanceHistory: List<AccountBalanceEntity>,
    onDismiss: () -> Unit,
    onDeleteBalance: (Long) -> Unit,
    onUpdateBalance: (Long, BigDecimal) -> Unit
) {
    // Get the primary currency for this account
    val accountPrimaryCurrency = remember(bankName) {
        CurrencyFormatter.getBankBaseCurrency(bankName)
    }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var showDeleteConfirmation by remember { mutableStateOf<Long?>(null) }
    var expandedSources by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val clipboard = LocalClipboardManager.current
    
    Dialog(onDismissRequest = onDismiss) {
        PennyWiseCardV2(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            contentPadding = Dimensions.Padding.content
        ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Balance History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$bankName ••$accountLast4",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(Spacing.md))
                
                if (balanceHistory.isEmpty()) {
                    PennyWiseEmptyState(
                        icon = Icons.Default.History,
                        headline = "No Balance History",
                        description = "Balance records will appear here as transactions are processed.",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // Balance History List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(balanceHistory) { balance ->
                            val isLatest = balance == balanceHistory.first()
                            val isOnlyRecord = balanceHistory.size == 1
                            val isExpanded = expandedSources.contains(balance.id)
                            
                            BalanceHistoryItem(
                                balance = balance,
                                isLatest = isLatest,
                                isOnlyRecord = isOnlyRecord,
                                isExpanded = isExpanded,
                                editingId = editingId,
                                editingValue = editingValue,
                                accountPrimaryCurrency = accountPrimaryCurrency,
                                onEditClick = {
                                    editingId = balance.id
                                    editingValue = balance.balance.toPlainString()
                                },
                                onDeleteClick = {
                                    showDeleteConfirmation = balance.id
                                },
                                onEditValueChange = { value ->
                                    if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        editingValue = value
                                    }
                                },
                                onSaveEdit = {
                                    editingValue.toBigDecimalOrNull()?.let { newBalance ->
                                        onUpdateBalance(balance.id, newBalance)
                                        editingId = null
                                        editingValue = ""
                                    }
                                },
                                onCancelEdit = {
                                    editingId = null
                                    editingValue = ""
                                },
                                onToggleExpand = {
                                    expandedSources = if (isExpanded) {
                                        expandedSources - balance.id
                                    } else {
                                        expandedSources + balance.id
                                    }
                                },
                                clipboard = clipboard
                            )
                        }
                    }
                }
                
                // Info text
                Text(
                    text = "${balanceHistory.size} record(s) • Latest balance is shown in accounts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
        }
    }
    
    // Delete confirmation dialog
    showDeleteConfirmation?.let { balanceId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text("Delete Balance Record") },
            text = { Text("Are you sure you want to delete this balance record? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBalance(balanceId)
                        showDeleteConfirmation = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun BalanceHistoryItem(
    balance: AccountBalanceEntity,
    isLatest: Boolean,
    isOnlyRecord: Boolean,
    isExpanded: Boolean,
    editingId: Long?,
    editingValue: String,
    accountPrimaryCurrency: String,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditValueChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onToggleExpand: () -> Unit,
    clipboard: androidx.compose.ui.platform.ClipboardManager
) {
    PennyWiseCardV2(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLatest) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header with date and badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    // Date with icon
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = balance.timestamp.format(
                                DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Badges row
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Current badge
                        if (isLatest) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "CURRENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                                )
                            }
                        }
                        
                        // Source type badge
                        val sourceInfo: Triple<androidx.compose.ui.graphics.vector.ImageVector?, String, androidx.compose.ui.graphics.Color> = when (balance.sourceType) {
                            "TRANSACTION" -> Triple(Icons.Default.SwapHoriz, "Transaction", MaterialTheme.colorScheme.tertiary)
                            "SMS_BALANCE" -> Triple(Icons.AutoMirrored.Filled.Message, "Balance SMS", MaterialTheme.colorScheme.secondary)
                            "CARD_LINK" -> Triple(Icons.Default.CreditCard, "Card Link", MaterialTheme.colorScheme.primary)
                            "MANUAL" -> Triple(Icons.Default.Edit, "Manual", MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> if (balance.transactionId != null)
                                Triple(Icons.Default.SwapHoriz, "Transaction", MaterialTheme.colorScheme.tertiary)
                            else
                                Triple(null, "", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val (sourceIcon, sourceText, sourceColor) = sourceInfo

                        if (sourceText.isNotEmpty()) {
                            Surface(
                                color = sourceColor.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    sourceIcon?.let {
                                        Icon(
                                            imageVector = it,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = sourceColor
                                        )
                                    }
                                    Text(
                                        text = sourceText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = sourceColor
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Action buttons
                if (editingId != balance.id && !isOnlyRecord) {
                    Row {
                        IconButton(onClick = onEditClick) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit balance",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete balance",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            // Divider
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
            
            // Balance display or edit field
            if (editingId == balance.id) {
                // Edit mode
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    OutlinedTextField(
                        value = editingValue,
                        onValueChange = onEditValueChange,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New Balance") },
                        leadingIcon = {
                            Text(
                                text = CurrencyFormatter.getCurrencySymbol(accountPrimaryCurrency),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = onSaveEdit,
                            enabled = editingValue.toBigDecimalOrNull() != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = onCancelEdit,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Cancel")
                        }
                    }
                }
            } else {
                // Display mode - Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatCurrency(balance.balance, accountPrimaryCurrency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isLatest) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            
            // SMS Source (if available)
            balance.smsSource?.let { smsSource ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onToggleExpand() },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Message,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "SMS Source",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!isExpanded) {
                                        Text(
                                            text = "(${smsSource.length} chars)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                Text(
                                    text = if (isExpanded) smsSource else "${smsSource.take(80)}...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isExpanded) {
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(smsSource))
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy SMS text",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse SMS source" else "Expand SMS source",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}