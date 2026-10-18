package com.pennywiseai.tracker.presentation.split

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.share.UpiQr
import com.pennywiseai.tracker.domain.loan.SplitError
import com.pennywiseai.tracker.domain.loan.SplitPerson
import com.pennywiseai.tracker.domain.loan.SplitRules
import com.pennywiseai.tracker.domain.loan.UpiRequest
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Can [txn] be split? Expenses only, and not one already marked as a loan. */
fun canSplit(txn: TransactionEntity): Boolean = txn.transactionType == TransactionType.EXPENSE && txn.loanId == null

/**
 * What to ask [person] for: their share, but never more than their loan still
 * has open, so a part already repaid isn't requested again.
 * ponytail: a loan that merges several bills doesn't say which bill a
 * repayment was for, so this caps at the loan's total; per-bill tracking
 * would need repayments linked to shares.
 */
internal fun owedFor(person: SplitPerson): BigDecimal =
    person.loan?.remainingAmount?.let { minOf(it, person.share.amount) }?.coerceAtLeast(BigDecimal.ZERO) ?: person.share.amount

/** UPI requests are INR only (#346). */
private fun TransactionEntity.isInr() = currency.equals("INR", ignoreCase = true)

/**
 * "Split with" on the transaction screen: each person's share (tap for their
 * loan) with a UPI request for INR bills, and your part.
 */
