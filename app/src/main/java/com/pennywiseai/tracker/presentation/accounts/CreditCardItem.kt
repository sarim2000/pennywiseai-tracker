package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.ui.components.WavyMeter
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

@Composable
internal fun CreditCardItem(
    card: AccountBalanceEntity,
    isHidden: Boolean,
    position: ListItemPosition = ListItemPosition.Single,
    onToggleVisibility: () -> Unit,
    onUpdateBalance: () -> Unit,
    onViewHistory: () -> Unit,
    onDeleteAccount: () -> Unit,
    onEditAccount: () -> Unit = {},
    onSetStatementDay: (Int?) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showStatementDayDialog by remember { mutableStateOf(false) }
    val isManualAccount = card.sourceType == "MANUAL"
    val limit = card.creditLimit?.takeIf { it > BigDecimal.ZERO }
    val available = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
    val utilization = if (limit != null) {
        ((card.balance.toDouble() / limit.toDouble()) * 100).toInt()
    } else {
        0
    }

    val utilizationColor = when {
        utilization > 70 -> MaterialTheme.colorScheme.error
        utilization > 30 -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.primary
    }

    Box {
        AccountRowFrame(
            position = position,
            isHidden = isHidden,
            onClick = { showMenu = true },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountBadge(name = card.bankName, kind = AccountBadgeKind.CARD)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = card.bankName,
                            style = PennyWiseText.rowTitle,
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
                        style = PennyWiseText.rowSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(card.balance, card.currency),
                        style = PennyWiseText.amountRow,
                        fontWeight = FontWeight.Bold,
                        color = if (card.balance > BigDecimal.ZERO) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.manage_accounts_outstanding),
                        style = PennyWiseText.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // How much of the limit is used, as a bar under the name.
            if (limit != null) {
                Column(
                    modifier = Modifier.padding(start = AccountRowIndent),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    WavyMeter(
                        fraction = (utilization / 100f).coerceIn(0f, 1f),
                        color = utilizationColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        dense = true,
                    )
                    Text(
                        text = stringResource(
                            R.string.manage_accounts_limit_used,
                            utilization,
                            CurrencyFormatter.formatCurrency(limit, card.currency)
                        ),
                        style = PennyWiseText.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.manage_accounts_available_amount,
                            CurrencyFormatter.formatCurrency(available, card.currency)
                        ),
                        style = PennyWiseText.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = MaterialTheme.shapes.large
        ) {
            // Manual cards are edited outright; SMS-tracked ones get their figures
            // corrected first, and reach the edit dialog (name, currency, type) next.
            if (!isManualAccount) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.manage_accounts_action_update)) },
                    onClick = {
                        showMenu = false
                        onUpdateBalance()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_accounts_action_edit)) },
                onClick = {
                    showMenu = false
                    onEditAccount()
                },
                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_accounts_menu_history)) },
                onClick = {
                    showMenu = false
                    onViewHistory()
                },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
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
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
            )
            DeleteAccountMenuItem(onClick = {
                showMenu = false
                onDeleteAccount()
            })
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
