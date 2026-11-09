package com.pennywiseai.tracker.presentation.accounts

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity

@Composable
internal fun OrphanedCardItem(
    card: com.pennywiseai.tracker.data.database.entity.CardEntity,
    accounts: List<com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity>,
    onLinkToAccount: (String) -> Unit,
    onDeleteCard: (Long) -> Unit,
    onUpdateCard: (
        bankName: String,
        cardType: com.pennywiseai.tracker.data.database.entity.CardType,
        nickname: String?
    ) -> Unit = { _, _, _ -> }
) {
    var showLinkDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var expandedSource by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expandedSource = !expandedSource },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(Dimensions.Padding.content)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${card.bankName} ••${card.cardLast4}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (card.cardType == com.pennywiseai.tracker.data.database.entity.CardType.CREDIT) stringResource(R.string.manage_accounts_unlinked_credit_card) else stringResource(R.string.manage_accounts_unlinked_debit_card),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Show last known balance if available
                    if (card.lastBalance != null) {
                        Text(
                            text = stringResource(R.string.manage_accounts_last_balance, CurrencyFormatter.formatCurrency(card.lastBalance, card.currency)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Show source SMS that triggered card detection
                    if (card.lastBalanceSource != null) {
                        Text(
                            text = if (expandedSource) {
                                stringResource(R.string.manage_accounts_card_sms, card.lastBalanceSource)
                            } else {
                                stringResource(R.string.manage_accounts_card_sms_truncated, card.lastBalanceSource.take(80))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expandedSource) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showLinkDialog = true },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.manage_accounts_action_link))
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showEditDialog = true },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.manage_accounts_action_edit))
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onDeleteCard(card.id) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.accounts_action_delete))
                }
            }
        }
    }
    
    if (showLinkDialog) {
        LinkCardDialog(
            card = card,
            accounts = accounts.filter { it.bankName == card.bankName },
            onDismiss = { showLinkDialog = false },
            onConfirm = { accountLast4 ->
                onLinkToAccount(accountLast4)
                showLinkDialog = false
            }
        )
    }

    if (showEditDialog) {
        EditCardDialog(
            card = card,
            onDismiss = { showEditDialog = false },
            onConfirm = { bankName, cardType, nickname ->
                onUpdateCard(bankName, cardType, nickname)
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCardDialog(
    card: com.pennywiseai.tracker.data.database.entity.CardEntity,
    onDismiss: () -> Unit,
    onConfirm: (
        bankName: String,
        cardType: com.pennywiseai.tracker.data.database.entity.CardType,
        nickname: String?
    ) -> Unit
) {
    var bankName by remember { mutableStateOf(card.bankName) }
    var cardType by remember { mutableStateOf(card.cardType) }
    var nickname by remember { mutableStateOf(card.nickname.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_accounts_edit_card_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "••${card.cardLast4}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text(stringResource(R.string.manage_accounts_bank_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = stringResource(R.string.manage_accounts_card_type_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ConnectedChoiceRow(
                        options = listOf(
                            com.pennywiseai.tracker.data.database.entity.CardType.DEBIT,
                            com.pennywiseai.tracker.data.database.entity.CardType.CREDIT
                        ),
                        selected = cardType,
                        onSelect = { cardType = it },
                        label = { type ->
                            stringResource(
                                if (type == com.pennywiseai.tracker.data.database.entity.CardType.CREDIT) {
                                    R.string.manage_accounts_card_type_credit
                                } else {
                                    R.string.manage_accounts_card_type_debit
                                }
                            )
                        }
                    )
                }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(stringResource(R.string.manage_accounts_nickname_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(bankName, cardType, nickname.ifBlank { null })
                },
                enabled = bankName.isNotBlank(),
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.accounts_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.accounts_action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkCardDialog(
    card: com.pennywiseai.tracker.data.database.entity.CardEntity,
    accounts: List<com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedAccount by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.manage_accounts_link_card_title))
                Text(
                    text = "${card.bankName} ••${card.cardLast4}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (accounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.manage_accounts_link_no_accounts, card.bankName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.manage_accounts_link_select_account),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    accounts.forEach { account ->
                        Surface(
                            onClick = { selectedAccount = account.accountLast4 },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (selectedAccount == account.accountLast4) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(
                                1.dp,
                                if (selectedAccount == account.accountLast4) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    if (account.accountLast4 != AccountBalanceEntity.WALLET_ACCOUNT_MARKER) {
                                        Text(
                                            text = "••${account.accountLast4}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selectedAccount == account.accountLast4) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(Dimensions.Icon.medium)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedAccount?.let(onConfirm) },
                enabled = selectedAccount != null,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.manage_accounts_action_link))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.accounts_action_cancel))
            }
        }
    )
}
