package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SHORT_DATE
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A credit card's "Bill" row: what's left to pay and when, or what to add so Safe
 * to spend can set it aside. A tap edits the due day and statement amount.
 */
@Composable
fun CardBillCard(status: CardBillStatus, onSave: (dueDay: Int?, amount: BigDecimal?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val card = status.card
    val bill = status.bill
    val today = LocalDate.now()

    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Spacing.md,
        onClick = { editing = true }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(R.string.card_bill_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val overdue = bill?.isOverdue(today) == true
                Text(
                    text = when {
                        bill == null && card.cardBillAmount != null && card.cardDueDay != null && !status.needsBill ->
                            stringResource(R.string.card_bill_paid)
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
                if (status.needsBill && card.cardBillAmount != null && card.cardDueDay != null) {
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
            amount = card.cardBillAmount,
            currency = card.currency,
            onDismiss = { editing = false },
            onSave = { day, amount ->
                onSave(day, amount)
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
    currency: String,
    onDismiss: () -> Unit,
    onSave: (Int?, BigDecimal?) -> Unit
) {
    // No day until the user picks one: a made-up due day could mark the bill overdue.
    var day by remember { mutableStateOf(dueDay) }
    var text by remember { mutableStateOf(amount?.toPlainString().orEmpty()) }
    val parsed = text.trim().toBigDecimalOrNull()
    val valid = text.isBlank() || (parsed != null && parsed.signum() >= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_bill_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.card_bill_due_day_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { day = ((day ?: 2) - 1).coerceAtLeast(1) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.manage_accounts_decrease))
                    }
                    Text(
                        text = day?.toString() ?: "–",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = Spacing.lg)
                    )
                    IconButton(onClick = { day = ((day ?: 0) + 1).coerceAtMost(31) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.manage_accounts_increase))
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.card_bill_amount_label, currency)) },
                    supportingText = { Text(stringResource(R.string.card_bill_amount_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(day, parsed) },
                enabled = valid,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.accounts_action_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (dueDay != null || amount != null) {
                    TextButton(onClick = { onSave(null, null) }, shapes = ButtonDefaults.shapes()) {
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
