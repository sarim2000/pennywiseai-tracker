package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
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

    Box(modifier = modifier.fillMaxWidth()) {
        PennyWiseCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
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
            onClick = { isExpanded = !isExpanded },
            colors = CardDefaults.cardColors(
                containerColor = if (blurEffects) containerColor.copy(alpha = 0.5f) else containerColor.copy(alpha = 0.92f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isExpanded) {
                    // ── Collapsed View ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // Monthly change indicator
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPositive) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = changeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "${CurrencyFormatter.formatCurrency(monthlyChange.abs(), currency)} this month",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.alpha(0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Right side: balance + sparkline
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = CurrencyFormatter.formatCurrency(totalBalance, currency),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (balanceHistory.size >= 2) {
                                Box(
                                    modifier = Modifier
                                        .width(80.dp)
                                        .height(30.dp)
                                ) {
                                    BalanceSparkline(
                                        data = balanceHistory,
                                        lineColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ── Expanded View ──
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$userName \u2022 Net Worth ($currencySymbol)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.alpha(0.5f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$currentMonth 1-${now.dayOfMonth}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.alpha(0.4f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = CurrencyFormatter.formatCurrency(totalBalance, currency),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                // Currency selector chip
                                if (availableCurrencies.size > 1) {
                                    Spacer(modifier = Modifier.height(Spacing.xs))
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
                                                horizontal = 8.dp,
                                                vertical = 4.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = currency,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // Sparkline (larger in expanded view)
                        if (balanceHistory.size >= 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
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

                        // Summary row: Income | Expenses | Net
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SummaryItem(
                                label = "INCOME",
                                value = CurrencyFormatter.formatCurrency(currentMonthIncome, currency),
                                color = if (isDark) income_dark else income_light
                            )
                            VerticalDivider(
                                modifier = Modifier.height(30.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            SummaryItem(
                                label = "EXPENSES",
                                value = CurrencyFormatter.formatCurrency(currentMonthExpenses, currency),
                                color = if (isDark) expense_dark else expense_light
                            )
                            VerticalDivider(
                                modifier = Modifier.height(30.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            SummaryItem(
                                label = "NET",
                                value = CurrencyFormatter.formatCurrency(currentMonthTotal, currency),
                                color = if (currentMonthTotal >= BigDecimal.ZERO) {
                                    if (isDark) income_dark else income_light
                                } else {
                                    if (isDark) expense_dark else expense_light
                                }
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
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

