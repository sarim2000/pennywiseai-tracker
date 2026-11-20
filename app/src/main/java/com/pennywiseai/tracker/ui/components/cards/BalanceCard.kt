package com.pennywiseai.tracker.ui.components.cards

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.AnimatedCurrencyText
import com.pennywiseai.tracker.ui.components.WavyMeter
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.GRAPHIC_CONTRAST
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.clickableFillFor
import com.pennywiseai.tracker.ui.theme.expense
import com.pennywiseai.tracker.ui.theme.income
import com.pennywiseai.tracker.ui.theme.loan
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.ui.theme.onColoredFill
import com.pennywiseai.tracker.ui.theme.onSurfaceWash
import com.pennywiseai.tracker.ui.theme.readableOn
import com.pennywiseai.tracker.ui.theme.stateLayer
import com.pennywiseai.tracker.ui.theme.tabular
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle as DateTextStyle

/**
 * The overall budget Home's hero leads with: one spending limit on all expenses that
 * runs today, every figure in its one [currency].
 */
@Immutable
data class HomeHeroBudget(
    /** The budget's id, so the budget carousel can leave out the one the hero already shows. */
    val budgetId: Long,
    /** The budget's own name; null for a monthly budget, which the hero names by the month. */
    val name: String?,
    val spent: BigDecimal,
    val limit: BigDecimal,
    val currency: String,
    /** Whole days after today until the window ends; 0 on its last day. */
    val daysLeft: Int,
    /** Where the window should end at this pace: under (+) or over (−) the limit; null too early to say. */
    val pace: BigDecimal? = null,
) {
    val remaining: BigDecimal get() = limit - spent

    /** What's left spread over the days still to come, today included. */
    val perDay: BigDecimal
        get() = remaining.coerceAtLeast(BigDecimal.ZERO)
            .divide(BigDecimal(daysLeft + 1), 0, RoundingMode.HALF_UP)

    val fractionUsed: Float
        get() = if (limit.signum() > 0) (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 1f
}

/**
 * Home's hero: a big tonal card with one figure. With an overall [budget] it leads with
 * what's left to spend, a wavy meter and the days left; without one, with this month's
 * spending in [currency] and how it compares to last month. [footer] (Safe to spend) sits
 * at its foot. Tapping it opens the month's detail (the limit, the pace, sparkline,
 * income/expenses/saved, balance) with links to that budget or the month's transactions
 * ([onOpen]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BalanceCard(
    totalBalance: BigDecimal,
    monthlyChange: BigDecimal,
    /** Null hides the change line (no last-month spending to compare with). */
    monthlyChangePercent: Int?,
    currency: String,
    currentMonthIncome: BigDecimal,
    currentMonthExpenses: BigDecimal,
    currentMonthLent: BigDecimal = BigDecimal.ZERO,
    currentMonthTotal: BigDecimal,
    spendingHistory: List<BigDecimal> = emptyList(),
    lastMonthSpendingHistory: List<BigDecimal> = emptyList(),
    lastMonthSpending: BigDecimal = BigDecimal.ZERO,
    availableCurrencies: List<String>,
    isUnifiedMode: Boolean = false,
    isApproximate: Boolean = false,
    onCurrencySelected: (String) -> Unit,
    /** Opens the income / expenses / net breakdown, from the opened card. */
    onShowBreakdown: () -> Unit = {},
    isBalanceHidden: Boolean = false,
    onToggleBalanceVisibility: () -> Unit = {},
    /** Shows the accounts' total in the opened card when there are any. */
    hasAccounts: Boolean = false,
    /** Leads with what's left of this budget when set; this month's spending otherwise. */
    budget: HomeHeroBudget? = null,
    /** The last day of the spending cycle, for "days left" without a budget. */
    cycleEnd: LocalDate? = null,
    /** The labelled action: the budget with a [budget], the month's transactions without. */
    onOpen: () -> Unit = {},
    /** Opens Net worth from a labelled link in the expanded card; null hides the link. */
    onOpenNetWorth: (() -> Unit)? = null,
    /** A row at the card's foot, under the opened detail: Home's Safe to spend. */
    footer: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // The month's figure is Home's hero: a tonal primary card, its text and chips
    // retoned to sit on it.
    val outer = MaterialTheme.colorScheme
    // Nudged a touch where needed, so the card's text still reads while it's pressed or focused.
    // Remembered: a new scheme each frame (the chevron animates) would invalidate the whole card.
    val fill = remember(outer) { outer.primaryContainer.clickableFillFor(outer.onPrimaryContainer) }
    val cardScheme = remember(outer, fill) { outer.onColoredFill(fill, outer.onPrimaryContainer) }
    MaterialTheme(colorScheme = cardScheme) {
        var isExpanded by rememberSaveable { mutableStateOf(false) }
        val view = LocalView.current
        val chevronRotation by animateFloatAsState(
            targetValue = if (isExpanded) 180f else 0f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "chevronRotation"
        )
        val hidden: (BigDecimal, String) -> String = { amount, code ->
            if (isBalanceHidden) MASK else CurrencyFormatter.formatCurrency(amount, code)
        }
        val toggleHidden = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onToggleBalanceVisibility()
        }
        val changeText = monthlyChangePercent?.let { percent ->
            when {
                isBalanceHidden -> MASK
                monthlyChange >= BigDecimal.ZERO ->
                    stringResource(R.string.balance_card_change_more_same_point, kotlin.math.abs(percent))
                else -> stringResource(R.string.balance_card_change_less_same_point, kotlin.math.abs(percent))
            }
        }
        // Inverted for spending: more spent = red, less = green, each nudged until it reads on
        // the card (a saturated teal card turned the red to 1.3:1); the words say it either way.
        // Text on a mid-tone card can only be near white or black, so a trend arrow (a mark,
        // 3:1) keeps the hue there and says the direction without colour.
        val change = when {
            monthlyChangePercent == 0 || isBalanceHidden -> null
            monthlyChange >= BigDecimal.ZERO -> ChangeTrend(outer.expense, up = true)
            else -> ChangeTrend(outer.income, up = false)
        }
        val changeColor = change?.color?.textOn(fill) ?: MaterialTheme.colorScheme.onSurfaceVariant
        val changeIcon: (@Composable () -> Unit)? = change?.let { trend -> { ChangeArrow(trend, fill) } }
        val showCurrencyChip = availableCurrencies.size > 1 && !isUnifiedMode

        Surface(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                isExpanded = !isExpanded
            },
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = fill,
            // The card's ink, as onColoredFill nudged it: it's also the press and focus layer.
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                HeroHeader(
                    label = heroLabel(budget),
                    isBalanceHidden = isBalanceHidden,
                    onToggleBalanceVisibility = toggleHidden,
                    // With a budget the hero's figures are all in its currency; the
                    // month's currency picker sits with the month's spending, when opened.
                    currencyChip = if (budget == null && showCurrencyChip) {
                        { OnWash(outer, fill) { CurrencyChip(currency, availableCurrencies, onCurrencySelected) } }
                    } else {
                        null
                    },
                )
                val heroStyle = PennyWiseText.heroAmount
                AnimatedCurrencyText(
                    text = if (budget != null) {
                        hidden(budget.remaining.abs(), budget.currency)
                    } else {
                        hidden(currentMonthExpenses, currency)
                    },
                    style = heroStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    // A long amount shrinks to fit rather than wrapping mid-number.
                    autoSize = TextAutoSize.StepBased(PennyWiseText.autoSizeFloor, heroStyle.fontSize),
                )
                // Hidden amounts hide the meter too: a known limit and its fill give the spend away.
                if (budget != null && !isBalanceHidden) BudgetMeter(budget = budget, containerColor = fill)
                HeroFooter(
                    line = if (budget != null) daysLeftText(budget.daysLeft) else monthLine(cycleEnd),
                    lineColor = MaterialTheme.colorScheme.onSurface,
                    trailing = if (budget == null && changeText != null) changeText else null,
                    trailingColor = changeColor,
                    trailingIcon = changeIcon,
                    chevronRotation = chevronRotation,
                    isExpanded = isExpanded,
                )

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = com.pennywiseai.tracker.ui.animations.pwExpand(),
                    exit = com.pennywiseai.tracker.ui.animations.pwCollapse(),
                ) {
                    Column {
                        if (budget != null) {
                            Spacer(modifier = Modifier.height(Spacing.md))
                            // The limit only: what's spent is under Expenses below.
                            Text(
                                text = stringResource(R.string.home_hero_budget_limit, hidden(budget.limit, budget.currency)),
                                style = MaterialTheme.typography.bodyLarge.tabular(),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            paceText(budget, hidden)?.let { pace ->
                                Text(
                                    text = pace,
                                    style = MaterialTheme.typography.bodyMedium,
                                    // The card's own ink: red isn't checked against this fill, and the
                                    // wording already says it's over.
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // The month's spending is under Expenses below; only its currency
                            // and the change on last month are new here.
                            if (showCurrencyChip || changeText != null) {
                                Spacer(modifier = Modifier.height(Spacing.md))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    itemVerticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (showCurrencyChip) OnWash(outer, fill) { CurrencyChip(currency, availableCurrencies, onCurrencySelected) }
                                    if (changeText != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                        ) {
                                            changeIcon?.invoke()
                                            Text(text = changeText, style = MaterialTheme.typography.bodyMedium, color = changeColor)
                                        }
                                    }
                                }
                            }
                        }
                        MonthDetails(
                            fill = fill,
                            totalBalance = totalBalance,
                            currency = currency,
                            currentMonthIncome = currentMonthIncome,
                            currentMonthExpenses = currentMonthExpenses,
                            currentMonthLent = currentMonthLent,
                            currentMonthTotal = currentMonthTotal,
                            spendingHistory = spendingHistory,
                            lastMonthSpendingHistory = lastMonthSpendingHistory,
                            lastMonthSpending = lastMonthSpending,
                            isApproximate = isApproximate,
                            isBalanceHidden = isBalanceHidden,
                            hasAccounts = hasAccounts,
                            onOpenNetWorth = onOpenNetWorth,
                            onShowBreakdown = onShowBreakdown,
                            actionLabel = stringResource(
                                if (budget != null) R.string.home_hero_view_budget else R.string.home_hero_see_transactions
                            ),
                            onAction = onOpen,
                        )
                    }
                }
                // Safe to spend sits on a surface wash, so it takes the surface's inks, not the card's.
                footer?.let { OnWash(outer, fill, it) }
            }
        }
    }
}

