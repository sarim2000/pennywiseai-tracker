package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

@Composable
fun TransactionTotalsCard(
    income: BigDecimal,
    expenses: BigDecimal,
    netBalance: BigDecimal,
    currency: String,
    availableCurrencies: List<String> = emptyList(),
    onCurrencySelected: (String) -> Unit = {},
    isUnifiedMode: Boolean = false,
    isLoading: Boolean = false,
    // Optional fourth tile: a total the Income/Expenses/Net tiles leave out —
    // credit-card spend, or the transfers/investments a type filter shows
    // (#634, #893). See [extraTotalFor].
    extra: ExtraTotal? = null,
    // Optional heading, e.g. "3 selected".
    title: String? = null,
    modifier: Modifier = Modifier
) {
    val incomeAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.5f else 1f,
        animationSpec = tween(300),
        label = "income_alpha"
    )

    val expenseAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.5f else 1f,
        animationSpec = tween(300),
        label = "expense_alpha"
    )

    val netAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.5f else 1f,
        animationSpec = tween(300),
        label = "net_alpha"
    )

    // The bottom inset only exists to host the optional [CurrencyPickerPill],
    // which floats at BottomEnd of the parent Box. When no pill is rendered
    // (single-currency case) keep the card flush — otherwise a phantom blank
    // band sits under the totals.
    val hasCurrencyPill = availableCurrencies.size > 1 && !isUnifiedMode
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasCurrencyPill) Modifier.padding(bottom = Spacing.lg) else Modifier),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm)
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs)
                    )
                }
                // Totals Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Income Column
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(
                                    topEnd = Spacing.xs,
                                    topStart = Spacing.md,
                                    bottomEnd = Spacing.xs,
                                    bottomStart = Spacing.md
                                )
                            )
                            .padding(Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        TotalColumn(
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = stringResource(R.string.txn_totals_income),
                                    modifier = Modifier.size(Dimensions.Icon.inline),
                                    tint = if (!isSystemInDarkTheme()) income_light else income_dark
                                )
                            },
                            label = stringResource(R.string.txn_totals_income),
                            amount = CurrencyFormatter.formatCurrency(income, currency),
                            color = if (!isSystemInDarkTheme()) income_light else income_dark,
                            modifier = Modifier.alpha(incomeAlpha)
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.xxs))

                    // Expenses Column
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(
                                    topEnd = Spacing.xs,
                                    topStart = Spacing.xs,
                                    bottomEnd = Spacing.xs,
                                    bottomStart = Spacing.xs
                                )
                            )
                            .padding(Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        TotalColumn(
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = stringResource(R.string.txn_totals_expenses),
                                    modifier = Modifier.size(Dimensions.Icon.inline),
                                    tint = if (!isSystemInDarkTheme()) expense_light else expense_dark
                                )
                            },
                            // Four tiles leave no room for the plural — it wraps.
                            label = if (extra != null) stringResource(R.string.txn_totals_expense) else stringResource(R.string.txn_totals_expenses),
                            amount = CurrencyFormatter.formatCurrency(expenses, currency),
                            color = if (!isSystemInDarkTheme()) expense_light else expense_dark,
                            modifier = Modifier.alpha(expenseAlpha)
                        )
                    }

                    if (extra != null) {
                        Spacer(modifier = Modifier.width(Spacing.xxs))

                        val dark = isSystemInDarkTheme()
                        val (extraIcon, extraLabel, extraColor) = when (extra.kind) {
                            ExtraTotalKind.CREDIT -> Triple(Icons.Filled.CreditCard, stringResource(R.string.txn_totals_credit), if (dark) credit_dark else credit_light)
                            ExtraTotalKind.TRANSFER -> Triple(Icons.Filled.SwapHoriz, stringResource(R.string.txn_totals_transfer), if (dark) transfer_dark else transfer_light)
                            ExtraTotalKind.INVESTMENT -> Triple(Icons.AutoMirrored.Filled.ShowChart, stringResource(R.string.txn_totals_invest), if (dark) investment_dark else investment_light)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(Spacing.xs)
                                )
                                .padding(Spacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            TotalColumn(
                                icon = {
                                    Icon(
                                        imageVector = extraIcon,
                                        contentDescription = extraLabel,
                                        modifier = Modifier.size(Dimensions.Icon.inline),
                                        tint = extraColor
                                    )
                                },
                                label = extraLabel,
                                amount = CurrencyFormatter.formatCurrency(extra.amount, currency),
                                color = extraColor,
                                modifier = Modifier.alpha(expenseAlpha)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(Spacing.xxs))

                    // Net Balance Column
                    val netColor = when {
                        netBalance > BigDecimal.ZERO -> if (!isSystemInDarkTheme()) income_light else income_dark
                        netBalance < BigDecimal.ZERO -> if (!isSystemInDarkTheme()) expense_light else expense_dark
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    val netPrefix = when {
                        netBalance > BigDecimal.ZERO -> "+"
                        else -> ""
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(
                                    topEnd = Spacing.md,
                                    topStart = Spacing.xs,
                                    bottomEnd = Spacing.md,
                                    bottomStart = Spacing.xs
                                )
                            )
                            .padding(Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        TotalColumn(
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.SettingsEthernet,
                                    contentDescription = stringResource(R.string.txn_totals_net),
                                    modifier = Modifier.size(Dimensions.Icon.inline),
                                    tint = netColor
                                )
                            },
                            label = stringResource(R.string.txn_totals_net),
                            amount = "$netPrefix${CurrencyFormatter.formatCurrency(netBalance, currency)}",
                            color = netColor,
                            modifier = Modifier.alpha(netAlpha)
                        )
                    }
                }
            }
        }

        if (hasCurrencyPill) {
            CurrencyPickerPill(
                selectedCurrency = currency,
                availableCurrencies = availableCurrencies,
                onCurrencySelected = onCurrencySelected,
                modifier = Modifier.padding(end = Spacing.sm)
            )
        }
    }
}

