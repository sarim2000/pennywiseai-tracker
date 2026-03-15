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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.PennyWiseCard
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

    PennyWiseCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Currency Selector (if multiple currencies available)
            if (availableCurrencies.size > 1 && !isUnifiedMode) {
                CurrencySelector(
                    selectedCurrency = currency,
                    availableCurrencies = availableCurrencies,
                    onCurrencySelected = onCurrencySelected,
                    modifier = Modifier
                        .padding(horizontal = Spacing.xs)
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
            }

            // Totals Row with individual backgrounds
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // Income Column
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(
                                topStart = Spacing.md,
                                bottomStart = Spacing.md,
                                topEnd = Spacing.xs,
                                bottomEnd = Spacing.xs
                            )
                        )
                        .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
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

                // Expenses Column
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(Spacing.xs)
                        )
                        .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
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
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(
                                topStart = Spacing.xs,
                                bottomStart = Spacing.xs,
                                topEnd = Spacing.md,
                                bottomEnd = Spacing.md
                            )
                        )
                        .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
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
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySelector(
    selectedCurrency: String,
    availableCurrencies: List<String>,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(Spacing.sm),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .clickable { expanded = !expanded }
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
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableCurrencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency) },
                    onClick = {
                        onCurrencySelected(currency)
                        expanded = false
                    }
                )
            }
        }
    }
}