/** Where the budget's window should end at the pace so far; null too early (or too late) to say. */
@Composable
private fun paceText(budget: HomeHeroBudget, format: (BigDecimal, String) -> String): String? {
    val pace = budget.pace ?: return null
    return if (pace.signum() >= 0) {
        stringResource(R.string.home_nudge_under_budget, format(pace, budget.currency))
    } else {
        stringResource(R.string.home_nudge_over_budget, format(pace.abs(), budget.currency))
    }
}

/** "11 days left" in the spending cycle, or null once it's over. */
@Composable
private fun monthLine(cycleEnd: LocalDate?): String? {
    val today = LocalDate.now()
    val days = cycleEnd?.takeIf { !it.isBefore(today) }?.let { ChronoUnit.DAYS.between(today, it).toInt() }
    return days?.let { daysLeftText(it) }
}

/**
 * The hero's last row: one status line (pace, or days left and the change on last month)
 * and the chevron that says the card opens. The line wraps; the chevron keeps the end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroFooter(
    line: String?,
    lineColor: Color,
    trailing: String?,
    trailingColor: Color,
    trailingIcon: (@Composable () -> Unit)?,
    chevronRotation: Float,
    isExpanded: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (line != null) {
                Text(
                    text = if (trailing != null) "$line ·" else line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = lineColor
                )
            }
            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    trailingIcon?.invoke()
                    Text(text = trailing, style = MaterialTheme.typography.bodyMedium, color = trailingColor)
                }
            }
        }
        // The whole card is the target; this only says it opens.
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(if (isExpanded) R.string.card_collapse else R.string.card_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(Dimensions.Icon.inline)
                .rotate(chevronRotation)
        )
    }
}

/** "Left to spend in November", "Over in Groceries", or "Spent this month". */
@Composable
private fun heroLabel(budget: HomeHeroBudget?): String {
    if (budget == null) return stringResource(R.string.balance_card_spent_this_month)
    val over = budget.remaining.signum() < 0
    val name = budget.name
    return if (name == null) {
        val month = LocalDate.now().month.getDisplayName(DateTextStyle.FULL, AppDates.locale())
        stringResource(if (over) R.string.home_hero_over_in_month else R.string.home_hero_left_in_month, month)
    } else {
        stringResource(if (over) R.string.home_hero_over_in_budget else R.string.home_hero_left_in_budget, name)
    }
}

