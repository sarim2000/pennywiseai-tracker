package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate

/**
 * "Read messages again": scan every message again, or rebuild SMS
 * transactions from scratch. Each option closes the dialog, then acts.
 */
@Composable
internal fun FullResyncDialog(
    onDismiss: () -> Unit,
    onScanAgain: () -> Unit,
    onRebuild: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        title = {
            Text(stringResource(R.string.home_resync_title))
        },
        text = {
            // Two ways to re-read: the gentle one first, since a full rebuild
            // resets edits to SMS transactions.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ResyncOption(
                    title = stringResource(R.string.home_resync_scan_again),
                    description = stringResource(R.string.home_resync_scan_again_desc),
                    onClick = {
                        onDismiss()
                        onScanAgain()
                    }
                )
                ResyncOption(
                    title = stringResource(R.string.home_resync_rebuild),
                    description = stringResource(R.string.home_resync_rebuild_desc),
                    onClick = {
                        onDismiss()
                        onRebuild()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDismiss() },
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.home_cancel))
            }
        }
    )
}

/** One choice in the "Read messages again" dialog: a tappable title + what it does. */
@Composable
private fun ResyncOption(title: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        // The dialog itself is surfaceContainerHigh, so a step up keeps each
        // option reading as a tappable card.
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BreakdownDialog(
    currentMonthIncome: BigDecimal,
    currentMonthExpenses: BigDecimal,
    currentMonthTotal: BigDecimal,
    lastMonthIncome: BigDecimal,
    lastMonthExpenses: BigDecimal,
    lastMonthTotal: BigDecimal,
    currency: String,
    onDismiss: () -> Unit
) {
    val now = LocalDate.now()
    val monthName = { m: java.time.Month -> m.getDisplayName(java.time.format.TextStyle.FULL, AppDates.locale()) }
    val currentPeriod = stringResource(R.string.home_breakdown_period, monthName(now.month), now.dayOfMonth)
    val lastMonth = now.minusMonths(1)
    val lastPeriod = stringResource(R.string.home_breakdown_period, monthName(lastMonth.month), now.dayOfMonth)

    Dialog(onDismissRequest = onDismiss) {
        PennyWiseCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            contentPadding = Dimensions.Padding.dialog
        ) {
            // Period blocks are the unit of meaning here, so the gap *between*
            // blocks (lg) is larger than the gap between a block's own rows
            // (sm). The previous uniform 16dp made the period label float
            // equidistant from both periods' figures.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Text(
                    text = stringResource(R.string.home_breakdown_title),
                    style = MaterialTheme.typography.headlineSmall
                )

                BreakdownPeriod(
                    period = currentPeriod,
                    income = currentMonthIncome,
                    expenses = currentMonthExpenses,
                    net = currentMonthTotal,
                    currency = currency
                )

                BreakdownPeriod(
                    period = lastPeriod,
                    income = lastMonthIncome,
                    expenses = lastMonthExpenses,
                    net = lastMonthTotal,
                    currency = currency
                )

                PennyWiseCardV2(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = Spacing.smd
                ) {
                    Text(
                        text = stringResource(R.string.home_breakdown_formula),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.home_close))
                }
            }
        }
    }
}

/** One period's income / expenses / net block inside [BreakdownDialog]. */
@Composable
private fun BreakdownPeriod(
    period: String,
    income: BigDecimal,
    expenses: BigDecimal,
    net: BigDecimal,
    currency: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = period,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        BreakdownRow(label = stringResource(R.string.home_breakdown_income), amount = income, isIncome = true, currency = currency)
        BreakdownRow(label = stringResource(R.string.home_breakdown_expenses), amount = expenses, isIncome = false, currency = currency)
        HorizontalDivider()
        BreakdownRow(
            label = stringResource(R.string.home_breakdown_net_worth),
            amount = net,
            isIncome = net >= BigDecimal.ZERO,
            isBold = true,
            isNet = true,
            currency = currency
        )
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amount: BigDecimal,
    isIncome: Boolean,
    isBold: Boolean = false,
    isNet: Boolean = false,
    currency: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = CurrencyFormatter.signed(if (isIncome) "+" else "-", CurrencyFormatter.formatCurrency(amount.abs(), currency)),
            style = if (isBold) PennyWiseText.amountRow else PennyWiseText.amountRow.copy(
                fontWeight = FontWeight.Normal
            ),
            // Spending is ink; red is kept for a negative net.
            color = when {
                isIncome -> MaterialTheme.colorScheme.income
                isNet -> MaterialTheme.colorScheme.expense
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
