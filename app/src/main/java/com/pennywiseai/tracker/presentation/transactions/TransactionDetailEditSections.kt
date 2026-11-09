package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.pennywiseai.tracker.presentation.add.ReceiptPickerSection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.SplitEditor
import com.pennywiseai.tracker.ui.components.SplitItem
import com.pennywiseai.tracker.ui.components.TagInputField
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.money

// Reusable filled field colors for edit mode
@Composable
internal fun editFilledColors() = TextFieldDefaults.colors(
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

private val editTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val editMiddleShape = RoundedCornerShape(4.dp)
private val editBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
internal val editFullShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditableTransactionHeader(
    transaction: TransactionEntity,
    viewModel: TransactionDetailViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Amount and Currency
        val primaryCurrency by viewModel.primaryCurrency.collectAsStateWithLifecycle()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            CurrencyDropdown(
                selectedCurrency = transaction.currency.ifEmpty { primaryCurrency },
                onCurrencySelected = { viewModel.updateCurrency(it) },
                modifier = Modifier.width(130.dp)
            )

            TextField(
                value = transaction.amount.stripTrailingZeros().toPlainString(),
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text(stringResource(R.string.txn_detail_field_amount), fontWeight = FontWeight.SemiBold) },
                textStyle = MaterialTheme.typography.headlineSmall.money(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = editFullShape,
                colors = editFilledColors()
            )
        }

        // Merchant + Description (connected group)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            TextField(
                value = transaction.merchantName,
                onValueChange = { viewModel.updateMerchantName(it) },
                label = { Text(stringResource(R.string.txn_detail_field_merchant), fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    BrandIcon(
                        merchantName = transaction.merchantName,
                        category = transaction.category,
                        size = 24.dp,
                        showBackground = false
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = editTopShape,
                isError = transaction.merchantName.isBlank(),
                colors = editFilledColors()
            )

            // Display alias (#583): shown in place of the merchant everywhere,
            // for all transactions from it. The raw merchant above is unchanged.
            val merchantAlias by viewModel.merchantAlias.collectAsStateWithLifecycle()
            TextField(
                value = merchantAlias,
                onValueChange = { viewModel.updateMerchantAlias(it) },
                label = { Text(stringResource(R.string.txn_detail_field_alias), fontWeight = FontWeight.SemiBold) },
                placeholder = { Text(stringResource(R.string.txn_detail_field_alias_placeholder)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Badge,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = editMiddleShape,
                colors = editFilledColors()
            )

            TextField(
                value = transaction.description ?: "",
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text(stringResource(R.string.txn_detail_field_description), fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = editMiddleShape,
                colors = editFilledColors()
            )

            // Only the extracted/typed reference; the view mode's smsSender
            // fallback is display-only and must never be saved as a reference.
            TextField(
                value = transaction.reference ?: "",
                onValueChange = { viewModel.updateReference(it) },
                label = { Text(stringResource(R.string.txn_detail_field_reference), fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = editBottomShape,
                colors = editFilledColors()
            )
        }

        // Tags (optional) — create or select existing
        val editTags by viewModel.editableTags.collectAsStateWithLifecycle()
        val allTagNames by viewModel.allTagNames.collectAsStateWithLifecycle()
        TagInputField(
            selectedTags = editTags,
            allTags = allTagNames,
            onAddTag = { viewModel.addTag(it) },
            onRemoveTag = { viewModel.removeTag(it) }
        )
        // #752: mirrors the category checkbox below — saved as a Smart Rule.
        if (editTags.isNotEmpty()) {
            val applyTagsToAll by viewModel.applyTagsToAllFromMerchant.collectAsStateWithLifecycle()
            // The rule keys on the merchant as saved before this edit, so name that one.
            val originalMerchant = viewModel.transaction.collectAsStateWithLifecycle().value?.merchantName
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = applyTagsToAll,
                    role = Role.Checkbox,
                    onValueChange = { viewModel.toggleApplyTagsToAllFromMerchant() }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = applyTagsToAll,
                    onCheckedChange = null,
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
                Text(
                    text = stringResource(R.string.txn_detail_apply_tags_to_merchant, originalMerchant ?: transaction.merchantName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Transaction Type
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            TransactionType.values().forEach { type ->
                FilterChip(
                    selected = transaction.transactionType == type,
                    shapes = FilterChipDefaults.shapes(),
                    onClick = { viewModel.updateTransactionType(type) },
                    label = {
                        Text(
                            stringResource(transactionTypeLabel(type)),
                            maxLines = 1
                        )
                    },
                    leadingIcon = if (transaction.transactionType == type) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderWidth = 0.dp,
                        selected = transaction.transactionType == type,
                        enabled = true
                    )
                )
            }
        }

        // Date and Time
        DateTimeField(
            dateTime = transaction.dateTime,
            onDateTimeChange = { viewModel.updateDateTime(it) }
        )

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditableExtractedInfoCard(
    transaction: TransactionEntity,
    applyToAllFromMerchant: Boolean,
    updateExistingTransactions: Boolean,
    existingTransactionCount: Int,
    accountProfileId: Long?,
    viewModel: TransactionDetailViewModel,
    splits: List<SplitItem>,
    showSplitEditor: Boolean
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Account + Category (connected group)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            if (transaction.transactionType == TransactionType.TRANSFER) {
                AccountNumberField(
                    accountNumber = transaction.fromAccount,
                    onAccountNumberChange = { viewModel.updateFromAccount(it) },
                    onAccountSelected = { bank, suffix -> viewModel.updateFromAccount(suffix, bank) },
                    bankName = transaction.fromBankName,
                    excludeBankName = transaction.toBankName,
                    viewModel = viewModel,
                    label = stringResource(R.string.txn_detail_field_from_account),
                    placeholder = stringResource(R.string.txn_detail_field_from_account_placeholder),
                    excludeAccount = transaction.toAccount
                )
                AccountNumberField(
                    accountNumber = transaction.toAccount,
                    onAccountNumberChange = { viewModel.updateToAccount(it) },
                    onAccountSelected = { bank, suffix -> viewModel.updateToAccount(suffix, bank) },
                    bankName = transaction.toBankName,
                    excludeBankName = transaction.fromBankName,
                    viewModel = viewModel,
                    label = stringResource(R.string.txn_detail_field_to_account),
                    placeholder = stringResource(R.string.txn_detail_field_to_account_placeholder),
                    excludeAccount = transaction.fromAccount
                )
            } else {
                AccountNumberField(
                    accountNumber = transaction.accountNumber,
                    onAccountNumberChange = { viewModel.updateAccountNumber(it) },
                    onAccountSelected = { bank, suffix -> viewModel.selectAccount(suffix, bank) },
                    viewModel = viewModel,
                    // Without the bank, two accounts sharing a number (HDFC ••1155
                    // and a wallet ••1155) were indistinguishable (#876).
                    bankName = transaction.bankName
                )
            }

            if (!showSplitEditor) {
                CategoryDropdown(
                    selectedCategory = transaction.category,
                    onCategorySelected = { viewModel.updateCategory(it) },
                    isIncomeTransaction = transaction.transactionType == TransactionType.INCOME,
                    viewModel = viewModel
                )
            }
        }

        // Split button
        if (!showSplitEditor && transaction.transactionType in TransactionDetailViewModel.SPLITTABLE_TYPES) {
            OutlinedButton(
                onClick = { viewModel.enableSplitMode() },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.CallSplit,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(stringResource(R.string.txn_detail_split_into_categories))
            }
        }

        // Checkboxes
        if (!showSplitEditor) {
            // The mapping keys on the merchant as saved before this edit, so name that one.
            val originalMerchant = viewModel.transaction.collectAsStateWithLifecycle().value?.merchantName
            // The whole row toggles, so tapping the label ticks the box (#911).
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = applyToAllFromMerchant,
                    role = Role.Checkbox,
                    onValueChange = { viewModel.toggleApplyToAllFromMerchant() }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = applyToAllFromMerchant,
                    onCheckedChange = null,
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
                Text(
                    text = stringResource(R.string.txn_detail_apply_category_to_merchant, originalMerchant ?: transaction.merchantName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (existingTransactionCount > 0) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = updateExistingTransactions,
                        role = Role.Checkbox,
                        onValueChange = { viewModel.toggleUpdateExistingTransactions() }
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = updateExistingTransactions,
                        onCheckedChange = null,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                    Text(
                        text = pluralStringResource(R.plurals.txn_detail_update_existing, existingTransactionCount, existingTransactionCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (transaction.transactionType == TransactionType.INCOME) {
                BudgetImpactSection(viewModel = viewModel)
            }
        }

        // Classification toggle
        val accountDefault = accountProfileId ?: ProfileEntity.PERSONAL_ID
        val effectiveProfileId = transaction.profileId ?: accountDefault
        val isEffectivelyBusiness = effectiveProfileId == ProfileEntity.BUSINESS_ID
        Text(
            text = stringResource(R.string.txn_detail_label_classification),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        ConnectedChoiceRow(
            options = listOf(ProfileEntity.PERSONAL_ID, ProfileEntity.BUSINESS_ID),
            selected = if (isEffectivelyBusiness) ProfileEntity.BUSINESS_ID else ProfileEntity.PERSONAL_ID,
            onSelect = { profileId ->
                // Picking the account's own default clears the override.
                viewModel.updateProfileId(if (accountDefault == profileId) null else profileId)
            },
            label = { profileId ->
                stringResource(
                    if (profileId == ProfileEntity.BUSINESS_ID) R.string.txn_detail_business else R.string.txn_detail_personal
                )
            }
        )

        // Recurring
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = transaction.isRecurring,
                onCheckedChange = { viewModel.updateRecurringStatus(it) }
            )
            Text(
                text = stringResource(R.string.txn_detail_recurring_checkbox),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Receipt attachment
        val existingReceiptUri by viewModel.receiptUri.collectAsStateWithLifecycle()
        val pendingReceiptUri by viewModel.pendingReceiptUri.collectAsStateWithLifecycle()
        val receiptRemoved by viewModel.receiptRemoved.collectAsStateWithLifecycle()
        val displayReceiptUri = pendingReceiptUri ?: if (receiptRemoved) null else existingReceiptUri
        ReceiptPickerSection(
            receiptUri = displayReceiptUri,
            onReceiptSelected = { uri -> viewModel.updatePendingReceiptUri(uri) },
            onReceiptRemoved = { viewModel.removeReceipt() },
            onCreateCameraUri = { viewModel.createCameraUri() }
        )

        // Bank (read-only)
        transaction.bankName?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Text(
                    text = stringResource(R.string.txn_detail_bank_read_only, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Show SplitEditor when in split mode
    if (showSplitEditor) {
        Spacer(modifier = Modifier.height(Spacing.md))
        SplitEditor(
            totalAmount = transaction.amount,
            currency = transaction.currency,
            splits = splits,
            availableCategories = categories.map { it.name },
            onSplitsChanged = { viewModel.updateSplits(it) },
            onRemoveSplits = { viewModel.removeSplits() },
            modifier = Modifier.padding(horizontal = 0.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetImpactSection(viewModel: TransactionDetailViewModel) {
    val budgetImpactType by viewModel.budgetImpactType.collectAsStateWithLifecycle()
    val budgetCategory by viewModel.budgetCategory.collectAsStateWithLifecycle()
    val activeBudgetCategories by viewModel.activeBudgetCategories.collectAsStateWithLifecycle()
    val refundCategories by viewModel.refundCategories.collectAsStateWithLifecycle()
    val pickable = if (budgetImpactType == BudgetImpactType.DEDUCT_SPENT) refundCategories else activeBudgetCategories

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = stringResource(R.string.txn_detail_budget_impact),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ConnectedChoiceRow(
            options = listOf(null, BudgetImpactType.DEDUCT_SPENT, BudgetImpactType.ADD_TO_LIMIT),
            selected = budgetImpactType,
            onSelect = { viewModel.updateBudgetImpactType(it) },
            label = {
                stringResource(
                    when (it) {
                        null -> R.string.txn_detail_budget_impact_none
                        BudgetImpactType.DEDUCT_SPENT -> R.string.txn_detail_budget_impact_refund
                        BudgetImpactType.ADD_TO_LIMIT -> R.string.txn_detail_budget_impact_extra
                    }
                )
            },
            textStyle = MaterialTheme.typography.labelSmall
        )

        if (budgetImpactType != null) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = budgetCategory ?: stringResource(R.string.txn_detail_budget_select_category),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.txn_detail_budget_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (pickable.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.txn_detail_budget_no_categories)) },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    } else {
                        pickable.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(categoryLabel(category)) },
                                onClick = {
                                    viewModel.updateBudgetCategory(category)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.StringRes
internal fun transactionTypeLabel(type: TransactionType): Int = when (type) {
    TransactionType.INCOME -> R.string.txn_type_income
    TransactionType.EXPENSE -> R.string.txn_type_expense
    TransactionType.CREDIT -> R.string.txn_type_credit
    TransactionType.TRANSFER -> R.string.txn_type_transfer
    TransactionType.INVESTMENT -> R.string.txn_type_investment
}
