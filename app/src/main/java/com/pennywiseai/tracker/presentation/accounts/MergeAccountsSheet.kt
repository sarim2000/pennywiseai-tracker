package com.pennywiseai.tracker.presentation.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Self-contained two-step picker for account merge (#368).
 *
 *  Step 1 — pick the **source** (account whose transactions will be moved).
 *  Step 2 — pick the **target** (account they'll be moved into). The list
 *           filters to accounts compatible with the source: same currency,
 *           same `isCreditCard` flag, not the source itself.
 *  Step 3 — a confirmation dialog showing the actual transaction count.
 *
 * On confirm the sheet fires [onConfirm] and dismisses; the parent
 * ViewModel runs the merge + emits a success message. One-way operation
 * for v1 — no undo, hence the explicit confirmation step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeAccountsSheet(
    accounts: List<AccountBalanceEntity>,
    countTransactionsOn: suspend (bankName: String, accountLast4: String) -> Int,
    onConfirm: (source: AccountBalanceEntity, target: AccountBalanceEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var source by remember { mutableStateOf<AccountBalanceEntity?>(null) }
    var target by remember { mutableStateOf<AccountBalanceEntity?>(null) }
    var sourceTxnCount by remember { mutableStateOf<Int?>(null) }

    // Whenever the source changes, resolve its transaction count so the
    // confirmation step can show "Move N transactions into …".
    LaunchedEffect(source) {
        sourceTxnCount = source?.let { countTransactionsOn(it.bankName, it.accountLast4) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    bottom = Spacing.lg
                )
        ) {
            Text(
                text = "Merge accounts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Move all transactions from one account into another. The source account is removed when done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md)
            )

            // Source picker
            SectionLabel("Move from")
            AccountPicker(
                accounts = accounts,
                selected = source,
                onSelect = { picked ->
                    source = picked
                    // If the previously-chosen target is no longer compatible, clear it.
                    target?.let { t -> if (!compatible(picked, t)) target = null }
                }
            )

            // Target picker — only meaningful once a source is picked.
            source?.let { src ->
                val targets = accounts.filter { compatible(src, it) }
                if (targets.isEmpty()) {
                    Text(
                        text = "No other accounts match this one's currency / type.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.md)
                    )
                } else {
                    SectionLabel("Into", modifier = Modifier.padding(top = Spacing.md))
                    Row(
                        modifier = Modifier.padding(bottom = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.padding(end = Spacing.xs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${src.bankName} ••${src.accountLast4}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AccountPicker(
                        accounts = targets,
                        selected = target,
                        onSelect = { target = it }
                    )
                }
            }
        }
    }

    // Confirmation dialog appears once both ends are chosen. Tap "Merge" once
    // here to actually run the operation — sheet is one-way and not undoable.
    val s = source
    val t = target
    if (s != null && t != null) {
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("Merge accounts?") },
            text = {
                val n = sourceTxnCount
                Text(
                    if (n != null)
                        "Move $n transactions from ${s.bankName} ••${s.accountLast4} into ${t.bankName} ••${t.accountLast4}. The source account is removed after the move. This can't be undone."
                    else
                        "Move all transactions from ${s.bankName} ••${s.accountLast4} into ${t.bankName} ••${t.accountLast4}. The source account is removed after the move. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(s, t) }) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { target = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(bottom = Spacing.xs)
    )
}

@Composable
private fun AccountPicker(
    accounts: List<AccountBalanceEntity>,
    selected: AccountBalanceEntity?,
    onSelect: (AccountBalanceEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        items(
            accounts,
            key = { "${it.bankName}|${it.accountLast4}|${it.id}" }
        ) { acct ->
            val isSelected = selected?.bankName == acct.bankName &&
                selected.accountLast4 == acct.accountLast4
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(acct) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = acct.bankName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            text = buildString {
                                append("••")
                                append(acct.accountLast4)
                                append(" · ")
                                append(acct.currency)
                                if (acct.isCreditCard) append(" · Credit")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Selected") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                labelColor = MaterialTheme.colorScheme.onPrimary,
                                leadingIconContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

/** Two accounts can be merged when they share currency + credit-card flag. */
private fun compatible(a: AccountBalanceEntity, b: AccountBalanceEntity): Boolean {
    val sameAccount = a.bankName.equals(b.bankName, ignoreCase = true) &&
        a.accountLast4 == b.accountLast4
    if (sameAccount) return false
    if (!a.currency.equals(b.currency, ignoreCase = true)) return false
    if (a.isCreditCard != b.isCreditCard) return false
    return true
}
