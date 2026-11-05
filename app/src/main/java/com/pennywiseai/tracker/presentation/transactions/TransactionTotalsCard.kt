package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.ui.components.cards.ledgerRule
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import com.pennywiseai.tracker.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.AmountPrivacy
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
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "income_alpha"
    )

    val expenseAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.5f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "expense_alpha"
    )

    val netAlpha by animateFloatAsState(
        targetValue = if (isLoading) 0.5f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "net_alpha"
    )

    val hasCurrencyPill = availableCurrencies.size > 1 && !isUnifiedMode
    Column(modifier = modifier.fillMaxWidth()) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                }
                val dark = isAppInDarkTheme()
                val netColor = when {
                    // While hidden, the colour mustn't give away the sign either.
                    AmountPrivacy.hidden -> MaterialTheme.colorScheme.onSurface
                    netBalance > BigDecimal.ZERO -> if (!dark) income_light else income_dark
                    netBalance < BigDecimal.ZERO -> if (!dark) expense_light else expense_dark
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val tiles = buildList {
                    add(TotalTileData(Icons.AutoMirrored.Outlined.TrendingUp, stringResource(R.string.txn_totals_income),
                        CurrencyFormatter.formatCurrency(income, currency), if (!dark) income_light else income_dark, incomeAlpha))
                    add(TotalTileData(Icons.AutoMirrored.Outlined.TrendingDown, stringResource(R.string.txn_totals_expenses),
                        CurrencyFormatter.formatCurrency(expenses, currency), MaterialTheme.colorScheme.onSurface, expenseAlpha))
                    if (extra != null) {
                        val (extraIcon, extraLabel, extraColor) = when (extra.kind) {
                            ExtraTotalKind.CREDIT -> Triple(Icons.Outlined.CreditCard, stringResource(R.string.txn_totals_credit), if (dark) credit_dark else credit_light)
                            ExtraTotalKind.TRANSFER -> Triple(Icons.Outlined.SwapHoriz, stringResource(R.string.txn_totals_transfer), if (dark) transfer_dark else transfer_light)
                            ExtraTotalKind.INVESTMENT -> Triple(Icons.AutoMirrored.Outlined.ShowChart, stringResource(R.string.txn_totals_invest), if (dark) investment_dark else investment_light)
                        }
                        add(TotalTileData(extraIcon, extraLabel, CurrencyFormatter.formatCurrency(extra.amount, currency), extraColor, expenseAlpha))
                    }
                    add(TotalTileData(Icons.Outlined.SettingsEthernet, stringResource(R.string.txn_totals_net),
                        CurrencyFormatter.formatSigned(netBalance, currency), netColor, netAlpha))
                }
                // Four tiles in one row squeezed the amounts into a scrolling
                // marquee; two rows of two keep every figure whole.
                // At large font, one per row: side by side each shrank by a different amount.
                val rows = when {
                    LocalDensity.current.fontScale >= 1.5f -> tiles.chunked(1)
                    tiles.size > 3 -> tiles.chunked(2)
                    else -> listOf(tiles)
                }
                // A strip of the statement: labels over figures, rules above and below, no boxes.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .ledgerRule()
                        .padding(vertical = Spacing.smd),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    rows.forEachIndexed { r, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { tile ->
                                TotalColumn(
                                    icon = null,
                                    label = tile.label,
                                    amount = tile.amount,
                                    color = tile.color,
                                    modifier = Modifier.weight(1f).alpha(tile.alpha)
                                )
                            }
                            if (r == 0 && hasCurrencyPill) {
                                CurrencyPickerPill(
                                    selectedCurrency = currency,
                                    availableCurrencies = availableCurrencies,
                                    onCurrencySelected = onCurrencySelected
                                )
                            }
                        }
                    }
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
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
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
                autoSize = PennyWiseText.fit(MaterialTheme.typography.labelMedium)
            )
        }
        // Shrinks to fit. A marquee here showed "580.802" mid-scroll with the sign and
        // currency off-screen, which is a different number.
        Text(
            text = amount,
            style = PennyWiseText.amountRow,
            color = color,
            maxLines = 1,
            autoSize = PennyWiseText.fit(PennyWiseText.amountRow)
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
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer,
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
                    imageVector = Icons.Outlined.ExpandMore,
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
            availableCurrencies.forEachIndexed { index, currency ->
                SelectableDropdownMenuItem(
                    selected = currency == selectedCurrency,
                    text = { Text(currency) },
                    onClick = {
                        onCurrencySelected(currency)
                        expanded = false
                    },
                    shapes = MenuDefaults.itemShape(index, availableCurrencies.size),
                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                    selectedLeadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null
                        )
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
 * their own total; the Credit filter and a selection (#634) always show the
 * credit tile, even at zero; otherwise it shows whenever the shown rows have
 * card spend.
 */
internal fun extraTotalFor(totals: FilteredTotals, typeFilter: TransactionTypeFilter, selectionMode: Boolean): ExtraTotal? = when {
    !selectionMode && typeFilter == TransactionTypeFilter.TRANSFER -> ExtraTotal(ExtraTotalKind.TRANSFER, totals.transfer)
    !selectionMode && typeFilter == TransactionTypeFilter.INVESTMENT -> ExtraTotal(ExtraTotalKind.INVESTMENT, totals.investment)
    selectionMode || typeFilter == TransactionTypeFilter.CREDIT || totals.credit.signum() != 0 -> ExtraTotal(ExtraTotalKind.CREDIT, totals.credit)
    else -> null
}

private data class TotalTileData(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val amount: String,
    val color: androidx.compose.ui.graphics.Color,
    val alpha: Float
)

