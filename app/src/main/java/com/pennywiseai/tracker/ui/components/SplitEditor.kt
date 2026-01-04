package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

data class SplitItem(
    val id: Long = 0,
    val category: String,
    val amount: BigDecimal
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitEditor(
    totalAmount: BigDecimal,
    currency: String,
    splits: List<SplitItem>,
    availableCategories: List<String>,
    onSplitsChanged: (List<SplitItem>) -> Unit,
    onRemoveSplits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val splitsTotal = splits.sumOf { it.amount }
    val remaining = totalAmount - splitsTotal
    val isBalanced = remaining.abs() <= BigDecimal("0.01")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Split Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                TextButton(
                    onClick = onRemoveSplits,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove Splits")
                }
            }

            // Split rows
            splits.forEachIndexed { index, split ->
                SplitRow(
                    split = split,
                    availableCategories = availableCategories.filter { cat ->
                        cat == split.category || splits.none { it.category == cat }
                    },
                    onCategoryChanged = { newCategory ->
                        val newSplits = splits.toMutableList()
                        newSplits[index] = split.copy(category = newCategory)
                        onSplitsChanged(newSplits)
                    },
                    onAmountChanged = { newAmount ->
                        val newSplits = splits.toMutableList()
                        newSplits[index] = split.copy(amount = newAmount)
                        onSplitsChanged(newSplits)
                    },
                    onRemove = {
                        if (splits.size > 2) {
                            val newSplits = splits.toMutableList()
                            newSplits.removeAt(index)
                            onSplitsChanged(newSplits)
                        }
                    },
                    canRemove = splits.size > 2,
                    currency = currency
                )
            }

            // Add split button
            OutlinedButton(
                onClick = {
                    val usedCategories = splits.map { it.category }.toSet()
                    val nextCategory = availableCategories.firstOrNull { it !in usedCategories } ?: "Others"
                    val newSplit = SplitItem(
                        id = 0,
                        category = nextCategory,
                        amount = remaining.coerceAtLeast(BigDecimal.ZERO)
                    )
                    onSplitsChanged(splits + newSplit)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = availableCategories.size > splits.size
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text("Add Split")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))

            // Total validation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBalanced) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Balanced",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Not balanced",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Total:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(splitsTotal, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isBalanced) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    if (!isBalanced) {
                        Text(
                            text = if (remaining > BigDecimal.ZERO) {
                                "${CurrencyFormatter.formatCurrency(remaining, currency)} remaining"
                            } else {
                                "${CurrencyFormatter.formatCurrency(remaining.abs(), currency)} over"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitRow(
    split: SplitItem,
    availableCategories: List<String>,
    onCategoryChanged: (String) -> Unit,
    onAmountChanged: (BigDecimal) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    currency: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var amountText by remember(split.amount) {
        mutableStateOf(if (split.amount == BigDecimal.ZERO) "" else split.amount.stripTrailingZeros().toPlainString())
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = split.category,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableCategories.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = category,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onCategoryChanged(category)
                            expanded = false
                        }
                    )
                }
            }
        }

        // Amount field
        OutlinedTextField(
            value = amountText,
            onValueChange = { newValue ->
                // Allow only valid decimal input
                val filtered = newValue.filter { it.isDigit() || it == '.' }
                if (filtered.count { it == '.' } <= 1) {
                    amountText = filtered
                    val parsedAmount = filtered.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    onAmountChanged(parsedAmount)
                }
            },
            singleLine = true,
            modifier = Modifier.width(120.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            prefix = {
                Text(
                    text = CurrencyFormatter.getCurrencySymbol(currency),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Remove button
        IconButton(
            onClick = onRemove,
            enabled = canRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove split",
                tint = if (canRemove) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Card showing split breakdown in view mode (read-only).
 */
@Composable
fun SplitBreakdownCard(
    splits: List<SplitItem>,
    currency: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "Category Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            splits.forEach { split ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = split.category,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = CurrencyFormatter.formatCurrency(split.amount, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
