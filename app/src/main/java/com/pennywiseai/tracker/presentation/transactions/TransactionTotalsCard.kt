package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/**
 * The Activity summary: "Spent in Nov" and "Money in" side by side, plus the
 * total the two leave out when it matters (card spend, or the transfers /
 * investments a type filter shows; see [extraTotalFor]). Every figure is in the
 * one [currency] the list resolved its totals to (a single currency, or all
 * converted into it in unified mode), so nothing here adds across currencies.
 */
@Composable
internal fun ActivitySummaryTiles(
    totals: FilteredTotals,
    extra: ExtraTotal?,
    currency: String,
    spentLabel: String,
    isLoading: Boolean,
    /** Adds a Net tile (money in less spent), e.g. for a selection (#634). */
    showNet: Boolean = false,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (isLoading) Dimensions.Alpha.medium else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "summary_alpha"
    )
    val colors = MaterialTheme.colorScheme
    val spent = SummaryTile(spentLabel, CurrencyFormatter.formatCurrency(totals.expenses, currency), colors.onSurface)
    val moneyIn = SummaryTile(stringResource(R.string.txn_list_money_in), CurrencyFormatter.formatCurrency(totals.income, currency), colors.income)
    val extraTile = extra?.let {
        val (label, color) = when (it.kind) {
            ExtraTotalKind.CREDIT -> stringResource(R.string.txn_totals_credit) to colors.credit
            ExtraTotalKind.TRANSFER -> stringResource(R.string.txn_totals_transfer) to colors.transfer
            ExtraTotalKind.INVESTMENT -> stringResource(R.string.txn_totals_invest) to colors.investment
        }
        SummaryTile(label, CurrencyFormatter.formatCurrency(it.amount, currency), color)
    }
    // A Transfers or Investments filter shows rows Spent / Money in never count,
    // so their own total stands alone rather than beside two zeros (#893).
    // Net is cash flow in the same one currency: money in less spent (card spend,
    // transfers and investments stay out, as they do from those two tiles).
    val net = totals.netBalance
    val netTile = SummaryTile(
        stringResource(R.string.txn_totals_net),
        CurrencyFormatter.signed(if (net.signum() > 0) "+" else "", CurrencyFormatter.formatCurrency(net, currency)),
        when {
            net.signum() > 0 -> colors.income
            net.signum() < 0 -> colors.expense
            else -> colors.onSurfaceVariant
        }
    ).takeIf { showNet }
    val tiles = when (extra?.kind) {
        ExtraTotalKind.TRANSFER, ExtraTotalKind.INVESTMENT -> listOfNotNull(extraTile)
        else -> listOfNotNull(spent, moneyIn, extraTile, netTile)
    }

    val outer = MaterialTheme.shapes.largeIncreased
    val inner = MaterialTheme.shapes.small
    // Four tiles in one row squeeze the figures; two rows of two keep them whole.
    val rows = if (tiles.size > 3) tiles.chunked(2) else listOf(tiles)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                row.forEachIndexed { index, tile ->
                    SummaryTileView(
                        tile = tile,
                        shape = tileShape(outer, inner, start = index == 0, end = index == row.lastIndex),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

private data class SummaryTile(val label: String, val amount: String, val color: Color)

/** Rounded on the row's outer edges, near-square where two tiles meet. */
private fun tileShape(outer: CornerBasedShape, inner: CornerBasedShape, start: Boolean, end: Boolean) = outer.copy(
    topStart = if (start) outer.topStart else inner.topStart,
    bottomStart = if (start) outer.bottomStart else inner.bottomStart,
    topEnd = if (end) outer.topEnd else inner.topEnd,
    bottomEnd = if (end) outer.bottomEnd else inner.bottomEnd
)

@Composable
private fun SummaryTileView(tile: SummaryTile, shape: CornerBasedShape, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = tile.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = PennyWiseText.autoSizeFloor,
                maxFontSize = MaterialTheme.typography.labelMedium.fontSize
            )
        )
        // One line, shrinking to fit, so a long figure never wraps mid-number.
        Text(
            text = tile.amount,
            style = PennyWiseText.amountMedium,
            color = tile.color,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = PennyWiseText.autoSizeFloor,
                maxFontSize = PennyWiseText.amountMedium.fontSize
            )
        )
    }
}

/** Picks the one currency the list and its totals are shown in (several currencies, not unified). */
@Composable
internal fun CurrencyPickerPill(
    selectedCurrency: String,
    availableCurrencies: List<String>,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = Dimensions.Elevation.none,
            modifier = Modifier
                .height(Dimensions.Component.chipHeight)
                .clip(CircleShape)
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.smd),
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
                            imageVector = Icons.Default.Check,
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
 * The extra summary tile. Spent / Money in never include credit-card spend,
 * transfers or investments, so a list of only those read ₹0 / ₹0 (#893).
 * Transfer and Investment filters show their own total; the Credit filter and
 * a selection (#634) always show the credit tile, even at zero; otherwise it
 * shows whenever the shown rows have card spend.
 */
internal fun extraTotalFor(totals: FilteredTotals, typeFilter: TransactionTypeFilter, selectionMode: Boolean): ExtraTotal? = when {
    !selectionMode && typeFilter == TransactionTypeFilter.TRANSFER -> ExtraTotal(ExtraTotalKind.TRANSFER, totals.transfer)
    !selectionMode && typeFilter == TransactionTypeFilter.INVESTMENT -> ExtraTotal(ExtraTotalKind.INVESTMENT, totals.investment)
    selectionMode || typeFilter == TransactionTypeFilter.CREDIT || totals.credit.signum() != 0 -> ExtraTotal(ExtraTotalKind.CREDIT, totals.credit)
    else -> null
}
