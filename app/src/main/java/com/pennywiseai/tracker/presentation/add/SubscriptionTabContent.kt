package com.pennywiseai.tracker.presentation.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.model.displayName
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.presentation.accounts.AccountType
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter

private val subTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val subBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
private val subMiddleShape = RoundedCornerShape(4.dp)
private val subFullShape = RoundedCornerShape(16.dp)

@Composable
private fun subFilledColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    disabledIndicatorColor = Color.Transparent,
    disabledLabelColor = MaterialTheme.colorScheme.primary,
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit
) {
    val uiState by viewModel.subscriptionUiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showBillingCycleMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }

    val billingCycles = listOf("Monthly", "Quarterly", "Semi-Annual", "Annual", "Weekly")

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content, vertical = Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Error Card
            uiState.error?.let { errorMessage ->
                PennyWiseCardV2(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    contentPadding = 12.dp
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Income / Expense direction toggle (#371). Income subscriptions
            // get phantom-created on schedule (for accounts that don't send
            // SMS — wallets, allowances). Expense subscriptions match
            // incoming bank-debit SMS like today.
            val isIncome = uiState.direction ==
                com.pennywiseai.tracker.data.database.entity.SubscriptionDirection.INCOME
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isIncome,
                    onClick = {
                        viewModel.updateSubscriptionDirection(
                            com.pennywiseai.tracker.data.database.entity.SubscriptionDirection.EXPENSE
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Expense") }
                )
                SegmentedButton(
                    selected = isIncome,
                    onClick = {
                        viewModel.updateSubscriptionDirection(
                            com.pennywiseai.tracker.data.database.entity.SubscriptionDirection.INCOME
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Income") }
                )
            }

            // Info Card — copy adapts to the chosen direction.
            PennyWiseCardV2(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                contentPadding = 12.dp
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                    Text(
                        text = if (isIncome)
                            "Track recurring income (wallet top-ups, allowance). A transaction is auto-created on each scheduled date."
                        else
                            "Track recurring expenses. You'll need to add transactions manually each month.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Amount row: currency + amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                ExposedDropdownMenuBox(
                    expanded = showCurrencyMenu,
                    onExpandedChange = { showCurrencyMenu = it },
                    modifier = Modifier.width(130.dp)
                ) {
                    TextField(
                        value = "${CurrencyFormatter.getCurrencySymbol(uiState.currency)} ${uiState.currency}",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine = true,
                        shape = subFullShape,
                        colors = subFilledColors()
                    )
                    ExposedDropdownMenu(
                        expanded = showCurrencyMenu,
                        onDismissRequest = { showCurrencyMenu = false }
                    ) {
                        CurrencyFormatter.getSupportedCurrencies().forEach { currency ->
                            DropdownMenuItem(
                                text = { Text("${CurrencyFormatter.getCurrencySymbol(currency)} $currency") },
                                onClick = {
                                    viewModel.updateSubscriptionCurrency(currency)
                                    showCurrencyMenu = false
                                }
                            )
                        }
                    }
                }

                TextField(
                    value = uiState.amount,
                    onValueChange = viewModel::updateSubscriptionAmount,
                    label = { Text("Amount *", fontWeight = FontWeight.SemiBold) },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = uiState.amountError != null,
                    supportingText = uiState.amountError?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = subFullShape,
                    colors = subFilledColors()
                )
            }

            // Billing cycle + Next payment date row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Billing cycle
                ExposedDropdownMenuBox(
                    expanded = showBillingCycleMenu,
                    onExpandedChange = { showBillingCycleMenu = it },
                    modifier = Modifier.weight(1f)
                ) {
                    TextField(
                        value = uiState.billingCycle,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Billing Cycle", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.EventRepeat, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine = true,
                        shape = subFullShape,
                        colors = subFilledColors()
                    )

                    ExposedDropdownMenu(
                        expanded = showBillingCycleMenu,
                        onDismissRequest = { showBillingCycleMenu = false }
                    ) {
                        billingCycles.forEach { cycle ->
                            DropdownMenuItem(
                                text = { Text(cycle) },
                                onClick = {
                                    viewModel.updateSubscriptionBillingCycle(cycle)
                                    showBillingCycleMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.sm))

                // Next payment date
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                        )
                        .padding(Spacing.sm)
                        .clickable(
                            onClick = { showDatePicker = true },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Column {
                            Text(
                                text = uiState.nextPaymentDate.format(DateTimeFormatter.ofPattern("yyyy")),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = uiState.nextPaymentDate.format(DateTimeFormatter.ofPattern("dd MMMM")),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Service Name + Category + Notes (connected group)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                TextField(
                    value = uiState.serviceName,
                    onValueChange = viewModel::updateSubscriptionService,
                    label = { Text("Service Name *", fontWeight = FontWeight.SemiBold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = subTopShape,
                    leadingIcon = { Icon(Icons.Default.Subscriptions, contentDescription = null) },
                    isError = uiState.serviceError != null,
                    supportingText = uiState.serviceError?.let { { Text(it) } },
                    colors = subFilledColors()
                )

                ExposedDropdownMenuBox(
                    expanded = showCategoryMenu,
                    onExpandedChange = { showCategoryMenu = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = uiState.category,
                        onValueChange = {},
                        label = { Text("Category", fontWeight = FontWeight.SemiBold) },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = subMiddleShape,
                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                        isError = uiState.categoryError != null,
                        supportingText = uiState.categoryError?.let { { Text(it) } },
                        colors = subFilledColors()
                    )

                    ExposedDropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    viewModel.updateSubscriptionCategory(category.name)
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }

                TextField(
                    value = uiState.notes,
                    onValueChange = viewModel::updateSubscriptionNotes,
                    label = { Text("Notes (Optional)", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = subBottomShape,
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    colors = subFilledColors()
                )
            }

            // ── Funding account (optional) ──
            // When set, marking this subscription paid (or an auto-created
            // scheduled income) moves the chosen account's balance. Leaving it
            // unset keeps the subscription unlinked, exactly like before. (#570)
            Card(
                onClick = { showAccountMenu = true },
                modifier = Modifier.fillMaxWidth(),
                shape = subFullShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        when (uiState.selectedAccount?.getAccountType()) {
                            AccountType.CASH -> Icons.Default.Money
                            AccountType.CREDIT -> Icons.Default.CreditCard
                            AccountType.SAVINGS, AccountType.CURRENT -> Icons.Default.AccountBalance
                            null -> Icons.Default.AccountBalance
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.selectedAccount?.bankName ?: "Paid from (optional)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (uiState.selectedAccount != null)
                                MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.selectedAccount != null &&
                            uiState.selectedAccount?.accountLast4 != AccountBalanceEntity.WALLET_ACCOUNT_MARKER) {
                            Text(
                                text = "••${uiState.selectedAccount?.accountLast4}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (uiState.selectedAccount != null) {
                        IconButton(
                            onClick = { viewModel.updateSubscriptionAccount(null) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Account selection dropdown menu
            DropdownMenu(
                expanded = showAccountMenu,
                onDismissRequest = { showAccountMenu = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("No account")
                            Text(
                                "Won't affect any balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        viewModel.updateSubscriptionAccount(null)
                        showAccountMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) }
                )
                HorizontalDivider()
                val groupedAccounts = accounts.groupBy { it.getAccountType() }
                groupedAccounts.forEach { (accountType, accountList) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = accountType.displayName(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = {},
                        enabled = false
                    )
                    accountList.forEach { account ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(AccountBalanceEntity.accountLabel(account.bankName, account.accountLast4))
                                    Text(
                                        CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                viewModel.updateSubscriptionAccount(account)
                                showAccountMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    when (accountType) {
                                        AccountType.CASH -> Icons.Default.Money
                                        AccountType.CREDIT -> Icons.Default.CreditCard
                                        else -> Icons.Default.AccountBalance
                                    },
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (uiState.selectedAccount?.id == account.id) {
                                    Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }

            // Bottom padding for save button overlay
            Spacer(modifier = Modifier.height(72.dp))
        }

        // Sticky Save Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = { viewModel.saveSubscription(onSuccess = onSave) },
                enabled = uiState.isValid && !uiState.isLoading,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = Dimensions.Padding.content)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.Icon.small),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Save", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.nextPaymentDate
                .atStartOfDay()
                .toInstant(java.time.ZoneOffset.UTC)
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.updateSubscriptionNextPaymentDate(millis)
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
