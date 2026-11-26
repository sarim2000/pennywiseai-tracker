package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.ReimbursementStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.formatAmount
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.income
import com.pennywiseai.tracker.ui.theme.spending
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter

/** Whether [txn] can be marked reimbursable: money out that isn't one already. */
fun canMarkReimbursable(txn: TransactionEntity): Boolean =
    txn.reimbursementStatus == null &&
        !txn.isDeleted &&
        (txn.transactionType == TransactionType.EXPENSE || txn.transactionType == TransactionType.CREDIT)

/**
 * "Reimbursement" on the transaction screen (#1389). On a reimbursable expense: waiting
 * (tap to say what paid it back) or reimbursed (tap to open that payment). On money
 * received: the expenses it paid back. Draws nothing otherwise.
 */
@Composable
fun ReimbursementSection(
    viewModel: TransactionReimbursementViewModel,
    transaction: TransactionEntity,
    onOpenTransaction: (Long) -> Unit,
    onOpenAll: () -> Unit,
    onPickPayment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val payment by viewModel.payment.collectAsStateWithLifecycle()
    val paidBack by viewModel.paidBack.collectAsStateWithLifecycle()
    val status = transaction.reimbursementStatus
    if (status == null && paidBack.isEmpty()) return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (status != null) R.string.reimburse_section_title else R.string.reimburse_paid_back_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = onOpenAll, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reimburse_see_all)) }
        }
        GroupedList {
            when (status) {
                ReimbursementStatus.PENDING -> GroupedRow(position = ListItemPosition.Single, onClick = onPickPayment) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RowLabels(
                        title = stringResource(R.string.reimburse_pending_title),
                        subtitle = stringResource(R.string.reimburse_pending_subtitle),
                    )
                    IconButton(onClick = viewModel::clear, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.reimburse_remove),
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ReimbursementStatus.REIMBURSED -> {
                    val linked = payment
                    GroupedRow(position = ListItemPosition.Single, onClick = linked?.let { { onOpenTransaction(it.id) } }) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.medium),
                            tint = MaterialTheme.colorScheme.success,
                        )
                        RowLabels(
                            title = stringResource(R.string.reimburse_done_title),
                            subtitle = if (linked != null) {
                                stringResource(
                                    R.string.reimburse_done_by,
                                    linked.merchantName,
                                    linked.formatAmount(),
                                    linked.dateTime.format(AppDates.dateFormatter()),
                                )
                            } else {
                                stringResource(R.string.reimburse_done_unlinked)
                            },
                        )
                        IconButton(onClick = viewModel::markPending, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.reimburse_undo),
                                modifier = Modifier.size(Dimensions.Icon.small),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                null -> paidBack.forEachIndexed { i, expense ->
                    GroupedRow(position = ListItemPosition.from(i, paidBack.size), onClick = { onOpenTransaction(expense.id) }) {
                        RowLabels(title = expense.merchantName, subtitle = expense.dateTime.format(AppDates.dateFormatter()))
                        Text(
                            CurrencyFormatter.signed("-", expense.formatAmount()),
                            style = PennyWiseText.amountRow,
                            color = MaterialTheme.colorScheme.spending,
                        )
                    }
                }
            }
        }
    }
}

/**
 * "What paid it back?": money received in the expense's currency since its day, newest
 * first, plus a choice for a payment PennyWise didn't record. A pick closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaidBackBySheet(
    viewModel: TransactionReimbursementViewModel,
    expense: TransactionEntity,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(expense.id) { viewModel.loadCandidates() }
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    fun pick(paymentId: Long?) {
        viewModel.markReimbursed(paymentId)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    stringResource(R.string.reimburse_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.reimburse_sheet_hint, expense.currency, expense.dateTime.format(AppDates.dateFormatter())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val rows = candidates
            if (rows == null) {
                Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@Column
            }
            val count = rows.size + 1
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap),
            ) {
                item(key = "unlinked") {
                    // No leading icon, so its text lines up with the payment rows under it.
                    GroupedRow(position = ListItemPosition.from(0, count), onClick = { pick(null) }) {
                        RowLabels(
                            title = stringResource(R.string.reimburse_sheet_unlinked),
                            subtitle = stringResource(R.string.reimburse_sheet_unlinked_hint),
                        )
                    }
                }
                itemsIndexed(rows, key = { _, it -> it.id }) { i, payment ->
                    GroupedRow(position = ListItemPosition.from(i + 1, count), onClick = { pick(payment.id) }) {
                        RowLabels(title = payment.merchantName, subtitle = payment.dateTime.format(AppDates.dateFormatter()))
                        Text(
                            CurrencyFormatter.signed("+", payment.formatAmount()),
                            style = PennyWiseText.amountRow,
                            color = MaterialTheme.colorScheme.income,
                        )
                    }
                }
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.reimburse_sheet_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}