@Composable
private fun daysLeftText(days: Int): String =
    if (days <= 0) stringResource(R.string.home_hero_last_day)
    else pluralStringResource(R.plurals.home_hero_days_left, days, days)

@Composable
private fun HeroHeader(
    label: String,
    isBalanceHidden: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    currencyChip: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        currencyChip?.invoke()
        IconButton(onClick = onToggleBalanceVisibility, shapes = IconButtonDefaults.shapes()) {
            Icon(
                imageVector = if (isBalanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = stringResource(if (isBalanceHidden) R.string.balance_show else R.string.balance_hide),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
    }
}

/** The budget's wavy meter, standing still: warning from 70% used, error from 90%. */
@Composable
private fun BudgetMeter(budget: HomeHeroBudget, containerColor: Color) {
    val progress by animateFloatAsState(
        targetValue = budget.fractionUsed,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "heroBudgetProgress"
    )
    val color = when {
        progress >= DANGER_AT -> MaterialTheme.colorScheme.error
        progress >= WARNING_AT -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.primary
    }
    // The figures sit right under it, so the meter itself needs no text.
    WavyMeter(
        fraction = progress,
        color = color,
        // An ink track near the card's own tone: against the surface wash no colour reached
        // 3:1 on both it and the card in palettes like Monochrome. Checked at rest, not under the
        // card's press layer: there the three states turned one white on mid-tone fills.
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = Dimensions.Alpha.meterTrack),
        containerColor = containerColor,
        modifier = Modifier.clearAndSetSemantics { }
    )
}

/** The currency pill on the hero; opens the currency list. */
@Composable
private fun HeroChip(text: String, onClick: () -> Unit, trailingLabel: String) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Dimensions.Component.chipHeight)
                .padding(horizontal = Spacing.smd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = trailingLabel, modifier = Modifier.size(Dimensions.Icon.small))
        }
    }
}

