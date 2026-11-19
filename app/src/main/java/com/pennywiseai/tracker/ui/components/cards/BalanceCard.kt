package com.pennywiseai.tracker.ui.components.cards

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.formatBalance
import com.pennywiseai.tracker.ui.components.AnimatedCurrencyText
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.expense
import com.pennywiseai.tracker.ui.theme.income
import com.pennywiseai.tracker.ui.theme.loan
import com.pennywiseai.tracker.ui.theme.money
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
 * Home's hero: a big tonal card. With an overall [budget] it leads with what's left to
 * spend, a meter and "spent / of" under it; without one, with this month's spending in
 * [currency]. Tapping it opens the month's detail (sparkline, income/expenses/saved,
 * accounts); the round button opens Budgets or the month's transactions ([onOpen]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BalanceCard(
    totalBalance: BigDecimal,
    monthlyChange: BigDecimal,
    /** Null hides the change chip (no last-month spending to compare with). */
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
    accountBalances: List<AccountBalanceEntity> = emptyList(),
    creditCards: List<AccountBalanceEntity> = emptyList(),
    /** Leads with what's left of this budget when set; this month's spending otherwise. */
    budget: HomeHeroBudget? = null,
    /** The last day of the spending cycle, for the "days left" chip without a budget. */
    cycleEnd: LocalDate? = null,
    /** The round arrow: Budgets with a [budget], the month's transactions without. */
    onOpen: () -> Unit = {},
    onAccountClick: (String, String) -> Unit = { _, _ -> },
    /** Opens Net worth from a labelled link in the expanded card; null hides the link. */
    onOpenNetWorth: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // The month's figure is Home's hero: a tonal primary card, its text and chips
    // retoned to sit on it.
    val outer = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = outer.heroTones()) {
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
                isBalanceHidden -> "••••"
                monthlyChange >= BigDecimal.ZERO ->
                    stringResource(R.string.balance_card_change_more_same_point, kotlin.math.abs(percent))
                else -> stringResource(R.string.balance_card_change_less_same_point, kotlin.math.abs(percent))
            }
        }
        // Inverted for spending: more spent = red, less = green.
        val changeColor = when {
            monthlyChangePercent == 0 -> MaterialTheme.colorScheme.onSurface
            monthlyChange >= BigDecimal.ZERO -> MaterialTheme.colorScheme.expense
            else -> MaterialTheme.colorScheme.income
        }
        val showCurrencyChip = availableCurrencies.size > 1 && !isUnifiedMode

        Surface(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                isExpanded = !isExpanded
            },
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = outer.primaryContainer,
            contentColor = outer.onPrimaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(start = Spacing.lg, top = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.smd)
            ) {
                HeroHeader(
                    label = heroLabel(budget),
                    isBalanceHidden = isBalanceHidden,
                    onToggleBalanceVisibility = toggleHidden,
                    openLabel = stringResource(
                        if (budget != null) R.string.home_hero_open_budgets else R.string.home_hero_open_activity
                    ),
                    onOpen = onOpen,
                )
                val heroStyle = MaterialTheme.typography.displayLargeEmphasized.money()
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
                if (budget != null) {
                    BudgetMeter(budget = budget, format = hidden)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (budget != null) {
                        HeroChip(Icons.Default.CalendarToday, daysLeftText(budget.daysLeft))
                        if (budget.remaining.signum() > 0) {
                            HeroChip(
                                Icons.Default.Payments,
                                stringResource(R.string.home_hero_per_day, hidden(budget.perDay, budget.currency))
                            )
                        }
                    } else {
                        if (showCurrencyChip) {
                            CurrencyChip(currency, availableCurrencies, onCurrencySelected)
                        }
                        cycleEnd?.let {
                            val today = LocalDate.now()
                            if (!it.isBefore(today)) {
                                HeroChip(
                                    Icons.Default.CalendarToday,
                                    daysLeftText(ChronoUnit.DAYS.between(today, it).toInt())
                                )
                            }
                        }
                        if (changeText != null) HeroChip(text = changeText, textColor = changeColor)
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = com.pennywiseai.tracker.ui.animations.pwExpand(),
                    exit = com.pennywiseai.tracker.ui.animations.pwCollapse(),
                ) {
                    Column {
                        if (budget != null) {
                            // The month's spending, which the budget hero doesn't lead with.
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Text(
                                text = stringResource(R.string.balance_card_spent_this_month),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AnimatedCurrencyText(
                                text = hidden(currentMonthExpenses, currency),
                                style = PennyWiseText.amountLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (showCurrencyChip || changeText != null) {
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    if (showCurrencyChip) CurrencyChip(currency, availableCurrencies, onCurrencySelected)
                                    if (changeText != null) HeroChip(text = changeText, textColor = changeColor)
                                }
                            }
                        }
                        MonthDetails(
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
                            accountBalances = accountBalances,
                            creditCards = creditCards,
                            onAccountClick = onAccountClick,
                            onOpenNetWorth = onOpenNetWorth,
                            onShowBreakdown = onShowBreakdown,
                        )
                    }
                }

                // Says the card opens; the whole card is the target.
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(if (isExpanded) R.string.card_collapse else R.string.card_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(Dimensions.Icon.inline)
                        .rotate(chevronRotation)
                )
            }
        }
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
    openLabel: String,
    onOpen: () -> Unit,
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
        IconButton(onClick = onToggleBalanceVisibility, shapes = IconButtonDefaults.shapes()) {
            Icon(
                imageVector = if (isBalanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = stringResource(if (isBalanceHidden) R.string.balance_show else R.string.balance_hide),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
        FilledIconButton(
            onClick = onOpen,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.size(Dimensions.Component.iconButton)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = openLabel,
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
    }
}

/** A flat data meter (the wave is for work in progress), then "spent / of total". */
@Composable
private fun BudgetMeter(budget: HomeHeroBudget, format: (BigDecimal, String) -> String) {
    val used = budget.fractionUsed
    val progress by animateFloatAsState(
        targetValue = used,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "heroBudgetProgress"
    )
    // Status follows the value on screen: warning from 70%, error from 90%.
    val color = when {
        progress >= DANGER_AT -> MaterialTheme.colorScheme.error
        progress >= WARNING_AT -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.primary
    }
    LinearProgressIndicator(
        progress = { progress },
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.Component.progressBarHeight)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(R.string.home_hero_spent, format(budget.spent, budget.currency)),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.home_hero_of_total, format(budget.limit, budget.currency)),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A pill on the hero: "11 days left", "~₹450 a day", the change on last month. */
@Composable
private fun HeroChip(
    icon: ImageVector? = null,
    text: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    trailingLabel: String? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .heightIn(min = Dimensions.Component.chipHeight)
                .padding(horizontal = Spacing.smd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1)
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = trailingLabel, modifier = Modifier.size(Dimensions.Icon.small))
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = content
        )
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = content
        )
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
            trailingIcon = Icons.Default.KeyboardArrowDown,
            trailingLabel = stringResource(R.string.balance_card_change_currency)
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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

/** The opened card: the month's sparkline, income / expenses / saved, and the accounts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDetails(
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
    accountBalances: List<AccountBalanceEntity>,
    creditCards: List<AccountBalanceEntity>,
    onAccountClick: (String, String) -> Unit,
    onOpenNetWorth: (() -> Unit)?,
    onShowBreakdown: () -> Unit,
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
            val expenseColor = MaterialTheme.colorScheme.expense
            BalanceSparkline(
                data = spendingHistory,
                lineColor = expenseColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SPARKLINE_HEIGHT),
                currency = currency,
                isBalanceHidden = isBalanceHidden,
                comparisonData = lastMonthSpendingHistory.ifEmpty { null },
                comparisonLineColor = MaterialTheme.colorScheme.outline
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
                            .background(MaterialTheme.colorScheme.outline, CircleShape)
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

        Spacer(modifier = Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(Spacing.md))

        DetailHeading(stringResource(R.string.balance_card_this_month))
        // Income | Expenses | Lent | Saved, with coloured accent bars. FlowRow so long
        // amounts (a 3-letter "MZN" prefix on big numbers) wrap onto a second line
        // instead of crushing the last column into one character per line.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            val incomeColor = MaterialTheme.colorScheme.income
            val expenseColor = MaterialTheme.colorScheme.expense
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
                    accentColor = MaterialTheme.colorScheme.loan
                )
            }
            SummaryItem(
                label = stringResource(R.string.balance_card_summary_saved),
                value = if (isBalanceHidden) "••••" else CurrencyFormatter.formatCurrency(currentMonthTotal, currency),
                accentColor = netColor
            )
        }
        TextButton(
            onClick = onShowBreakdown,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.home_breakdown_title), color = MaterialTheme.colorScheme.primary)
        }

        if (accountBalances.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(Spacing.md))
            DetailHeading(stringResource(R.string.balance_card_accounts))
            accountBalances.forEach { account ->
                AccountLine(
                    // Mobile-money wallets have no account number: just the service name.
                    name = if (account.accountLast4 == AccountBalanceEntity.WALLET_ACCOUNT_MARKER) {
                        account.bankName
                    } else {
                        "${account.bankName} •• ${account.accountLast4}"
                    },
                    // The account's own (pre-converted) currency, so an un-convertible
                    // balance shows honestly as "$400", not mislabelled "MZN400".
                    amount = if (isBalanceHidden) MASK else account.formatBalance(),
                    onClick = { onAccountClick(account.bankName, account.accountLast4) }
                )
            }

            if (creditCards.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                DetailHeading(stringResource(R.string.balance_card_credit_cards))
                creditCards.forEach { card ->
                    AccountLine(
                        name = "${card.bankName} •• ${card.accountLast4}",
                        // The amount owed on the card, in the card's own currency (#684).
                        amount = if (isBalanceHidden) MASK else CurrencyFormatter.formatCurrency(card.balance, card.currency),
                        onClick = { onAccountClick(card.bankName, card.accountLast4) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(Spacing.xs))
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
            // Named for where it goes: Net worth takes off cards and loans, so its
            // figure isn't this total.
            if (onOpenNetWorth != null) {
                val netWorthLabel = stringResource(R.string.net_worth_title)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
                        .clickable(onClickLabel = netWorthLabel, role = Role.Button, onClick = onOpenNetWorth)
                        .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = netWorthLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
    }
}

@Composable
private fun DetailHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(Spacing.sm))
}

@Composable
private fun AccountLine(name: String, amount: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.titleSmall.money(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
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
                    shape = RoundedCornerShape(Dimensions.CornerRadius.small)
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

/** The colour roles Home's hero card draws with, retoned to read on `primaryContainer`. */
private fun ColorScheme.heroTones() = copy(
    onSurface = onPrimaryContainer,
    onSurfaceVariant = onPrimaryContainer.copy(alpha = Dimensions.Alpha.subtitle),
    surfaceContainerHighest = surface.copy(alpha = Dimensions.Alpha.surface),
    outline = onPrimaryContainer.copy(alpha = Dimensions.Alpha.medium),
    outlineVariant = onPrimaryContainer.copy(alpha = Dimensions.Alpha.divider),
)
