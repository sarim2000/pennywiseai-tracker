package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.formatAmount
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Transactions the app couldn't place (#1049): money out left in Others, money in left
 * in Income. Usually a transfer to or from a person, which counts as spending or income
 * until the user says what it was. Loans, splits, paired transfers, refunds and rows
 * left out of analytics are already answered; so is a row split across categories
 * ([hasCategorySplits]), whose parent category stays as it was.
 */
internal fun needsAnAnswer(txn: TransactionEntity, hasCategorySplits: Boolean = false): Boolean =
    !txn.isDeleted && !txn.excludedFromAnalytics && !hasCategorySplits &&
        txn.loanId == null && txn.lentSharesTotal == null && txn.transferPairId == null &&
        when (txn.transactionType) {
            TransactionType.EXPENSE -> txn.category == BuiltinCategoryNames.current("Others")
            // A refund (budgetImpactType) offsets spending; it isn't income left to place.
            TransactionType.INCOME -> txn.budgetImpactType == null && txn.category == BuiltinCategoryNames.current("Income")
            else -> false
        }

/** [rows] still needing an answer on [today], newest first, without the ones kept or put off. */
internal fun needsAnswerQueue(
    rows: List<TransactionEntity>,
    splitTransactionIds: Set<Long>,
    snoozedUntil: Map<String, Long>,
    today: LocalDate,
): List<TransactionEntity> = rows
    .filter {
        needsAnAnswer(it, it.id in splitTransactionIds) &&
            (snoozedUntil[it.transactionHash] ?: Long.MIN_VALUE) < today.toEpochDay()
    }
    .sortedByDescending { it.dateTime }

private const val SHOWN = 3

@Composable
internal fun NeedsAnswerCard(
    queue: List<TransactionEntity>,
    onAnswer: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentPadding = Spacing.none,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, end = Spacing.md, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(Dimensions.Icon.medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = pluralStringResource(R.plurals.home_needs_answer_title, queue.size, queue.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = ink,
                )
                Text(
                    text = stringResource(R.string.home_needs_answer_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink,
                )
            }
        }
        queue.take(SHOWN).forEach { txn ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAnswer(txn.id) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = txn.merchantName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = txn.dateTime.format(dateFormat),
                        style = MaterialTheme.typography.bodySmall,
                        color = ink,
                    )
                }
                // Money in or out reads the same otherwise; the sign tells them apart.
                val sign = if (txn.transactionType == TransactionType.INCOME) "+" else "−"
                Text(text = sign + txn.formatAmount(), style = MaterialTheme.typography.titleSmall, color = ink)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ink)
            }
        }
        if (queue.size > SHOWN) {
            Text(
                text = pluralStringResource(R.plurals.home_needs_answer_more, queue.size - SHOWN, queue.size - SHOWN),
                style = MaterialTheme.typography.bodySmall,
                color = ink,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
            )
        } else {
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