/** The spending currency, as a chip that opens the list of the user's currencies. */
@Composable
private fun CurrencyChip(
    currency: String,
    currencies: List<String>,
    onCurrencySelected: (String) -> Unit,
) {
    Box {
        var showMenu by remember { mutableStateOf(false) }
        HeroChip(
            text = currency,
            onClick = { showMenu = true },
            trailingLabel = stringResource(R.string.balance_card_change_currency)
        )
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = MaterialTheme.shapes.large
        ) {
            currencies.forEachIndexed { index, code ->
                SelectableDropdownMenuItem(
                    selected = code == currency,
                    text = { Text(code) },
                    onClick = {
                        if (code != currency) onCurrencySelected(code)
                        showMenu = false
                    },
                    shapes = MenuDefaults.itemShape(index, currencies.size),
                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                    selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                )
            }
        }
    }
}

/** The opened card: the month's sparkline, income / expenses / saved, and the accounts' total. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDetails(
    /** The hero's fill: the series colours are nudged to 3:1 on it. */
    fill: Color,
    totalBalance: BigDecimal,
    currency: String,
    currentMonthIncome: BigDecimal,
    currentMonthExpenses: BigDecimal,
    currentMonthLent: BigDecimal,
    currentMonthTotal: BigDecimal,
    spendingHistory: List<BigDecimal>,
    lastMonthSpendingHistory: List<BigDecimal>,
    lastMonthSpending: BigDecimal,
    isApproximate: Boolean,
    isBalanceHidden: Boolean,
    hasAccounts: Boolean,
    onOpenNetWorth: (() -> Unit)?,
    onShowBreakdown: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = stringResource(
                R.string.balance_card_last_month_amount,
                if (isBalanceHidden) "••••" else CurrencyFormatter.formatCurrency(lastMonthSpending, currency)
            ),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Spending sparkline
        if (spendingHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.md))
            val expenseColor = MaterialTheme.colorScheme.expense.markOn(fill)
            val comparisonColor = MaterialTheme.colorScheme.outline
            BalanceSparkline(
                data = spendingHistory,
                lineColor = expenseColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SPARKLINE_HEIGHT),
                currency = currency,
                isBalanceHidden = isBalanceHidden,
                comparisonData = lastMonthSpendingHistory.ifEmpty { null },
                comparisonLineColor = comparisonColor
            )
            if (lastMonthSpendingHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(Spacing.sm)
                            .background(expenseColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.balance_card_this_month),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Box(
                        modifier = Modifier
                            .width(Spacing.smd)
                            .height(Spacing.xxs)
                            .background(comparisonColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.balance_card_last_month),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        // The month's figures, with how they're worked out a tap away on the info button.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.balance_card_this_month),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShowBreakdown, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.home_breakdown_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.Icon.inline)
                )
            }
        }
        // Income | Expenses | Lent | Saved, with coloured accent bars. FlowRow so long
        // amounts (a 3-letter "MZN" prefix on big numbers) wrap onto a second line
        // instead of crushing the last column into one character per line.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            val incomeColor = MaterialTheme.colorScheme.income.markOn(fill)
            val expenseColor = MaterialTheme.colorScheme.expense.markOn(fill)
            val netColor = if (currentMonthTotal >= BigDecimal.ZERO) incomeColor else expenseColor
            SummaryItem(
                label = stringResource(R.string.balance_card_summary_income),
                value = if (isBalanceHidden) "••••" else CurrencyFormatter.formatCurrency(currentMonthIncome, currency),
                accentColor = incomeColor
            )
            SummaryItem(
                label = stringResource(R.string.balance_card_summary_expenses),
                value = if (isBalanceHidden) "••••" else CurrencyFormatter.formatCurrency(currentMonthExpenses, currency),
                // The bar matches the sparkline's expense series.
                accentColor = expenseColor,
            )
            if (currentMonthLent > BigDecimal.ZERO) {
                SummaryItem(
                    label = stringResource(R.string.balance_card_summary_lent),
                    value = if (isBalanceHidden) "••••" else CurrencyFormatter.formatCurrency(currentMonthLent, currency),
                    accentColor = MaterialTheme.colorScheme.loan.markOn(fill)
                )
            }
            SummaryItem(
                label = stringResource(R.string.balance_card_summary_saved),
                value = if (isBalanceHidden) "••••" else CurrencyFormatter.formatCurrency(currentMonthTotal, currency),
                accentColor = netColor
            )
        }

        // The accounts' total; each account is a tap away under Accounts, not repeated here.
        if (hasAccounts) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.balance_card_total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isBalanceHidden) {
                        MASK
                    } else {
                        "${CurrencyFormatter.formatCurrency(totalBalance, currency)}${if (isApproximate) "*" else ""}"
                    },
                    style = MaterialTheme.typography.titleSmall.money(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (isApproximate) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.balance_card_other_currency_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // Where to go from here, in one row: the budget or the month's transactions, and Net worth (named for where it goes: it takes off cards and
        // loans, so its figure isn't the total above).
        Spacer(modifier = Modifier.height(Spacing.sm))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
        ) {
            HeroLink(actionLabel, onAction)
            if (onOpenNetWorth != null) HeroLink(stringResource(R.string.net_worth_title), onOpenNetWorth)
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
    }
}

