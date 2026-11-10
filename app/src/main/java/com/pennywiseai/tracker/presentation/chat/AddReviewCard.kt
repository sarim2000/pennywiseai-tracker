package com.pennywiseai.tracker.presentation.chat

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.ChatMessageFacts
import com.pennywiseai.tracker.data.model.ReviewRow
import com.pennywiseai.tracker.data.model.TransactionDraft
import com.pennywiseai.tracker.ui.components.CategoryMenuItems
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.SettledCheck
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.forPicker
import com.pennywiseai.tracker.ui.components.pickerDefault
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * What the model proposed from one message. Nothing is written until the user taps Add (#170).
 * Once the rows are [saved], the actions give way to the receipt's check until the card goes.
 */
@Composable
internal fun AddReviewCard(
    rows: List<ReviewRow>,
    categories: List<CategoryEntity>,
    enabled: Boolean,
    saved: Boolean,
    onToggle: (Int) -> Unit,
    onEdit: (Int, TransactionDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = rows.count { it.selected }
    val fg = MaterialTheme.colorScheme.onTertiaryContainer
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            val only = rows.singleOrNull()?.draft
            Text(
                text = stringResource(
                    when {
                        only == null -> R.string.chat_action_add_many_title
                        only.type == TransactionType.INCOME -> R.string.chat_action_add_income
                        else -> R.string.chat_action_add_expense
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = fg
            )
            Text(text = stringResource(R.string.chat_action_suggestion), style = MaterialTheme.typography.bodySmall, color = fg)
            rows.forEach { row ->
                ReviewRowItem(row, enabled, onToggle = { onToggle(row.id) }, onEdit = { editing = row.id })
            }
            // No amounts in here: the rows above keep their (masked) figures out of the crossfade.
            Crossfade(saved, animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "addCardActions") { done ->
                if (done) {
                    // The check is the avatar's height, about a button's, so the card doesn't jump.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SettledCheck(settled = true, size = Dimensions.Icon.avatar) {}
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = fg),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(stringResource(R.string.chat_cancel))
                        }
                        Button(onClick = onConfirm, enabled = enabled && selected > 0, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                            Text(
                                if (rows.size == 1) stringResource(R.string.chat_action_add)
                                else pluralStringResource(R.plurals.chat_action_add_count, selected, selected)
                            )
                        }
                    }
                }
            }
        }
    }
    rows.firstOrNull { it.id == editing }?.let { row ->
        ReviewRowEditSheet(
            draft = row.draft,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { draft -> onEdit(row.id, draft); editing = null }
        )
    }
}

