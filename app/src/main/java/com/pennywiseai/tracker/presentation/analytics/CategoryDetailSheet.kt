package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

/**
 * A tapped category on Insights: its total for the period, share, row count and
 * average, and its biggest merchants (each opens its rows). [onFocus], when set,
 * scopes the whole page to the category, so its hero history and trend become the
 * category's; [onSeeTransactions] opens the category's rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryDetailSheet(
    category: CategoryData,
    currency: String,
    onMerchantClick: (String) -> Unit,
    onSeeTransactions: () -> Unit,
    onFocus: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val merchantDisplay = LocalMerchantDisplay.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = Dimensions.Padding.dialog),
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
        ) {
            item(key = "header") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = Spacing.md)
                ) {
                    CategoryBadge(category.name, category.color)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            text = categoryLabel(category.name),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(category.amount, currency),
                            style = PennyWiseText.amountLarge
                        )
                    }
                }
            }
            item(key = "stats") {
                val count = category.transactionCount
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(bottom = Spacing.md)) {
                    StatTile(
                        label = stringResource(R.string.insights_category_share_label),
                        value = stringResource(R.string.insights_category_share_value, category.percentage.toInt()),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = stringResource(R.string.insights_category_count_label),
                        value = NumberFormat.getIntegerInstance().format(count),
                        modifier = Modifier.weight(1f)
                    )
                    if (count > 0) {
                        StatTile(
                            label = stringResource(R.string.insights_category_average_label),
                            value = CurrencyFormatter.formatCurrency(
                                category.amount.divide(BigDecimal(count), 2, RoundingMode.HALF_UP),
                                currency
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (category.topMerchants.isNotEmpty()) {
                item(key = "merchantsHeader") {
                    Text(
                        text = stringResource(R.string.insights_category_top_merchants),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(bottom = Spacing.xs)
                            .semantics { heading() }
                    )
                }
                itemsIndexed(category.topMerchants, key = { _, m -> "merchant-${m.name}" }) { index, merchant ->
                    GroupedRow(
                        position = ListItemPosition.from(index, category.topMerchants.size),
                        onClick = { onMerchantClick(merchant.name) },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
                    ) {
                        BrandIcon(merchantName = merchant.name, size = Dimensions.Icon.avatar, showBackground = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            Text(
                                text = merchantDisplay(merchant.name) ?: merchant.name,
                                style = PennyWiseText.rowTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.analytics_transaction_count,
                                    merchant.transactionCount,
                                    merchant.transactionCount
                                ),
                                style = PennyWiseText.rowSubtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = CurrencyFormatter.formatCurrency(merchant.amount, currency),
                            style = PennyWiseText.amountRow
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.dialog)
                .padding(top = Spacing.md, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Button(onClick = onSeeTransactions, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(
                    text = stringResource(R.string.insights_category_see_transactions),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                )
            }
            if (onFocus != null) {
                FilledTonalButton(onClick = onFocus, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FilterAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(
                        text = stringResource(R.string.insights_category_focus),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.smd, vertical = Spacing.smd),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = PennyWiseText.amountRow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
