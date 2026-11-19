package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.formatBalance
import com.pennywiseai.tracker.domain.spending.SafeToSpend
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SHORT_DATE
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.ui.theme.spending
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.Money
import java.time.LocalDate

private const val MASK = "••••••"

private fun SafeToSpend.formatAmount() = CurrencyFormatter.formatCurrency(amount, currency)

private fun List<SafeToSpend>.formatAmounts() =
    CurrencyFormatter.formatByCurrency(associate { it.currency to Money(it.amount, it.currency) })

/**
 * Home's "Safe to spend", as one row at the foot of the hero: the figure (per day when the
 * budget caps it at what the hero already shows as left) and the incomplete-data cue.
 * Balances, bills and other currencies are in the sheet. Follows the global hide-amounts
 * flag: while hidden, a tap reveals; once shown, a tap opens the breakdown sheet.
 */
@Composable
fun SafeToSpendLine(
    state: SafeToSpendUi,
    isBalanceHidden: Boolean,
    onReveal: () -> Unit,
    onOpenBreakdown: () -> Unit,
    modifier: Modifier = Modifier,
    /** True when the budget caps the figure at what Home's hero already shows as left: per day only. */
    repeatsHero: Boolean = false,
) {
    val primary = state.primary
    val perDay = primary.perDay
    val dueBefore = state.periodEnd.plusDays(1).format(SHORT_DATE)
    val short = primary.amount.signum() < 0
    Surface(
        onClick = if (isBalanceHidden) onReveal else onOpenBreakdown,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Dimensions.Component.minTouchTarget)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(R.string.safe_to_spend_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        isBalanceHidden -> MASK
                        perDay != null && repeatsHero -> stringResource(
                            R.string.safe_to_spend_per_day_headline,
                            CurrencyFormatter.formatCurrency(perDay, primary.currency)
                        )
                        perDay != null -> stringResource(
                            R.string.safe_to_spend_line,
                            primary.formatAmount(),
                            CurrencyFormatter.formatCurrency(perDay, primary.currency)
                        )
                        short -> stringResource(R.string.safe_to_spend_short, dueBefore)
                        // Budget used up: the cap, not "nothing left after bills", sets it.
                        primary.isCapped -> primary.formatAmount()
                        else -> stringResource(R.string.safe_to_spend_none_left, dueBefore)
                    },
                    style = MaterialTheme.typography.titleMedium.money(),
                    fontWeight = FontWeight.SemiBold,
                    color = if (short && !isBalanceHidden) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                if (!isBalanceHidden) IncompleteLine(state.cardsNeedingBill.size)
            }
            if (isBalanceHidden) {
                Text(
                    text = stringResource(R.string.safe_to_spend_tap_to_show),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.Icon.inline)
                )
            }
        }
    }
}

/** "Incomplete: 1 card bill isn't set aside", when a card's bill is missing; the sheet says which. */
@Composable
private fun IncompleteLine(cards: Int) {
    if (cards == 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimensions.Icon.inline)
        )
        Text(
            text = pluralStringResource(R.plurals.safe_to_spend_incomplete, cards, cards),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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

/** Shows the per-day line; false when there's none to show (the budget cap says why). */
@Composable
private fun PerDayLine(s: SafeToSpend, until: String, dueBefore: String): Boolean {
    val perDay = s.perDay
    // Budget used up: CappedLine says so, "nothing left after bills" would be wrong.
    if (perDay == null && s.isCapped) return false
    val short = s.amount.signum() < 0
    Text(
        text = when {
            // A date, not a day count: Home's "7 days left" excludes today, this includes it.
            perDay != null -> stringResource(
                R.string.safe_to_spend_per_day,
                CurrencyFormatter.formatCurrency(perDay, s.currency), until
            )
            short -> stringResource(R.string.safe_to_spend_short, dueBefore)
            else -> stringResource(R.string.safe_to_spend_none_left, dueBefore)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    )
    return true
}

/** Breakdown behind [SafeToSpendLine]: accounts counted, bills due, and the total. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeToSpendSheet(
    state: SafeToSpendUi,
    onSetIncluded: (AccountBalanceEntity, Boolean) -> Unit,
    onOpenCard: (AccountBalanceEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val primary = state.primary
    val until = state.periodEnd.format(SHORT_DATE)
    val dueBefore = state.periodEnd.plusDays(1).format(SHORT_DATE)
    val today = LocalDate.now()
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
            Text(
                text = primary.formatAmount(),
                style = PennyWiseText.amountLarge,
                fontWeight = FontWeight.Bold,
                color = if (primary.amount.signum() < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            PerDayLine(primary, until, dueBefore)
            CappedLine(primary)
            Text(
                text = stringResource(
                    R.string.safe_to_spend_breakdown,
                    CurrencyFormatter.formatCurrency(primary.balance, primary.currency),
                    CurrencyFormatter.formatCurrency(primary.bills, primary.currency),
                    dueBefore
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.others.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.safe_to_spend_other_currencies, state.others.formatAmounts()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionHeaderV2(
                title = stringResource(R.string.safe_to_spend_accounts),
                action = {
                    TextButton(onClick = { choosing = !choosing }, shapes = ButtonDefaults.shapes()) {
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
                                text = account.formatBalance(),
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
                            val overdue = bill.isCardBill && bill.dueDate.isBefore(today)
                            val due = if (overdue) {
                                stringResource(R.string.safe_to_spend_overdue, bill.dueDate.format(SHORT_DATE))
                            } else {
                                bill.dueDate.format(SHORT_DATE)
                            }
                            SheetRowLabels(
                                title = bill.name,
                                // A card bill's name is the card's, so say what it is.
                                subtitle = if (bill.isCardBill) stringResource(R.string.safe_to_spend_card_bill, due) else due,
                                subtitleColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "-" + CurrencyFormatter.formatCurrency(bill.amount.amount, bill.amount.currency),
                                style = PennyWiseText.amountRow,
                                color = MaterialTheme.colorScheme.spending
                            )
                        }
                    }
                }
            }

            if (state.cardsNeedingBill.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                // One row per card, each opening that card's page to add its bill.
                GroupedList {
                    state.cardsNeedingBill.forEachIndexed { i, card ->
                        GroupedRow(
                            position = ListItemPosition.from(i, state.cardsNeedingBill.size),
                            onClick = { onOpenCard(card) }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Dimensions.Icon.inline)
                            )
                            SheetRowLabels(
                                title = stringResource(R.string.safe_to_spend_cards_incomplete, card.displayLabel),
                                subtitle = null,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Dimensions.Icon.inline)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            // Inset like the grouped rows above, so the total lines up with their amounts.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.safe_to_spend_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = (listOf(primary) + state.others).formatAmounts(),
                    style = PennyWiseText.amountMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (primary.amount.signum() < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
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
private fun SheetRowLabels(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        subtitle?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
        }
    }
}
