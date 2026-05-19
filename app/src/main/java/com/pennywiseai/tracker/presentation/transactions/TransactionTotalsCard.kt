package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(Spacing.lg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm)
            ) {
                // Totals Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                    contentDescription = "Income",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (!isSystemInDarkTheme()) income_light else income_dark
                                )
                            },
                            label = "Income",
                            amount = CurrencyFormatter.formatCurrency(income, currency),
                            color = if (!isSystemInDarkTheme()) income_light else income_dark,
                            modifier = Modifier.alpha(incomeAlpha)
                        )
                    }

                    // Vertical Divider
                    VerticalDivider(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 0.5.dp),
                        color = Color.Transparent
                    )

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
                                    contentDescription = "Expenses",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (!isSystemInDarkTheme()) expense_light else expense_dark
                                )
                            },
                            label = "Expenses",
                            amount = CurrencyFormatter.formatCurrency(expenses, currency),
                            color = if (!isSystemInDarkTheme()) expense_light else expense_dark,
                            modifier = Modifier.alpha(expenseAlpha)
                        )
                    }

                    // Vertical Divider
                    VerticalDivider(
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 0.5.dp),
                        color = Color.Transparent
                    )

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
                                    contentDescription = "Net",
                                    modifier = Modifier.size(20.dp),
                                    tint = netColor
                                )
                            },
                            label = "Net",
                            amount = "$netPrefix${CurrencyFormatter.formatCurrency(netBalance, currency)}",
                            color = netColor,
                            modifier = Modifier.alpha(netAlpha)
                        )
                    }
                }
            }
        }

        if (availableCurrencies.size > 1 && !isUnifiedMode) {
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
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
            shape = RoundedCornerShape(Spacing.lg),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
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
                    contentDescription = "Select currency",
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
