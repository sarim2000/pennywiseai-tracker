package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.split.canSplit
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/** The "this isn't plain spending" answers that apply to [txn] (#1050). */
enum class WhatWasThis { LOAN, SPLIT, SAVING, REFUND, OWN_TRANSFER }

fun whatWasThisOptions(txn: TransactionEntity, hasLoan: Boolean): List<WhatWasThis> = buildList {
    // Already on a loan or split (a split bill is on Loans, one share per person):
    // answered, and the editor won't let its type change anyway. A paired transfer
    // leg is answered too: it's one side of a move between the user's own accounts.
    if (hasLoan || txn.loanId != null || txn.lentSharesTotal != null || txn.transferPairId != null) return@buildList
    add(WhatWasThis.LOAN)
    if (canSplit(txn)) add(WhatWasThis.SPLIT)
    if (txn.transactionType == TransactionType.EXPENSE) add(WhatWasThis.SAVING)
    if (txn.transactionType == TransactionType.INCOME && txn.budgetImpactType != BudgetImpactType.DEDUCT_SPENT) {
        add(WhatWasThis.REFUND)
    }
    if (txn.transactionType == TransactionType.EXPENSE || txn.transactionType == TransactionType.INCOME) {
        add(WhatWasThis.OWN_TRANSFER)
    }
}

@Composable
internal fun WhatWasThisRow(onClick: () -> Unit) {
    GroupedRow(position = ListItemPosition.Single, onClick = onClick) {
        Icon(
            Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.medium),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RowLabels(
            title = stringResource(R.string.what_was_this_title),
            subtitle = stringResource(R.string.what_was_this_subtitle)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhatWasThisSheet(
    options: List<WhatWasThis>,
    onDismiss: () -> Unit,
    onPick: (WhatWasThis) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.md)
        ) {
            Text(
                stringResource(R.string.what_was_this_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm)
            )
            GroupedList {
                options.forEachIndexed { index, option ->
                    val (icon, title, hint) = option.labels()
                    GroupedRow(position = ListItemPosition.from(index, options.size), onClick = { onPick(option) }) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        RowLabels(title = stringResource(title), subtitle = stringResource(hint))
                    }
                }
            }
        }
    }
}

private fun WhatWasThis.labels(): Triple<ImageVector, Int, Int> = when (this) {
    WhatWasThis.LOAN -> Triple(Icons.Default.Handshake, R.string.what_was_this_loan, R.string.what_was_this_loan_hint)
    WhatWasThis.SPLIT -> Triple(Icons.AutoMirrored.Filled.CallSplit, R.string.what_was_this_split, R.string.what_was_this_split_hint)
    WhatWasThis.SAVING -> Triple(Icons.AutoMirrored.Filled.ShowChart, R.string.what_was_this_saving, R.string.what_was_this_saving_hint)
    WhatWasThis.REFUND -> Triple(Icons.AutoMirrored.Filled.Undo, R.string.what_was_this_refund, R.string.what_was_this_refund_hint)
    WhatWasThis.OWN_TRANSFER -> Triple(Icons.Default.SwapHoriz, R.string.what_was_this_transfer, R.string.what_was_this_transfer_hint)
}