@Composable
private fun ReviewRowItem(row: ReviewRow, enabled: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    val draft = row.draft
    val fg = MaterialTheme.colorScheme.onTertiaryContainer
    val amount = CurrencyFormatter.formatCurrency(draft.amount, draft.currency)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The whole row is the checkbox, so a screen reader says what ticking it means.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget)
                .toggleable(value = row.selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
        ) {
            Checkbox(
                checked = row.selected,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.padding(horizontal = Spacing.smd),
                colors = CheckboxDefaults.colors(checkedColor = fg, uncheckedColor = fg, checkmarkColor = MaterialTheme.colorScheme.tertiaryContainer)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = "$amount · ${draft.merchant}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = fg)
                Text(
                    text = listOf(
                        stringResource(if (draft.type == TransactionType.INCOME) R.string.rules_type_income else R.string.rules_type_expense),
                        if (draft.date == LocalDate.now()) {
                            stringResource(R.string.chat_action_add_detail, categoryLabel(draft.category), draft.accountLabel)
                        } else {
                            stringResource(R.string.chat_action_add_detail_dated, categoryLabel(draft.category), draft.accountLabel, draft.date.format(DATE))
                        }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = fg
                )
                row.check?.let { check ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small), tint = fg)
                        Text(
                            text = stringResource(
                                when (check) {
                                    ReviewRow.Check.AMOUNT_NOT_IN_MESSAGE -> R.string.chat_action_check_amount
                                    ReviewRow.Check.REPEATED -> R.string.chat_action_check_repeat
                                    ReviewRow.Check.NOT_A_PAYMENT -> R.string.chat_action_check_not_paid
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = fg
                        )
                    }
                }
            }
        }
        IconButton(onClick = onEdit, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.chat_action_edit_row, draft.merchant), tint = fg)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewRowEditSheet(
    draft: TransactionDraft,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (TransactionDraft) -> Unit
) {
    var amount by rememberSaveable { mutableStateOf(draft.amount.stripTrailingZeros().toPlainString()) }
    var merchant by rememberSaveable { mutableStateOf(draft.merchant) }
    var type by rememberSaveable { mutableStateOf(draft.type) }
    var category by rememberSaveable { mutableStateOf(draft.category) }
    var currency by rememberSaveable { mutableStateOf(draft.currency) }
    var date by rememberSaveable { mutableStateOf(draft.date) }
    var keepAccount by rememberSaveable { mutableStateOf(draft.bankName != null) }
    var showCurrencies by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    val noAccount = stringResource(R.string.chat_account_none)
    val parsed = ChatMessageFacts.parseAmount(amount)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(stringResource(R.string.chat_action_edit_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.add_field_amount)) },
                    isError = parsed == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                ExposedDropdownMenuBox(expanded = showCurrencies, onExpandedChange = { showCurrencies = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = "${CurrencyFormatter.getCurrencySymbol(currency)} $currency",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.chat_action_edit_currency)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencies) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = showCurrencies, onDismissRequest = { showCurrencies = false }) {
                        val codes = (listOf(currency) + CurrencyFormatter.getSupportedCurrencies()).distinct()
                        codes.forEachIndexed { index, code ->
                            SelectableDropdownMenuItem(
                                selected = code == currency,
                                text = { Text("${CurrencyFormatter.getCurrencySymbol(code)} $code") },
                                onClick = { currency = code; showCurrencies = false },
                                shapes = MenuDefaults.itemShape(index, codes.size),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text(stringResource(R.string.add_field_merchant)) },
                isError = merchant.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ConnectedChoiceRow(
                options = listOf(TransactionType.EXPENSE, TransactionType.INCOME),
                selected = type,
                onSelect = { new ->
                    type = new
                    val isIncome = new == TransactionType.INCOME
                    if (categories.forPicker(isIncome).none { it.name == category }) category = categories.pickerDefault("Others", isIncome)
                },
                label = { stringResource(if (it == TransactionType.INCOME) R.string.rules_type_income else R.string.rules_type_expense) }
            )
            ExposedDropdownMenuBox(expanded = showCategories, onExpandedChange = { showCategories = it }) {
                OutlinedTextField(
                    value = categoryLabel(category),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.add_field_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategories) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = showCategories, onDismissRequest = { showCategories = false }) {
                    CategoryMenuItems(
                        categories = categories,
                        isIncome = type == TransactionType.INCOME,
                        onSelected = { category = it.name; showCategories = false }
                    )
                }
            }
            TextButton(onClick = { showDate = true }, shapes = ButtonDefaults.shapes()) { Text(date.format(DATE)) }
            if (keepAccount) {
                TextButton(onClick = { keepAccount = false }, shapes = ButtonDefaults.shapes()) {
                    Text("${stringResource(R.string.chat_action_edit_remove_account)}: ${draft.accountLabel}")
                }
            }
            Button(
                onClick = {
                    // A balance moves by the raw amount, so an account only keeps a row in its own currency.
                    val linked = keepAccount && currency == draft.currency
                    onSave(
                        draft.copy(
                            amount = parsed ?: return@Button,
                            merchant = merchant.trim(),
                            type = type,
                            category = category,
                            currency = currency,
                            date = date,
                            bankName = draft.bankName.takeIf { linked },
                            accountLast4 = draft.accountLast4.takeIf { linked },
                            accountLabel = if (linked || draft.bankName == null) draft.accountLabel else noAccount
                        )
                    )
                },
                enabled = parsed != null && merchant.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.chat_action_edit_done)) }
        }
    }
    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        showDate = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.add_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.add_cancel)) }
            }
        ) { DatePicker(state = state) }
    }
}

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy")