@Composable
private fun TotalColumn(
    icon: @Composable (() -> Unit)?,
    label: String,
    amount: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            icon?.invoke()
            // One line, shrinking to fit: four tiles at a large font size
            // otherwise break "Income" mid-word.
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = MaterialTheme.typography.labelMedium.fontSize
                )
            )
        }
        Text(
            text = amount,
            style = PennyWiseText.amountRow,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
        )
    }
}

@Composable
private fun CurrencyPickerPill(
    selectedCurrency: String,
    availableCurrencies: List<String>,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Dimensions.Elevation.none,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = selectedCurrency,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.txn_totals_select_currency),
                    modifier = Modifier.size(Dimensions.Icon.small),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large
        ) {
            availableCurrencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency) },
                    onClick = {
                        onCurrencySelected(currency)
                        expanded = false
                    },
                    leadingIcon = {
                        if (currency == selectedCurrency) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    }
}

enum class ExtraTotalKind { CREDIT, TRANSFER, INVESTMENT }

data class ExtraTotal(val kind: ExtraTotalKind, val amount: BigDecimal)

/**
 * The fourth tile for the totals card. Income/Expenses/Net never include
 * credit-card spend, transfers or investments (Net is cash flow), so a list of
 * only those read ₹0 / ₹0 / ₹0 (#893). Transfer and Investment filters show
 * their own total; otherwise credit spend shows whenever the shown rows have
 * any (and always for a selection, #634).
 */
internal fun extraTotalFor(totals: FilteredTotals, typeFilter: TransactionTypeFilter, selectionMode: Boolean): ExtraTotal? = when {
    !selectionMode && typeFilter == TransactionTypeFilter.TRANSFER -> ExtraTotal(ExtraTotalKind.TRANSFER, totals.transfer)
    !selectionMode && typeFilter == TransactionTypeFilter.INVESTMENT -> ExtraTotal(ExtraTotalKind.INVESTMENT, totals.investment)
    selectionMode || totals.credit.signum() != 0 -> ExtraTotal(ExtraTotalKind.CREDIT, totals.credit)
    else -> null
}
