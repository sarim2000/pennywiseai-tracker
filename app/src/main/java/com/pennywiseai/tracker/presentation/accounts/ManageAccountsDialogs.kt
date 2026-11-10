package com.pennywiseai.tracker.presentation.accounts

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.pennywiseai.tracker.domain.model.AccountType
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.foundation.text.KeyboardOptions
import java.math.BigDecimal
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.model.getAccountType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateBalanceDialog(
    bankName: String,
    accountLast4: String,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal) -> Unit
) {
    var balanceText by remember { mutableStateOf("") }
    var isValid by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.manage_accounts_update_balance))
                Text(
                    text = AccountBalanceEntity.accountLabel(bankName, accountLast4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            TextField(
                value = balanceText,
                onValueChange = { text ->
                    if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                        balanceText = text
                        isValid = text.isNotBlank() && text.toDoubleOrNull() != null
                    }
                },
                label = { Text(stringResource(R.string.manage_accounts_new_balance)) },
                placeholder = { Text("0.00") },
                leadingIcon = {
                    Text(
                        text = CurrencyFormatter.getCurrencySymbol(CurrencyFormatter.getBankBaseCurrency(bankName)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    balanceText.toBigDecimalOrNull()?.let { balance ->
                        onConfirm(balance)
                    }
                },
                enabled = isValid,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.manage_accounts_action_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.accounts_action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateCreditCardDialog(
    bankName: String,
    accountLast4: String,
    currentOutstanding: BigDecimal,
    currentLimit: BigDecimal,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal, BigDecimal) -> Unit
) {
    var outstandingText by remember { mutableStateOf(currentOutstanding.toString()) }
    var limitText by remember { mutableStateOf(currentLimit.toString()) }
    var isValid by remember { mutableStateOf(false) }
    
    LaunchedEffect(outstandingText, limitText) {
        isValid = outstandingText.isNotBlank() && 
                  outstandingText.toDoubleOrNull() != null &&
                  limitText.isNotBlank() && 
                  limitText.toDoubleOrNull() != null
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.manage_accounts_update_credit_card))
                Text(
                    text = AccountBalanceEntity.accountLabel(bankName, accountLast4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                TextField(
                    value = outstandingText,
                    onValueChange = { text ->
                        if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                            outstandingText = text
                        }
                    },
                    label = { Text(stringResource(R.string.manage_accounts_outstanding_balance)) },
                    placeholder = { Text("0.00") },
                    leadingIcon = {
                        Text(
                            text = CurrencyFormatter.getCurrencySymbol(CurrencyFormatter.getBankBaseCurrency(bankName)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingText = {
                        Text(stringResource(R.string.manage_accounts_outstanding_balance_hint))
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                TextField(
                    value = limitText,
                    onValueChange = { text ->
                        if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                            limitText = text
                        }
                    },
                    label = { Text(stringResource(R.string.manage_accounts_credit_limit)) },
                    placeholder = { Text("50000.00") },
                    leadingIcon = {
                        Text(
                            text = CurrencyFormatter.getCurrencySymbol(CurrencyFormatter.getBankBaseCurrency(bankName)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingText = {
                        Text(stringResource(R.string.manage_accounts_credit_limit_hint))
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Show available credit
                val outstanding = outstandingText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val limit = limitText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                if (limit > BigDecimal.ZERO) {
                    val available = limit - outstanding
                    val utilization = ((outstanding.toDouble() / limit.toDouble()) * 100).toInt()
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.manage_accounts_available_credit_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = CurrencyFormatter.formatCurrency(available, CurrencyFormatter.getBankBaseCurrency(bankName)),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.manage_accounts_utilization_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "$utilization%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = when {
                                        utilization > 70 -> MaterialTheme.colorScheme.error
                                        utilization > 30 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val outstanding = outstandingText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val limit = limitText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    onConfirm(outstanding, limit)
                },
                enabled = isValid,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.manage_accounts_action_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.accounts_action_cancel))
            }
        }
    )
}

@Composable
internal fun DeleteAccountConfirmDialog(
    bankName: String,
    accountLast4: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(stringResource(R.string.manage_accounts_delete_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.manage_accounts_delete_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                text = bankName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.manage_accounts_account_ending_in, accountLast4),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.manage_accounts_delete_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.accounts_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.accounts_action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditAccountDialog(
    account: com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity,
    onDismiss: () -> Unit,
    onConfirm: (bankName: String, balance: BigDecimal, creditLimit: BigDecimal?, currency: String, type: AccountType) -> Unit
) {
    var bankNameText by remember { mutableStateOf(account.bankName) }
    var balanceText by remember { mutableStateOf(account.balance.toString()) }
    var creditLimitText by remember { mutableStateOf(account.creditLimit?.toString() ?: "") }
    // Pre-fill with the *resolved* currency (what the account actually displays), not
    // the raw stored value — an SMS-tracked non-INR account stores the INR default but
    // shows the parser currency. Seeding from the raw value would let an unrelated edit
    // silently lock the account to INR.
    var currencyText by remember {
        mutableStateOf(
            CurrencyFormatter.resolveAccountCurrency(
                sourceType = account.sourceType,
                storedCurrency = account.currency,
                bankName = account.bankName
            )
        )
    }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    // Editable for every account, SMS-created ones included (#792).
    var selectedType by remember { mutableStateOf(account.getAccountType()) }
    var showTypeMenu by remember { mutableStateOf(false) }
    val isCreditForm = selectedType == AccountType.CREDIT
    var isValid by remember { mutableStateOf(false) }

    LaunchedEffect(bankNameText, balanceText, creditLimitText, isCreditForm) {
        isValid = bankNameText.isNotBlank() &&
                  balanceText.isNotBlank() &&
                  balanceText.toDoubleOrNull() != null &&
                  (if (isCreditForm) creditLimitText.isNotBlank() && creditLimitText.toDoubleOrNull() != null else true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.manage_accounts_edit_account_title))
                Text(
                    text = selectedType.displayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Bank Name (Editable)
                TextField(
                    value = bankNameText,
                    onValueChange = { bankNameText = it },
                    label = { Text(stringResource(R.string.manage_accounts_bank_name_label)) },
                    leadingIcon = {
                        Icon(
                            selectedType.icon,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Account Number (Read-only)
                TextField(
                    value = AccountBalanceEntity.accountLabel("", account.accountLast4).trim(),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.manage_accounts_account_number_label)) },
                    enabled = false,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        disabledIndicatorColor = Color.Transparent,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.manage_accounts_read_only))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Currency (editable — lets an existing account switch currency)
                Column {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCurrencyMenu = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.add_account_currency_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$currencyText   ${CurrencyFormatter.getCurrencySymbol(currencyText)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showCurrencyMenu,
                        onDismissRequest = { showCurrencyMenu = false }
                    ) {
                        CurrencyFormatter.getSupportedCurrencies().sorted().forEach { code ->
                            DropdownMenuItem(
                                text = { Text("$code   ${CurrencyFormatter.getCurrencySymbol(code)}") },
                                onClick = {
                                    currencyText = code
                                    showCurrencyMenu = false
                                }
                            )
                        }
                    }
                }

                // Account type (#792). Changing it only changes how the balance is
                // counted from now on; past transactions are not rewritten.
                Column {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTypeMenu = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.add_account_type_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    selectedType.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                                Text(
                                    text = selectedType.displayName(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false }
                    ) {
                        AccountType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName()) },
                                leadingIcon = { Icon(type.icon, contentDescription = null) },
                                onClick = {
                                    selectedType = type
                                    showTypeMenu = false
                                }
                            )
                        }
                    }
                    if (selectedType != account.getAccountType()) {
                        Text(
                            text = stringResource(R.string.manage_accounts_type_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                    }
                }

                if (isCreditForm) {
                    // Outstanding Balance (Credit Card)
                    TextField(
                        value = balanceText,
                        onValueChange = { text ->
                            if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                                balanceText = text
                            }
                        },
                        label = { Text(stringResource(R.string.manage_accounts_outstanding_balance)) },
                        placeholder = { Text("0.00") },
                        leadingIcon = {
                            Text(
                                text = CurrencyFormatter.getCurrencySymbol(account.currency),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        supportingText = {
                            Text(stringResource(R.string.manage_accounts_outstanding_balance_hint))
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Credit Limit
                    TextField(
                        value = creditLimitText,
                        onValueChange = { text ->
                            if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                                creditLimitText = text
                            }
                        },
                        label = { Text(stringResource(R.string.manage_accounts_credit_limit)) },
                        placeholder = { Text("50000.00") },
                        leadingIcon = {
                            Text(
                                text = CurrencyFormatter.getCurrencySymbol(account.currency),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        supportingText = {
                            Text(stringResource(R.string.manage_accounts_credit_limit_hint))
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Show available credit preview
                    val outstanding = balanceText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val limit = creditLimitText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    if (limit > BigDecimal.ZERO) {
                        val available = limit - outstanding
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.manage_accounts_available_credit_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = CurrencyFormatter.formatCurrency(available, account.currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                } else {
                    // Account Balance (Regular Account)
                    TextField(
                        value = balanceText,
                        onValueChange = { text ->
                            if (text.isEmpty() || text.matches(Regex("^\\d*\\.?\\d*$"))) {
                                balanceText = text
                            }
                        },
                        label = {
                            Text(
                                if (selectedType == AccountType.LOAN) stringResource(R.string.manage_accounts_outstanding_balance)
                                else stringResource(R.string.manage_accounts_account_balance_label)
                            )
                        },
                        supportingText = if (selectedType == AccountType.LOAN) {
                            { Text(stringResource(R.string.manage_accounts_loan_outstanding_hint)) }
                        } else null,
                        placeholder = { Text("0.00") },
                        leadingIcon = {
                            Text(
                                text = CurrencyFormatter.getCurrencySymbol(account.currency),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val balance = balanceText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val creditLimit = if (isCreditForm) {
                        creditLimitText.toBigDecimalOrNull()
                    } else null
                    onConfirm(bankNameText, balance, creditLimit, currencyText, selectedType)
                },
                enabled = isValid,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.accounts_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.accounts_action_cancel))
            }
        }
    )
}
