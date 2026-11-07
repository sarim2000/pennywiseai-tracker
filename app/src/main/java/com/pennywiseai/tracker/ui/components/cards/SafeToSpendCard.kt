package com.pennywiseai.tracker.ui.components.cards

import com.pennywiseai.tracker.ui.theme.pennyWiseButtonShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.spending.SafeToSpend
import com.pennywiseai.tracker.presentation.home.SafeToSpendUi
import com.pennywiseai.tracker.presentation.home.isUncountedNegative
import com.pennywiseai.tracker.presentation.home.safeToSpendKey
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.expense
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.formatBalance

private const val MASK = "••••••"

private fun SafeToSpend.formatAmount() = CurrencyFormatter.formatCurrency(amount, currency)

private fun List<SafeToSpend>.formatAmounts() =
    CurrencyFormatter.formatByCurrency(associate { it.currency to Money(it.amount, it.currency) })

/**
 * Home "Safe to spend" card. Follows the global hide-amounts flag like
 * [CashFlowCard]: while hidden, a tap reveals; once shown, a tap opens the
 * breakdown sheet.
 */
@Composable
fun SafeToSpendCard(
    state: SafeToSpendUi,
    isBalanceHidden: Boolean,
    onReveal: () -> Unit,
    onOpenBreakdown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = state.primary
    val until = state.periodEnd.format(SHORT_DATE)
    val dueBefore = state.periodEnd.plusDays(1).format(SHORT_DATE)
    // The second line of the statement, under Accounts. A rate and a caveat each, in the
    // small print: the screen's one big number is the one above.
    val detail = if (isBalanceHidden) stringResource(R.string.safe_to_spend_tap_to_show) else buildList {
        add(perDayText(primary, until, dueBefore))
        if (primary.bills.signum() > 0) {
            add(stringResource(R.string.safe_to_spend_after_bills, CurrencyFormatter.formatCurrency(primary.bills, primary.currency)))
        }
        if (primary.isCapped) add(stringResource(R.string.safe_to_spend_capped, primary.formatAmount()))
    }.filterNotNull().joinToString(" · ")
    StatementLine(
        label = stringResource(R.string.safe_to_spend_title),
        value = if (isBalanceHidden) MASK else primary.formatAmount(),
        valueColor = if (!isBalanceHidden && primary.amount.signum() < 0) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface,
        // The daily answer, so it outranks the balance above it.
        valueStyle = PennyWiseText.amountLarge,
        detail = detail,
        onClick = if (isBalanceHidden) onReveal else onOpenBreakdown,
        modifier = modifier,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
    )
}

/** "Capped by your budget: ₹5,000 left", only when the budget sets the figure. */
@Composable
private fun CappedLine(s: SafeToSpend) {
    if (!s.isCapped) return
    Text(
        text = stringResource(R.string.safe_to_spend_capped, s.formatAmount()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Null when the budget is used up: the capped line says so, "nothing left after bills" would be wrong. */
@Composable
private fun perDayText(s: SafeToSpend, until: String, dueBefore: String): String? {
    val perDay = s.perDay
    if (perDay == null && s.isCapped) return null
    return when {
        // A date, not a day count: Home's "7 days left" excludes today, this includes it.
        perDay != null -> stringResource(
            R.string.safe_to_spend_per_day,
            CurrencyFormatter.formatEstimate(perDay, s.currency), until
        )
        s.amount.signum() < 0 -> stringResource(R.string.safe_to_spend_short, dueBefore)
        else -> stringResource(R.string.safe_to_spend_none_left, dueBefore)
    }
}

@Composable
private fun PerDayLine(s: SafeToSpend, until: String, dueBefore: String) {
    val short = s.amount.signum() < 0
    Text(
        text = perDayText(s, until, dueBefore) ?: return,
        style = MaterialTheme.typography.bodyMedium,
        color = if (short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    )
}

/** Breakdown behind [SafeToSpendCard]: accounts counted, bills due, and the total. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeToSpendSheet(
    state: SafeToSpendUi,
    onSetIncluded: (AccountBalanceEntity, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val primary = state.primary
    val until = state.periodEnd.format(SHORT_DATE)
    val dueBefore = state.periodEnd.plusDays(1).format(SHORT_DATE)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surface,
        // M3 wraps the handle slot in a clickable whose hover/focus state layer draws a
        // grey square behind the pill. The same pill as plain content has none.
        dragHandle = null
    ) {
        BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = stringResource(R.string.safe_to_spend_until, until),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = primary.formatAmount(),
                style = PennyWiseText.amountLarge,
                maxLines = 1,
                autoSize = PennyWiseText.fit(PennyWiseText.amountLarge),
                color = if (primary.amount.signum() < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            PerDayLine(primary, until, dueBefore)
            CappedLine(primary)

            SectionHeaderV2(
                title = stringResource(R.string.safe_to_spend_accounts),
                action = {
                    TextButton(onClick = { choosing = !choosing }, shapes = pennyWiseButtonShapes()) {
                        Text(stringResource(if (choosing) R.string.safe_to_spend_done else R.string.safe_to_spend_choose_accounts))
                    }
                }
            )
            GroupedList {
                state.accounts.forEachIndexed { i, account ->
                    val negative = isUncountedNegative(account)
                    val included = !negative && safeToSpendKey(account) !in state.excludedKeys
                    GroupedRow(
                        position = ListItemPosition.from(i, state.accounts.size),
                        onClick = if (choosing && !negative) ({ onSetIncluded(account, !included) }) else null
                    ) {
                        SheetRowLabels(
                            title = account.displayLabel,
                            subtitle = when {
                                negative -> stringResource(R.string.safe_to_spend_excluded_negative)
                                // Its own currency: can't be added into the total below.
                                account.currency != primary.currency -> stringResource(R.string.safe_to_spend_other_currency_row)
                                included -> null
                                else -> stringResource(R.string.safe_to_spend_excluded)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (choosing) {
                            Checkbox(
                                checked = included,
                                onCheckedChange = { onSetIncluded(account, it) },
                                enabled = !negative
                            )
                        } else {
                            Text(
                                text = CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                style = PennyWiseText.amountRow,
                                color = if (included) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SectionHeaderV2(title = stringResource(R.string.safe_to_spend_bills_due, dueBefore))
            if (state.bills.isEmpty()) {
                Text(
                    text = stringResource(R.string.safe_to_spend_no_bills),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                GroupedList {
                    state.bills.sortedBy { it.dueDate }.forEachIndexed { i, bill ->
                        GroupedRow(position = ListItemPosition.from(i, state.bills.size)) {
                            SheetRowLabels(
                                title = bill.name,
                                subtitle = bill.dueDate.format(SHORT_DATE),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = CurrencyFormatter.signed("\u2212", CurrencyFormatter.formatCurrency(bill.amount.amount, bill.amount.currency)),
                                style = PennyWiseText.amountRow,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.ledgerRule().padding(top = Spacing.smd)) {
                Text(
                    text = stringResource(R.string.safe_to_spend_title),
                    style = PennyWiseText.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = primary.formatAmount(),
                    style = PennyWiseText.amountMedium,
                    maxLines = 1,
                    autoSize = PennyWiseText.fit(PennyWiseText.amountMedium),
                    color = if (primary.amount.signum() < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            // Other currencies under the total, never beside it: "JD1,573.930 · $800" read as
            // one safe-to-spend figure that included the dollars.
            if (state.others.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.safe_to_spend_other_currencies, state.others.formatAmounts()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.safe_to_spend_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SheetRowLabels(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        subtitle?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
