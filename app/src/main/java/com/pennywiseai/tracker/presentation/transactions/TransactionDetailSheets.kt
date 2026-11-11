package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.ui.components.CategoryMenuItems
import com.pennywiseai.tracker.ui.components.SelectedCategoryIcon
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Dimensions
import java.math.BigDecimal
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.money

// ==================== Mark as Loan Bottom Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarkAsLoanBottomSheet(
    transactionAmount: BigDecimal,
    transactionCurrency: String,
    direction: LoanDirection,
    recentPersonNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (personName: String, note: String?, loanAmount: BigDecimal?) -> Unit
) {
    var personName by remember { mutableStateOf("") }
    var isAddingNew by remember { mutableStateOf(recentPersonNames.isEmpty()) }
    var note by remember { mutableStateOf("") }
    // Pre-filled with the full transaction amount — user only edits this when
    // a portion of the payment isn't part of the loan (e.g. issue #309: paid
    // ₹5500 but only ₹3500 is meant to come back).
    var loanAmountInput by remember(transactionAmount) {
        mutableStateOf(transactionAmount.toPlainString())
    }
    val parsedLoanAmount = loanAmountInput.toBigDecimalOrNull()
    val isLoanAmountValid = parsedLoanAmount != null &&
        parsedLoanAmount > BigDecimal.ZERO &&
        parsedLoanAmount <= transactionAmount

    val loanColor = MaterialTheme.colorScheme.loan
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scroll so the loan-amount/note fields and the Confirm button stay
                // reachable when several existing people are listed — otherwise they're
                // pushed below the sheet and the user can only proceed via "New person".
                // (#489)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(
                    if (direction == LoanDirection.LENT) R.string.txn_detail_loan_sheet_lent else R.string.txn_detail_loan_sheet_borrowed,
                    CurrencyFormatter.formatCurrency(transactionAmount, transactionCurrency)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (direction == LoanDirection.LENT) stringResource(R.string.txn_detail_loan_sheet_who_lent) else stringResource(R.string.txn_detail_loan_sheet_who_borrowed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isAddingNew && recentPersonNames.isNotEmpty()) {
                // Pick from existing people
                recentPersonNames.forEach { name ->
                    Surface(
                        onClick = { personName = name },
                        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                        color = if (personName == name) loanColor.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (personName == name) BorderStroke(1.dp, loanColor)
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(loanColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = loanColor
                                )
                            }
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (personName == name) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = loanColor,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                            }
                        }
                    }
                }

                // Add new person option
                TextButton(onClick = { isAddingNew = true; personName = "" }, shapes = ButtonDefaults.shapes()) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.txn_detail_loan_new_person))
                }
            } else {
                // Text field for new person name
                TextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text(stringResource(R.string.txn_detail_loan_person_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = editFullShape,
                    colors = editFilledColors()
                )
                if (recentPersonNames.isNotEmpty()) {
                    TextButton(onClick = { isAddingNew = false; personName = "" }, shapes = ButtonDefaults.shapes()) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.txn_detail_loan_pick_existing))
                    }
                }
            }

            // Loan amount (defaults to full txn amount; user lowers when only a
            // portion of the payment is meant to come back).
            TextField(
                value = loanAmountInput,
                onValueChange = { loanAmountInput = it },
                label = { Text(stringResource(R.string.txn_detail_loan_amount)) },
                supportingText = {
                    Text(
                        if (parsedLoanAmount != null && parsedLoanAmount < transactionAmount) {
                            stringResource(R.string.txn_detail_loan_amount_partial_hint)
                        } else {
                            stringResource(R.string.txn_detail_loan_amount_max, CurrencyFormatter.formatCurrency(transactionAmount, transactionCurrency))
                        }
                    )
                },
                isError = !isLoanAmountValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = editFullShape,
                colors = editFilledColors()
            )

            // Optional note
            TextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.txn_detail_loan_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = editFullShape,
                colors = editFilledColors()
            )

            // Confirm button
            Button(
                onClick = {
                    onConfirm(
                        personName.trim(),
                        note.trim().ifEmpty { null },
                        parsedLoanAmount
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = personName.isNotBlank() && isLoanAmountValid,
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = loanColor
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.small)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(stringResource(R.string.txn_detail_action_confirm))
            }
        }
    }
}

