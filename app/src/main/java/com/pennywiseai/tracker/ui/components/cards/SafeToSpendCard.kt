package com.pennywiseai.tracker.ui.components.cards

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.spending.SafeToSpend
import com.pennywiseai.tracker.presentation.home.SafeToSpendUi
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
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        contentPadding = Spacing.md,
        onClick = if (isBalanceHidden) onReveal else onOpenBreakdown
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.safe_to_spend_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isBalanceHidden) {
                Text(
                    text = stringResource(R.string.safe_to_spend_tap_to_show),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
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
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = if (isBalanceHidden) MASK else primary.formatAmount(),
            style = PennyWiseText.amountLarge,
            fontWeight = FontWeight.Bold,
            color = if (!isBalanceHidden && primary.amount.signum() < 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
        if (isBalanceHidden) return@PennyWiseCardV2

        val dueBefore = state.periodEnd.plusDays(1).format(SHORT_DATE)
        Spacer(Modifier.height(Spacing.xs))
        PerDayLine(primary, dueBefore)
        Spacer(Modifier.height(Spacing.xxs))
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
    }
}

@Composable
private fun PerDayLine(s: SafeToSpend, dueBefore: String) {
    val perDay = s.perDay
    Text(
        text = if (perDay != null) {
            pluralStringResource(
                R.plurals.safe_to_spend_per_day, s.days,
                CurrencyFormatter.formatCurrency(perDay, s.currency), s.days
            )
        } else {
            stringResource(R.string.safe_to_spend_short, dueBefore)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (perDay != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
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
            PerDayLine(primary, dueBefore)

            SectionHeaderV2(
                title = stringResource(R.string.safe_to_spend_accounts),
                action = {
                    TextButton(onClick = { choosing = !choosing }) {
                        Text(stringResource(if (choosing) R.string.safe_to_spend_done else R.string.safe_to_spend_choose_accounts))
                    }
                }
            )
            GroupedList {
                state.accounts.forEachIndexed { i, account ->
                    val included = safeToSpendKey(account) !in state.excludedKeys
                    GroupedRow(
                        position = ListItemPosition.from(i, state.accounts.size),
                        onClick = if (choosing) ({ onSetIncluded(account, !included) }) else null
                    ) {
                        SheetRowLabels(
                            title = account.displayLabel,
                            subtitle = if (included) null else stringResource(R.string.safe_to_spend_excluded),
                            modifier = Modifier.weight(1f)
                        )
                        if (choosing) {
                            Checkbox(checked = included, onCheckedChange = { onSetIncluded(account, it) })
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
                            SheetRowLabels(
                                title = bill.name,
                                subtitle = bill.dueDate.format(SHORT_DATE),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "-" + CurrencyFormatter.formatCurrency(bill.amount.amount, bill.amount.currency),
                                style = PennyWiseText.amountRow,
                                color = MaterialTheme.colorScheme.expense
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
private fun SheetRowLabels(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        subtitle?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
