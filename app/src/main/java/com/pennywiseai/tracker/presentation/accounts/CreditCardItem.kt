package com.pennywiseai.tracker.presentation.accounts

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.ui.text.style.TextOverflow
import java.math.BigDecimal

@Composable
internal fun CreditCardItem(
    card: com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity,
    isHidden: Boolean,
    onToggleVisibility: () -> Unit,
    onUpdateBalance: () -> Unit,
    onViewHistory: () -> Unit,
    onDeleteAccount: () -> Unit,
    onEditAccount: () -> Unit = {},
    onSetStatementDay: (Int?) -> Unit = {}
) {
    var showStatementDayDialog by remember { mutableStateOf(false) }
    val isManualAccount = card.sourceType == "MANUAL"
    val available = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
    val utilization = if (card.creditLimit != null && card.creditLimit > BigDecimal.ZERO) {
        ((card.balance.toDouble() / card.creditLimit.toDouble()) * 100).toInt()
    } else {
        0
    }
    
    val utilizationColor = when {
        utilization > 70 -> MaterialTheme.colorScheme.error
        utilization > 30 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHidden) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            // Set outright: a translucent container matches no theme colour, so the default went black.
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Credit Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = card.bankName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isHidden) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = stringResource(R.string.manage_accounts_hidden_badge),
                                    modifier = Modifier.size(Dimensions.Icon.small),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Masked card number on its own line below the name (#465).
                        Text(
                            text = "••${card.accountLast4}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Credit Card Details
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Outstanding Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.manage_accounts_outstanding),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatCurrency(card.balance, card.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (card.balance > BigDecimal.ZERO) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                
                // Available Credit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.manage_accounts_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatCurrency(available, card.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Credit Limit with Utilization
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.manage_accounts_credit_limit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = CurrencyFormatter.formatCurrency(card.creditLimit ?: BigDecimal.ZERO, card.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.manage_accounts_utilization_used, utilization),
                            style = MaterialTheme.typography.bodySmall,
                            color = utilizationColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Action Buttons - Primary action + overflow menu
            var showMenu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary action
                OutlinedButton(
                    onClick = if (isManualAccount) onEditAccount else onUpdateBalance,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(if (isManualAccount) stringResource(R.string.manage_accounts_action_edit) else stringResource(R.string.manage_accounts_action_update))
                }

                // Overflow menu
                Box {
                    IconButton(onClick = { showMenu = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.manage_accounts_more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // SMS-tracked accounts reach the edit dialog (name, currency,
                        // type) from here; manual ones have it as the primary button.
                        if (!isManualAccount) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.manage_accounts_action_edit)) },
                                onClick = {
                                    showMenu = false
                                    onEditAccount()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manage_accounts_menu_history)) },
                            onClick = {
                                showMenu = false
                                onViewHistory()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isHidden) stringResource(R.string.manage_accounts_menu_show) else stringResource(R.string.manage_accounts_menu_hide)) },
                            onClick = {
                                showMenu = false
                                onToggleVisibility()
                            },
                            leadingIcon = {
                                Icon(
                                    if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (card.statementDay != null) stringResource(R.string.manage_accounts_menu_statement_date, card.statementDay)
                                    else stringResource(R.string.manage_accounts_menu_set_statement_date)
                                )
                            },
                            onClick = {
                                showMenu = false
                                showStatementDayDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.accounts_action_delete)) },
                            onClick = {
                                showMenu = false
                                onDeleteAccount()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
        }
    }

    if (showStatementDayDialog) {
        StatementDayPickerDialog(
            currentDay = card.statementDay,
            onDismiss = { showStatementDayDialog = false },
            onConfirm = { day ->
                onSetStatementDay(day)
                showStatementDayDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementDayPickerDialog(
    currentDay: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    var selectedDay by remember { mutableIntStateOf(currentDay ?: 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_accounts_statement_dialog_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.manage_accounts_statement_dialog_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { if (selectedDay > 1) selectedDay-- },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.manage_accounts_decrease))
                    }
                    Text(
                        text = "$selectedDay",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = Spacing.lg)
                    )
                    IconButton(
                        onClick = { if (selectedDay < 28) selectedDay++ },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.manage_accounts_increase))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDay) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.accounts_action_save))
            }
        },
        dismissButton = {
            if (currentDay != null) {
                TextButton(onClick = { onConfirm(null) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.manage_accounts_action_clear))
                }
            } else {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.accounts_action_cancel))
                }
            }
        }
    )
}