/**
 * Names the untracked amount behind a balance mismatch before it's added
 * (#734): the amount is fixed, only "what was it?" and the category are asked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BalanceAdjustmentSheet(
    discrepancy: com.pennywiseai.tracker.data.ledger.BalanceDiscrepancy,
    viewModel: TransactionDetailViewModel,
    onDismiss: () -> Unit
) {
    val type = balanceAdjustmentType(discrepancy)
    val isIncome = type == TransactionType.INCOME
    val categories by viewModel.adjustmentCategories.collectAsStateWithLifecycle()
    var merchant by rememberSaveable { mutableStateOf("") }
    // Null until the user picks: the view model then tries the merchant's mapping.
    var pickedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val shownCategory = pickedCategory ?: categories.balanceAdjustmentDefaultCategory(type)
    var expanded by remember { mutableStateOf(false) }
    val amountColor = when {
        isIncome -> MaterialTheme.colorScheme.income
        else -> MaterialTheme.colorScheme.onSurface
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column {
                Text(
                    text = stringResource(
                        if (isIncome) R.string.txn_detail_adjustment_title_income
                        else R.string.txn_detail_adjustment_title_expense
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = (if (isIncome) "+" else "-") +
                        CurrencyFormatter.formatCurrency(discrepancy.delta.abs(), discrepancy.currency),
                    style = MaterialTheme.typography.headlineMedium.money(),
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
            }
            TextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text(stringResource(R.string.txn_detail_adjustment_what)) },
                placeholder = { Text(stringResource(R.string.txn_detail_adjustment_what_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = editFullShape,
                colors = editFilledColors()
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                TextField(
                    value = categoryLabel(shownCategory),
                    onValueChange = { },
                    label = { Text(stringResource(R.string.txn_detail_label_category)) },
                    leadingIcon = { SelectedCategoryIcon(categories.find { it.name == shownCategory }) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    shape = editFullShape,
                    colors = editFilledColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    CategoryMenuItems(
                        categories = categories,
                        isIncome = isIncome,
                        onSelected = {
                            pickedCategory = it.name
                            expanded = false
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
            ) {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.txn_detail_action_cancel))
                }
                Button(onClick = {
                    viewModel.addBalanceAdjustment(merchant, pickedCategory)
                    onDismiss()
                }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.txn_detail_adjustment_add))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupBottomSheet(
    currentGroup: TransactionGroupEntity?,
    availableGroups: List<TransactionGroupEntity>,
    onDismiss: () -> Unit,
    onAddToGroup: (Long) -> Unit,
    onRemoveFromGroup: () -> Unit,
    onCreateGroup: (String, String?) -> Unit
) {
    var showCreateField by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Dimensions.Padding.content)
        ) {
            Text(
                text = stringResource(R.string.txn_detail_group_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            if (currentGroup != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.txn_detail_group_current, currentGroup.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        onRemoveFromGroup()
                        onDismiss()
                    }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.txn_detail_group_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(bottom = Spacing.md))
            }

            val otherGroups = availableGroups.filter { it.id != currentGroup?.id }
            if (otherGroups.isNotEmpty()) {
                Text(
                    text = if (currentGroup != null) stringResource(R.string.txn_detail_group_move_to) else stringResource(R.string.txn_detail_menu_add_to_group),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
                otherGroups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddToGroup(group.id) }
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            if (showCreateField) {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text(stringResource(R.string.txn_detail_group_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (newGroupName.isNotBlank()) {
                                    onCreateGroup(newGroupName.trim(), null)
                                }
                            },
                            enabled = newGroupName.isNotBlank(),
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.txn_detail_group_create))
                        }
                    }
                )
            } else {
                TextButton(
                    onClick = { showCreateField = true },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.txn_detail_group_create_new))
                }
            }
        }
    }
}