@Composable
private fun HeroLink(label: String, onClick: () -> Unit) {
    // The hero's primary is nudged onto the card (onColoredFill), under its own ripple too.
    TextButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
        Text(label)
    }
}

/** A series colour as a mark on the hero's [fill]: its hue, nudged to 3:1 there and under the card's press layer. */
@Composable
private fun Color.markOn(fill: Color) =
    readableOn(listOf(fill), GRAPHIC_CONTRAST, listOf(MaterialTheme.colorScheme.onSurface.stateLayer()))

/** A semantic colour as text on the hero's [fill], at rest and under the card's press layer. */
@Composable
private fun Color.textOn(fill: Color) =
    readableOn(listOf(fill), layers = listOf(MaterialTheme.colorScheme.onSurface.stateLayer()))

/** Which way spending moved on last month, in its semantic colour (expense up, income down). */
private class ChangeTrend(val color: Color, val up: Boolean)

/** The change's direction as an arrow: a mark, so it keeps more of [ChangeTrend.color]'s hue than text can. */
@Composable
private fun ChangeArrow(trend: ChangeTrend, fill: Color) {
    Icon(
        imageVector = if (trend.up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
        // The sentence beside it says which way.
        contentDescription = null,
        tint = trend.color.markOn(fill),
        modifier = Modifier.size(Dimensions.Icon.small)
    )
}

/** [content] on the hero's surface wash (a chip, Safe to spend), in [outer]'s own inks. */
@Composable
private fun OnWash(outer: ColorScheme, fill: Color, content: @Composable () -> Unit) {
    val cardInk = MaterialTheme.colorScheme.onSurface
    val scheme = remember(outer, fill, cardInk) { outer.onSurfaceWash(fill, cardInk) }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * "31 Oct" — day and short month, in the user's locale. A getter, not a cached val: a
 * formatter built once keeps the locale the process started in, so after switching the
 * app language the month stayed in the old one.
 */
internal val SHORT_DATE: DateTimeFormatter get() = AppDates.dayMonthFormatter()

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    accentColor: Color,
    // The figure sits on the hero's primaryContainer, so it takes that fill's ink; the bar keeps the series colour.
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row {
        // Coloured left-border accent bar
        Box(
            modifier = Modifier
                .width(Spacing.xs)
                .height(Dimensions.Component.iconButton)
                .background(
                    color = accentColor,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.money(),
                color = valueColor,
                fontWeight = FontWeight.Bold,
                // Keep the amount on one line — never break a "MZN52,245.26" into
                // one-character-per-line text when the column gets narrow.
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private const val MASK = "••••••"

/** The meter turns warning here, and error at [DANGER_AT] (fractions of the limit used). */
private const val WARNING_AT = 0.7f
private const val DANGER_AT = 0.9f

/** Height of the inline spending sparkline inside the expanded card. */
private val SPARKLINE_HEIGHT = Spacing.xxl + Spacing.xl
