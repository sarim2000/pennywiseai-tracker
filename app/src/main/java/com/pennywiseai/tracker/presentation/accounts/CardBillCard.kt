package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.spending.CardBillStatus
import com.pennywiseai.tracker.ui.components.AmountBarDialogHost
import com.pennywiseai.tracker.ui.components.RoundedField
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SHORT_DATE
import com.pennywiseai.tracker.ui.components.orSupporting
import com.pennywiseai.tracker.ui.components.rememberAmountEntry
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A credit card's "Bill" row: what's left to pay and when, or what to add so Safe
 * to spend can set it aside. A tap edits the due day and statement amount.
 */
@Composable
fun CardBillCard(
    status: CardBillStatus,
    onSave: (dueDay: Int?, amount: BigDecimal?) -> Unit,
    onClear: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val card = status.card
    val bill = status.bill
    val today = LocalDate.now()

    val overdue = bill?.isOverdue(today) == true
    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = Spacing.md,
        onClick = { editing = true }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // An overdue bill says so in colour before the words are read.
            IconTile(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                containerColor = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(R.string.card_bill_title),
                    style = PennyWiseText.rowTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val entered = card.cardBillAmount != null && card.cardDueDay != null
                Text(
                    text = when {
                        bill == null && entered && status.needsBill -> stringResource(R.string.card_bill_new_statement)
                        bill == null && entered -> stringResource(R.string.card_bill_paid)
                        bill == null -> stringResource(R.string.card_bill_add)
                        overdue -> stringResource(
                            R.string.card_bill_overdue,
                            CurrencyFormatter.formatCurrency(bill.remaining, bill.currency),
                            bill.dueDate.format(SHORT_DATE)
                        )
                        else -> stringResource(
                            R.string.card_bill_due,
                            CurrencyFormatter.formatCurrency(bill.remaining, bill.currency),
                            bill.dueDate.format(SHORT_DATE)
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (bill != null && bill.paid.signum() > 0) {
                    Text(
                        text = stringResource(
                            R.string.card_bill_paid_part,
                            CurrencyFormatter.formatCurrency(bill.paid, bill.currency),
                            CurrencyFormatter.formatCurrency(bill.amount, bill.currency)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (bill != null && status.needsBill) {
                    Text(
                        text = stringResource(R.string.card_bill_new_statement),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.card_bill_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
    }

    if (editing) {
        CardBillDialog(
            dueDay = card.cardDueDay,
            // A paid or older bill's amount isn't offered: saved unchanged, it would
            // quietly become the new statement's. Left blank, that bill stays as it was.
            amount = card.cardBillAmount.takeUnless { status.awaitsNewStatement },
            forNewStatement = status.awaitsNewStatement,
            currency = card.currency,
            onDismiss = { editing = false },
            onSave = { day, amount ->
                onSave(day, amount)
                editing = false
            },
            onClear = {
                onClear()
                editing = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardBillDialog(
    dueDay: Int?,
    amount: BigDecimal?,
    forNewStatement: Boolean,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Int?, BigDecimal?) -> Unit,
    onClear: () -> Unit
) {
    // No day until the user types one: a made-up due day could mark the bill overdue.
    var dayText by remember { mutableStateOf(dueDay?.toString().orEmpty()) }
    val day = dayText.trim().toIntOrNull()?.takeIf { it in 1..31 }
    val dayValid = dayText.isBlank() || day != null
    var text by remember { mutableStateOf(amount?.toPlainString().orEmpty()) }
    val parsed = text.trim().toBigDecimalOrNull()
    val amountEntry = rememberAmountEntry(text, { text = it }, currency)
    val amountSum = amountEntry.sumHint
    // A sum that can't be an amount ("5÷0") isn't a blank one: Save would clear the bill.
    val valid = (text.isBlank() || (parsed != null && parsed.signum() >= 0)) && amountSum?.isError != true
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        // Clearing drops the bill from Safe to spend, so ask first. Cancel returns
        // to the edit dialog with what was typed still there.
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.card_bill_clear_title)) },
            text = { Text(stringResource(R.string.card_bill_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = onClear,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.manage_accounts_action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.accounts_action_cancel))
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_bill_title)) },
        text = {
            AmountBarDialogHost {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) dayText = it },
                        label = { Text(stringResource(R.string.card_bill_due_day_label)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    when {
                                        !dayValid -> R.string.card_bill_due_day_error
                                        // Empty is a real state: no due day, so nothing is set aside yet.
                                        dayText.isBlank() -> R.string.card_bill_due_day_not_set
                                        else -> R.string.card_bill_due_day_description
                                    }
                                )
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = !dayValid,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedField.shape
                    )
                    OutlinedTextField(
                        value = amountEntry.value,
                        onValueChange = amountEntry::onValueChange,
                        label = { Text(stringResource(R.string.card_bill_amount_label, currency)) },
                        supportingText = amountSum.orSupporting {
                            Text(stringResource(if (forNewStatement) R.string.card_bill_amount_new_hint else R.string.card_bill_amount_hint))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = amountSum?.isError ?: !valid,
                        modifier = Modifier.fillMaxWidth().then(amountEntry.modifier),
                        shape = RoundedField.shape
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(day, parsed) },
                enabled = valid && dayValid,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.accounts_action_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (dueDay != null || amount != null) {
                    TextButton(onClick = { confirmClear = true }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.manage_accounts_action_clear))
                    }
                }
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.accounts_action_cancel))
                }
            }
        }
    )
}
