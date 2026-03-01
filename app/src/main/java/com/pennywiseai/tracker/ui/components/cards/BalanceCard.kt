package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.pennywiseai.tracker.ui.theme.Dimensions
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.income_dark
import com.pennywiseai.tracker.ui.theme.income_light
import com.pennywiseai.tracker.ui.theme.expense_dark
import com.pennywiseai.tracker.ui.theme.expense_light
import com.pennywiseai.tracker.ui.components.AnimatedCurrencyText
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun BalanceCard(
    userName: String = "User",
    totalBalance: BigDecimal,
    monthlyChange: BigDecimal,
    monthlyChangePercent: Int,
    currency: String,
    currentMonthIncome: BigDecimal,
    currentMonthExpenses: BigDecimal,
    currentMonthTotal: BigDecimal,
    balanceHistory: List<BigDecimal>,
    availableCurrencies: List<String>,
    onCurrencyClick: () -> Unit,
    onShowBreakdown: () -> Unit,
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(Dimensions.Animation.medium),
        label = "chevronRotation"
    )

    val isDark = isSystemInDarkTheme()
    val isPositive = monthlyChange >= BigDecimal.ZERO
    val changeColor = if (isPositive) {
        if (isDark) income_dark else income_light
    } else {
        if (isDark) expense_dark else expense_light
    }

    val now = LocalDate.now()
    val currentMonth = now.month.name.lowercase().replaceFirstChar { it.uppercase() }
    val currencySymbol = CurrencyFormatter.getCurrencySymbol(currency)

    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    val changeSign = if (isPositive) "+" else ""
    val changeText = "$changeSign${CurrencyFormatter.formatCurrency(monthlyChange, currency)} ($monthlyChangePercent%)"

    Box(modifier = modifier.fillMaxWidth()) {
        PennyWiseCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(Dimensions.Animation.medium)
                )
                .then(
                    if (blurEffects) Modifier
                        .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                        .hazeEffect(
                            state = hazeState,
                            block = fun HazeEffectScope.() {
                                style = HazeDefaults.style(
                                    backgroundColor = Color.Transparent,
                                    tint = HazeDefaults.tint(containerColor),
                                    blurRadius = 20.dp,
                                    noiseFactor = -1f,
                                )
                                blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                            }
                        )
                    else Modifier
                ),
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                isExpanded = !isExpanded
            },
            colors = CardDefaults.cardColors(
                containerColor = if (blurEffects) containerColor.copy(alpha = 0.5f) else containerColor.copy(alpha = 0.92f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isExpanded) {
                    // ── Collapsed View ── Balance is the hero, no sparkline
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.xs)
                    ) {
                        AnimatedCurrencyText(
                            text = CurrencyFormatter.formatCurrency(totalBalance, currency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        // Currency chip + monthly change pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // Currency chip
                            if (availableCurrencies.size > 1) {
                                Surface(
                                    onClick = onCurrencyClick,
                                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                                    border = BorderStroke(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = Spacing.sm,
                                            vertical = Spacing.xs
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = currency,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Change currency",
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            // Monthly change pill
                            Surface(
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = changeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = changeColor,
                                    modifier = Modifier.padding(
                                        horizontal = Spacing.sm,
                                        vertical = Spacing.xs
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // ── Expanded View ──
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Balance at top as hero
                        AnimatedCurrencyText(
                            text = CurrencyFormatter.formatCurrency(totalBalance, currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Date range label
                        Text(
                            text = "$currentMonth 1\u2013${now.dayOfMonth}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alpha(0.4f),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // Currency selector
                        if (availableCurrencies.size > 1) {
                            Surface(
                                onClick = onCurrencyClick,
                                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                                border = BorderStroke(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
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
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Change currency",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(Spacing.sm))
                        }

                        // Sparkline (full width, 100dp)
                        if (balanceHistory.size >= 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                            ) {
                                BalanceSparkline(
                                    data = balanceHistory,
                                    lineColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))

                        // Summary row: Income | Expenses | Net — with colored accent bars
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val incomeColor = if (isDark) income_dark else income_light
                            val expenseColor = if (isDark) expense_dark else expense_light
                            val netColor = if (currentMonthTotal >= BigDecimal.ZERO) {
                                if (isDark) income_dark else income_light
                            } else {
                                if (isDark) expense_dark else expense_light
                            }

                            SummaryItem(
                                label = "Income",
                                value = CurrencyFormatter.formatCurrency(currentMonthIncome, currency),
                                accentColor = incomeColor
                            )
                            SummaryItem(
                                label = "Expenses",
                                value = CurrencyFormatter.formatCurrency(currentMonthExpenses, currency),
                                accentColor = expenseColor
                            )
                            SummaryItem(
                                label = "Net",
                                value = CurrencyFormatter.formatCurrency(currentMonthTotal, currency),
                                accentColor = netColor
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))
                    }
                }

                // Chevron indicator
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    accentColor: Color
) {
    Row {
        // Colored left-border accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .background(
                    color = accentColor,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.small)
                )
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
