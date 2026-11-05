package com.pennywiseai.tracker.ui.components.cards

import com.pennywiseai.tracker.ui.theme.pennyWiseButtonShapes
import com.pennywiseai.tracker.utils.AppDates

import androidx.compose.ui.res.pluralStringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.pennywiseai.tracker.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.income_dark
import com.pennywiseai.tracker.ui.theme.income_light
import com.pennywiseai.tracker.ui.theme.expense_dark
import com.pennywiseai.tracker.ui.theme.expense_light
import com.pennywiseai.tracker.ui.components.AnimatedCurrencyText
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatBalance
import com.pennywiseai.tracker.utils.sumByCurrency
import com.pennywiseai.tracker.domain.spending.SpendForecast
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BalanceCard(
    userName: String = "User",
    totalBalance: BigDecimal,
    monthlyChange: BigDecimal,
    /** Null hides the change chip (no last-month spending to compare with). */
    monthlyChangePercent: Int?,
    currency: String,
    currentMonthIncome: BigDecimal,
    currentMonthExpenses: BigDecimal,
    currentMonthLent: BigDecimal = BigDecimal.ZERO,
    currentMonthTotal: BigDecimal,
    balanceHistory: List<BigDecimal>,
    spendingHistory: List<BigDecimal> = emptyList(),
    lastMonthSpendingHistory: List<BigDecimal> = emptyList(),
    lastMonthSpending: BigDecimal = BigDecimal.ZERO,
    availableCurrencies: List<String>,
    isUnifiedMode: Boolean = false,
    isApproximate: Boolean = false,
    onCurrencySelected: (String) -> Unit,
    onShowBreakdown: () -> Unit,
    isBalanceHidden: Boolean = false,
    onToggleBalanceVisibility: () -> Unit = {},
    accountBalances: List<AccountBalanceEntity> = emptyList(),
    creditCards: List<AccountBalanceEntity> = emptyList(),
    totalAvailableCredit: BigDecimal = BigDecimal.ZERO,
    forecast: SpendForecast? = null,
    onAccountClick: (String, String) -> Unit = { _, _ -> },
    /** False when the Bank Accounts section below already lists them. */
    showAccountList: Boolean = true,
    /** First day of the current spending period, for the chart's date axis. */
    periodStart: java.time.LocalDate? = null,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chevronRotation"
    )

    val isDark = isAppInDarkTheme()
    val isPositive = monthlyChange >= BigDecimal.ZERO
    // A comparison, not an alarm: red is kept for over budget and overdue. The words say
    // which way it went.
    val changeColor = MaterialTheme.colorScheme.onSurfaceVariant

    val changeText = monthlyChangePercent?.let { percent ->
        when {
            isBalanceHidden -> "••••"
            // Shows the figure it compares with: this is last month to the same day, a
            // different number from the full month above it.
            else -> when {
                percent == 0 -> stringResource(R.string.balance_card_change_same_point)
                isPositive -> stringResource(R.string.balance_card_change_more_same_point, kotlin.math.abs(percent))
                else -> stringResource(R.string.balance_card_change_less_same_point, kotlin.math.abs(percent))
            } + lastMonthSpendingHistory.lastOrNull()
                ?.let { " (${CurrencyFormatter.formatCurrency(it, currency)})" }.orEmpty()
        }
    }

    // Straight on the page: the figure is the hero, and a box around it only competes.
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
                )
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = stringResource(if (isExpanded) R.string.card_collapse else R.string.card_expand)) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    isExpanded = !isExpanded
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isExpanded) {
                    // ── Collapsed View ── Spending is the hero, no sparkline
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.xs)
                    ) {
                        SpendingAmountHeader(
                            amountText = if (isBalanceHidden) "••••••" else CurrencyFormatter.formatCurrency(currentMonthExpenses, currency),
                            amountStyle = PennyWiseText.heroAmount,
                            isBalanceHidden = isBalanceHidden,
                            onToggleBalanceVisibility = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onToggleBalanceVisibility()
                            },
                            currencyChip = {
                                SpendingCurrencyChip(
                                    currency = currency,
                                    show = availableCurrencies.size > 1 && !isUnifiedMode,
                                    currencies = availableCurrencies,
                                    onCurrencySelected = onCurrencySelected,
                                )
                            }
                        )

                        // The pace line: this month against the same days last month. It answers
                        // "am I on track?" at a glance, which a sentence can only describe.
                        // Not while hidden: its shape gives the amounts away.
                        if (spendingHistory.size >= 2 && !isBalanceHidden) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            BalanceSparkline(
                                data = spendingHistory,
                                lineColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(PACE_LINE_HEIGHT),
                                startDate = periodStart,
                                comparisonData = lastMonthSpendingHistory.ifEmpty { null },
                                comparisonLineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                comparisonLabel = stringResource(R.string.balance_card_last_month)
                            )
                        }

                        if (changeText != null) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = changeText,
                                style = PennyWiseText.metadata,
                                color = changeColor
                            )
                        }
                        forecast?.let {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            ForecastRow(it, isBalanceHidden)
                        }

                        // The first line of the statement under the figure.
                        if (accountBalances.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.smd))
                            StatementLine(
                                label = stringResource(R.string.balance_card_balance_label),
                                value = if (isBalanceHidden) "••••••" else CurrencyFormatter.formatCurrency(totalBalance, currency),
                                detail = balanceDetail(currency, accountBalances, isBalanceHidden),
                                trailing = {
                                    Icon(
                                        imageVector = Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(Dimensions.Icon.inline)
                                            .rotate(chevronRotation)
                                    )
                                }
                            )
                        }
                    }
                } else {
                    // ── Expanded View ──
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SpendingAmountHeader(
                            amountText = if (isBalanceHidden) "••••••" else CurrencyFormatter.formatCurrency(currentMonthExpenses, currency),
                            amountStyle = PennyWiseText.heroAmount,
                            isBalanceHidden = isBalanceHidden,
                            onToggleBalanceVisibility = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onToggleBalanceVisibility()
                            },
                            supportingText = stringResource(R.string.balance_card_last_month_amount, if (isBalanceHidden) "••••" else CurrencyFormatter.formatCurrency(lastMonthSpending, currency)),
                            currencyChip = {
                                SpendingCurrencyChip(
                                    currency = currency,
                                    show = availableCurrencies.size > 1 && !isUnifiedMode,
                                    currencies = availableCurrencies,
                                    onCurrencySelected = onCurrencySelected,
                                )
                            }
                        )

                        if (changeText != null) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Text(text = changeText, style = PennyWiseText.metadata, color = changeColor)
                        }
                        forecast?.let {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            ForecastRow(it, isBalanceHidden)
                        }
                        // Spending sparkline. Not while hidden: its shape gives the amounts away.
                        if (spendingHistory.isNotEmpty() && !isBalanceHidden) {
                            Spacer(modifier = Modifier.height(Spacing.md))
                            val lineColor = MaterialTheme.colorScheme.primary
                            BalanceSparkline(
                                data = spendingHistory,
                                lineColor = lineColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(SPARKLINE_HEIGHT),
                                startDate = periodStart,
                                scaleLabel = (spendingHistory + lastMonthSpendingHistory).maxOrNull()
                                    ?.let { CurrencyFormatter.formatEstimate(it, currency) },
                                comparisonData = lastMonthSpendingHistory.ifEmpty { null },
                                comparisonLineColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Legend
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
                                            .background(lineColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = stringResource(R.string.balance_card_this_month),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.md))
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                        repeat(2) {
                                            Box(
                                                modifier = Modifier
                                                    .width(Spacing.xs)
                                                    .height(Spacing.xxs)
                                                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }
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
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))

                        // "This month" section label
                        Text(
                            text = stringResource(R.string.balance_card_this_month),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // Summary row: Income | Expenses | Lent | Saved. FlowRow so long amounts (e.g. a 3-char "MZN"
                        // prefix + big numbers across all four items) wrap onto a second
                        // line instead of crushing the last column into vertical,
                        // one-character-per-line text.
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            val incomeColor = if (isDark) income_dark else income_light
                            val expenseColor = if (isDark) expense_dark else expense_light
                            val netColor = if (currentMonthTotal >= BigDecimal.ZERO) {
                                if (isDark) income_dark else income_light
                            } else {
                                if (isDark) expense_dark else expense_light
                            }

                            SummaryItem(
                                label = stringResource(R.string.balance_card_summary_income),
                                value = if (isBalanceHidden) "••••" else CurrencyFormatter.formatCurrency(currentMonthIncome, currency),
                                accentColor = if (isBalanceHidden || currentMonthIncome.signum() == 0) MaterialTheme.colorScheme.onSurface else incomeColor
                            )
                            if (currentMonthLent > BigDecimal.ZERO) {
                                SummaryItem(
                                    label = stringResource(R.string.balance_card_summary_lent),
                                    value = if (isBalanceHidden) "••••" else CurrencyFormatter.formatCurrency(currentMonthLent, currency),
                                    accentColor = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            // Income minus expenses. "Saved"/"Overspent" read as a verdict, and
                            // five days into a month with no income yet that's a false alarm.
                            SummaryItem(
                                label = stringResource(R.string.balance_card_summary_net),
                                value = if (isBalanceHidden) "••••" else CurrencyFormatter.formatSigned(currentMonthTotal, currency),
                                accentColor = if (isBalanceHidden) MaterialTheme.colorScheme.onSurface else netColor
                            )
                        }

                        // Accounts section (only if accounts exist)
                        if (showAccountList && accountBalances.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))

                            Text(
                                text = stringResource(R.string.balance_card_accounts),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))

                            accountBalances.forEach { account ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget)
                                        .clickable { onAccountClick(account.bankName, account.accountLast4) }
                                        .padding(vertical = Spacing.xs),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        // Mobile-money wallets have no account number — show
                                        // just the service name (no "•• 1234" suffix).
                                        text = if (account.accountLast4 == AccountBalanceEntity.WALLET_ACCOUNT_MARKER) {
                                            account.bankName
                                        } else {
                                            "${account.bankName} •• ${account.accountLast4}"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false).padding(end = Spacing.md)
                                    )
                                    Text(
                                        // Use the account's own (pre-converted) currency
                                        // so an un-convertible balance shows honestly as
                                        // "$400" rather than a raw amount mislabelled with
                                        // the display currency ("MZN400").
                                        text = if (isBalanceHidden) "••••••" else CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                        style = PennyWiseText.amountRow,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Credit Cards sub-section
                            if (creditCards.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = stringResource(R.string.balance_card_credit_cards),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))

                                creditCards.forEach { card ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget)
                                            .clickable { onAccountClick(card.bankName, card.accountLast4) }
                                            .padding(vertical = Spacing.xs),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${card.bankName} •• ${card.accountLast4}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f, fill = false).padding(end = Spacing.md)
                                        )
                                        Text(
                                            // Show the amount spent (outstanding) on the card, not
                                            // the remaining credit limit — for a credit card
                                            // `balance` is the outstanding debt. Format in the
                                            // card's own currency (it may be unconverted). (#684)
                                            text = if (isBalanceHidden) "••••••" else CurrencyFormatter.formatCurrency(card.balance, card.currency),
                                            style = PennyWiseText.amountRow,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Total row
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
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
                                    text = balanceText(totalBalance, currency, accountBalances, isBalanceHidden),
                                    style = PennyWiseText.amountRow,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = Spacing.md)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))
                    }
                }

                // With no accounts there is no statement line to carry the chevron.
                if (isExpanded || accountBalances.isEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(Dimensions.Icon.inline)
                            .rotate(chevronRotation)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpendingAmountHeader(
    amountText: String,
    amountStyle: TextStyle,
    isBalanceHidden: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    supportingText: String? = null,
    currencyChip: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.balance_card_spent_this_month),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        currencyChip()
        IconButton(
            onClick = onToggleBalanceVisibility,
            modifier = Modifier.size(Dimensions.Component.iconButton),
            shapes = IconButtonDefaults.shapes()
        ) {
            Icon(
                imageVector = if (isBalanceHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (isBalanceHidden) stringResource(R.string.balance_show) else stringResource(R.string.balance_hide),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
    }
    AnimatedCurrencyText(
        text = amountText,
        style = amountStyle,
        color = MaterialTheme.colorScheme.onSurface
    )
    supportingText?.let { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * "JD1,585.930, plus $800": the balance in the chosen currency, then any accounts in other
 * currencies. They can't be added together, and a "*" with a footnote hid that the figure
 * left money out.
 */
@Composable
private fun balanceText(
    total: BigDecimal,
    currency: String,
    accounts: List<AccountBalanceEntity>,
    hidden: Boolean
): String {
    if (hidden) return "••••••"
    val others = accounts
        .filter { it.currency != currency }
        .sumByCurrency({ it.currency }, { it.balance })
    val main = CurrencyFormatter.formatCurrency(total, currency)
    return if (others.isEmpty()) main
    else stringResource(R.string.balance_card_plus_other_currencies, main, CurrencyFormatter.formatByCurrency(others))
}

/** "Plus $800 · 3 accounts": what the statement line's single figure leaves out. */
@Composable
private fun balanceDetail(currency: String, accounts: List<AccountBalanceEntity>, hidden: Boolean): String {
    val others = accounts
        .filter { it.currency != currency }
        .sumByCurrency({ it.currency }, { it.balance })
    val plus = if (others.isEmpty() || hidden) null
    else stringResource(R.string.balance_card_plus_other, CurrencyFormatter.formatByCurrency(others))
    val count = if (accounts.size > 1) pluralStringResource(R.plurals.balance_card_accounts_count, accounts.size, accounts.size) else null
    return listOfNotNull(plus, count).joinToString(" · ")
}

@Composable
private fun SpendingCurrencyChip(
    currency: String,
    show: Boolean,
    currencies: List<String>,
    onCurrencySelected: (String) -> Unit,
) {
    if (!show) return
    Box {
        var showMenu by remember { mutableStateOf(false) }
        CurrencyChip(currency = currency, onClick = { showMenu = true })
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
                    selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) }
                )
            }
        }
    }
}