@Composable
fun SplitSection(
    viewModel: SplitBillViewModel,
    transaction: TransactionEntity,
    onOpenLoan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val people by viewModel.split.collectAsStateWithLifecycle()
    val upiId by viewModel.upiId.collectAsStateWithLifecycle()
    val payeeName by viewModel.payeeName.collectAsStateWithLifecycle()
    if (people.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askUpiFor by remember { mutableStateOf<SplitPerson?>(null) }
    val currency = transaction.currency

    fun request(person: SplitPerson, id: String) {
        val owed = owedFor(person)
        val amount = CurrencyFormatter.formatCurrency(owed, currency)
        val uri = UpiRequest.payUri(id, payeeName, owed, transaction.merchantName)
        val text = context.getString(R.string.split_request_text, amount, id, transaction.merchantName) + "\n" + uri
        val title = context.getString(R.string.split_request, amount)
        scope.launch {
            runCatching { context.startActivity(UpiQr.shareIntent(context, uri, text, title)) }
                .onFailure { Toast.makeText(context, R.string.split_request_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.split_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::openSheet) { Text(stringResource(R.string.split_menu_edit)) }
        }
        val rows = people.size + 1
        GroupedList {
            people.forEachIndexed { i, person ->
                val loan = person.loan
                val owed = owedFor(person)
                val settled = loan?.status == LoanStatus.SETTLED || owed.signum() == 0
                val amount = CurrencyFormatter.formatCurrency(person.share.amount, currency)
                GroupedRow(position = ListItemPosition.from(i, rows), onClick = loan?.let { { onOpenLoan(it.id) } }) {
                    RowLabels(
                        title = loan?.personName.orEmpty(),
                        subtitle = if (settled) stringResource(R.string.split_settled) else null,
                    )
                    if (transaction.isInr() && !settled) {
                        FilledTonalButton(onClick = { upiId?.let { request(person, it) } ?: run { askUpiFor = person } }) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                            Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.split_request, CurrencyFormatter.formatCurrency(owed, currency)))
                        }
                    } else {
                        Text(amount, style = PennyWiseText.amountRow, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            GroupedRow(position = ListItemPosition.from(people.size, rows)) {
                RowLabels(title = stringResource(R.string.split_you), subtitle = stringResource(R.string.split_your_part))
                Text(
                    CurrencyFormatter.formatCurrency(SplitRules.yourPart(transaction.amount, people.map { it.share.amount }), currency),
                    style = PennyWiseText.amountRow,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    askUpiFor?.let { person ->
        UpiIdDialog(
            initial = "",
            onDismiss = { askUpiFor = null },
            onSave = { id ->
                viewModel.saveUpiId(id).also { saved ->
                    if (saved) {
                        askUpiFor = null
                        request(person, id.trim())
                    }
                }
            },
        )
    }
}

/** Asks for your own UPI ID; [onSave] returns false to keep the dialog open on an invalid one. */
@Composable
fun UpiIdDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Boolean, allowClear: Boolean = false) {
    var value by rememberSaveable { mutableStateOf(initial) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_upi_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.split_upi_message), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.trim(); invalid = false },
                    placeholder = { Text(stringResource(R.string.split_upi_hint)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) { { Text(stringResource(R.string.split_upi_invalid)) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (allowClear && value.isBlank()) { onSave(""); return@TextButton }
                invalid = !onSave(value)
            }) { Text(stringResource(R.string.split_upi_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.txn_detail_action_cancel)) } },
    )
}

/** The split sheet, when open. Contacts permission is asked only when "Pick from contacts" is tapped. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitBillSheet(viewModel: SplitBillViewModel, transaction: TransactionEntity) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val saved by viewModel.split.collectAsStateWithLifecycle()
    val f = form ?: return
    val context = LocalContext.current
    val total = transaction.amount
    val currency = transaction.currency
    val preview = f.preview(total, currency)
    val digits = SplitRules.fractionDigits(currency)
    val amountInput = remember(digits) { Regex(if (digits == 0) "^\\d*$" else "^\\d*(\\.\\d{0,$digits})?$") }
    var name by rememberSaveable { mutableStateOf("") }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val full = f.people.size >= SplitRules.MAX_PEOPLE
    // Rows sit one tonal step above the sheet, or they vanish into it.
    val sheetRow = MaterialTheme.colorScheme.surfaceContainerHigh

    // The system picker grants a one-off read of the picked contact, so no
    // READ_CONTACTS request: only its display name is read.
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let { contactName(context, it) }?.let { viewModel.addPerson(it) }
    }

    ModalBottomSheet(onDismissRequest = viewModel::closeSheet, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.split_sheet_title, CurrencyFormatter.formatCurrency(total, currency)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.split_sheet_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SplitMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = f.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, SplitMode.entries.size),
                    ) {
                        Text(stringResource(if (mode == SplitMode.EQUAL) R.string.split_mode_equal else R.string.split_mode_custom))
                    }
                }
            }

            // Everyone's part: read-only rows when equal, an amount field each when custom.
            if (f.mode == SplitMode.EQUAL) {
                val rows = f.people.size + 1
                GroupedList {
                    f.people.forEachIndexed { i, person ->
                        GroupedRow(position = ListItemPosition.from(i, rows), containerColor = sheetRow) {
                            RowLabels(title = person.name)
                            Text(CurrencyFormatter.formatCurrency(preview.shares[i], currency), style = PennyWiseText.amountRow)
                            RemoveButton(person.name) { viewModel.removePerson(i) }
                        }
                    }
                    GroupedRow(position = ListItemPosition.from(f.people.size, rows), containerColor = sheetRow) {
                        RowLabels(title = stringResource(R.string.split_you), subtitle = stringResource(R.string.split_your_part))
                        Text(CurrencyFormatter.formatCurrency(preview.yours, currency), style = PennyWiseText.amountRow)
                    }
                }
            } else {
                val symbol = CurrencyFormatter.getCurrencySymbol(currency)
                f.people.forEachIndexed { i, person ->
                    OutlinedTextField(
                        value = person.amount,
                        onValueChange = { if (it.matches(amountInput)) viewModel.setShare(i, it) },
                        label = { Text(person.name) },
                        prefix = { Text(symbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        trailingIcon = { RemoveButton(person.name) { viewModel.removePerson(i) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = f.yourPart,
                    onValueChange = { if (it.matches(amountInput)) viewModel.setYourPart(it) },
                    label = { Text(stringResource(R.string.split_your_part)) },
                    prefix = { Text(symbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                val r = preview.remainder
                Text(
                    when {
                        r.signum() > 0 -> stringResource(R.string.split_left_to_assign, CurrencyFormatter.formatCurrency(r, currency))
                        r.signum() < 0 -> stringResource(R.string.split_over_total, CurrencyFormatter.formatCurrency(r.negate(), currency))
                        else -> stringResource(R.string.split_adds_up, CurrencyFormatter.formatCurrency(total, currency))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (r.signum() == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }

            // Adding people: type, a suggestion, or the contacts picker.
            Text(
                stringResource(R.string.split_people_count, f.people.size, SplitRules.MAX_PEOPLE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!full) {
                fun add() { if (viewModel.addPerson(name)) name = "" }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.split_person_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    trailingIcon = {
                        IconButton(onClick = ::add, enabled = name.isNotBlank()) {
                            Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.split_add_person))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                val unused = suggestions.filter { s -> f.people.none { it.name.equals(s, ignoreCase = true) } }
                if (unused.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        unused.take(MAX_SUGGESTIONS).forEach { s ->
                            SuggestionChip(onClick = { viewModel.addPerson(s) }, label = { Text(s) })
                        }
                    }
                }
                TextButton(onClick = { runCatching { pickContact.launch(null) } }) {
                    Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.split_pick_contact))
                }
            }

            val error = when (preview.error) {
                null -> null
                // Save is disabled and the people field is right there; no need to shout.
                SplitError.NO_PEOPLE -> null
                SplitError.SHARE_NOT_POSITIVE -> stringResource(R.string.split_error_share_positive)
                SplitError.OVER_AMOUNT -> stringResource(R.string.split_error_over)
                SplitError.YOUR_PART_NEGATIVE -> stringResource(R.string.split_error_your_part_negative)
                // The remainder line above already says by how much.
                SplitError.NOT_BALANCED -> null
                // The sheet never lets these through.
                SplitError.TOO_MANY_PEOPLE, SplitError.BLANK_NAME, SplitError.DUPLICATE_PERSON -> null
            }
            (f.failure?.let { stringResource(R.string.split_error_failed, it) } ?: error)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (saved.isNotEmpty()) {
                    TextButton(onClick = { confirmRemove = true }, enabled = !f.saving) {
                        Text(stringResource(R.string.split_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                Box(Modifier.weight(1f))
                OutlinedButton(onClick = viewModel::closeSheet) { Text(stringResource(R.string.txn_detail_action_cancel)) }
                Button(onClick = viewModel::save, enabled = !f.saving && preview.error == null) { Text(stringResource(R.string.split_save)) }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.split_remove_title)) },
            text = { Text(stringResource(R.string.split_remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    viewModel.removeSplit()
                }) { Text(stringResource(R.string.split_remove_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.txn_detail_action_cancel)) } },
        )
    }
}

@Composable
private fun RemoveButton(name: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.split_remove_person, name),
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MAX_SUGGESTIONS = 8

/** The picked contact's display name only — never its numbers. */
private fun contactName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0)?.trim()?.takeIf { it.isNotEmpty() } else null
    }
}.getOrNull()