/** "On track for ₹42,300 by 31 Oct", plus an "over usual" chip styled like the change chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForecastRow(forecast: SpendForecast, isBalanceHidden: Boolean) {
    val isDark = isAppInDarkTheme()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                R.string.forecast_on_track,
                if (isBalanceHidden) "••••" else CurrencyFormatter.formatEstimate(forecast.projected, forecast.currency),
                forecast.periodEnd.format(SHORT_DATE)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        forecast.overUsual?.let { over ->
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = stringResource(
                        R.string.forecast_over_usual,
                        if (isBalanceHidden) "••••" else CurrencyFormatter.formatCurrency(over, forecast.currency)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) expense_dark else expense_light,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                )
            }
        }
    }
}

/**
 * "31 Oct" — day and short month, in the user's locale. A getter, not a cached val: a
 * formatter built once keeps the locale the process started in, so after switching the
 * app language the month stayed in the old one.
 */
internal val SHORT_DATE: DateTimeFormatter get() = AppDates.dayMonthFormatter()

@Composable
private fun CurrencyChip(
    currency: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.sm,
                vertical = Spacing.xs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = currency,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = stringResource(R.string.balance_card_change_currency),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.small)
            )
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    accentColor: Color
) {
    Column {
        Text(
            text = value,
            style = PennyWiseText.amountMedium,
            color = accentColor,
            // Keep the amount on one line — never break a "MZN52,245.26" into
            // one-character-per-line text when the column gets narrow.
            maxLines = 1,
            autoSize = PennyWiseText.fit(PennyWiseText.amountMedium),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Height of the pace line under the hero figure. */
private val PACE_LINE_HEIGHT = Spacing.xxxl + Spacing.sm

/** Height of the inline spending sparkline inside the expanded balance card. */
private val SPARKLINE_HEIGHT = Spacing.xxxl + Spacing.xxl
