package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.presentation.common.shortLabel
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.text.rememberSyncedTextFieldState
import com.pennywiseai.tracker.ui.theme.*

/** The type filters offered as one-tap chips under the search field. */
private val QUICK_TYPE_FILTERS = listOf(
    TransactionTypeFilter.ALL,
    TransactionTypeFilter.SPENDING,
    TransactionTypeFilter.INCOME,
    TransactionTypeFilter.TRANSFER
)

/**
 * The Activity search field: a tall pill with the search glyph, a clear button
 * while there's a query, and a tune button that opens the filters sheet. A count
 * on the tune button shows how many filters are in force.
 */
@Composable
internal fun ActivitySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    categoryFilter: String?,
    focusRequester: FocusRequester,
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filtersLabel = if (activeFilterCount > 0) {
        pluralStringResource(R.plurals.txn_list_filters_active, activeFilterCount, activeFilterCount)
    } else {
        stringResource(R.string.txn_list_filters)
    }
    Surface(
        modifier = modifier.height(Dimensions.Component.listItemMinHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Spacing.md, end = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.txn_list_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
            BasicTextField(
                state = rememberSyncedTextFieldState(query, onQueryChange),
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorator = TextFieldDecorator { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = if (categoryFilter != null) {
                                    stringResource(R.string.txn_list_search_in_category, categoryLabel(categoryFilter))
                                } else {
                                    stringResource(R.string.txn_list_search_hint)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.txn_list_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onOpenFilters, shapes = IconButtonDefaults.shapes()) {
                BadgedBox(badge = { if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) } }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = filtersLabel,
                        tint = if (activeFilterCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/** One filter in force, as [ActiveFiltersRow] shows it; [onRemove] puts just it back to its default. */
internal class ActiveFilter(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val onRemove: () -> Unit
)

/**
 * The filters in force as one row of removable chips, Clear all first so it's
 * reachable however many there are.
 */
@Composable
internal fun ActiveFiltersRow(
    filters: List<ActiveFilter>,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "clearAll") { ClearFiltersChip(onClick = onClearAll, label = stringResource(R.string.txn_list_clear_filters)) }
        items(filters.size, key = { filters[it].key }) { index ->
            val filter = filters[index]
            InputChip(
                selected = true,
                onClick = filter.onRemove,
                label = { Text(filter.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(filter.icon, contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize))
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.txn_list_remove_filter, filter.label),
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                },
                shape = MaterialTheme.shapes.medium,
                colors = InputChipDefaults.inputChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun ClearFiltersChip(onClick: () -> Unit, label: String) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.ClearAll,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        },
        shape = CircleShape
    )
}

/**
 * The period first, then All · Spending · Income · Transfers and (with several
 * currencies) the currency. A type picked from the advanced filters that isn't
 * one of the four (Expense, Credit, Investment) shows as its own selected chip
 * so the list never looks unfiltered while it is.
 */
@Composable
internal fun ActivityQuickFilters(
    transactionTypeFilter: TransactionTypeFilter,
    onTransactionTypeSelected: (TransactionTypeFilter) -> Unit,
    periodChipLabel: String,
    selectedPeriod: TimePeriod,
    timePeriods: List<TimePeriod>,
    showPeriodMenu: Boolean,
    onPeriodClick: () -> Unit,
    onPeriodDismiss: () -> Unit,
    onPeriodSelected: (TimePeriod) -> Unit,
    currencyPicker: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Box {
                ExpressiveFilterChip(
                    selected = selectedPeriod != TimePeriod.THIS_MONTH,
                    text = periodChipLabel,
                    icon = Icons.Default.CalendarMonth,
                    onClick = onPeriodClick
                )
                DropdownMenu(
                    expanded = showPeriodMenu,
                    onDismissRequest = onPeriodDismiss,
                    shape = MaterialTheme.shapes.large
                ) {
                    timePeriods.forEachIndexed { index, period ->
                        SelectableDropdownMenuItem(
                            selected = selectedPeriod == period,
                            text = { Text(period.label) },
                            shapes = MenuDefaults.itemShape(index, timePeriods.size),
                            colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                            selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { onPeriodSelected(period) }
                        )
                    }
                }
            }
        }
        items(QUICK_TYPE_FILTERS.size) { index ->
            val filter = QUICK_TYPE_FILTERS[index]
            QuickChip(
                selected = transactionTypeFilter == filter,
                text = if (filter == TransactionTypeFilter.TRANSFER) {
                    stringResource(R.string.txn_list_chip_transfers)
                } else {
                    filter.shortLabel()
                },
                onClick = { onTransactionTypeSelected(filter) }
            )
        }
        if (transactionTypeFilter !in QUICK_TYPE_FILTERS) {
            item {
                QuickChip(
                    selected = true,
                    text = transactionTypeFilter.shortLabel(),
                    onClick = { onTransactionTypeSelected(TransactionTypeFilter.ALL) }
                )
            }
        }
        if (currencyPicker != null) {
            item { currencyPicker() }
        }
    }
}

/** One of the type chips: outlined at rest, tonal with a check when picked. */
@Composable
private fun QuickChip(selected: Boolean, text: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shapes = FilterChipDefaults.shapes(shape = MaterialTheme.shapes.medium),
        label = { Text(text = text, maxLines = 1) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected)
    )
}

/** "All categories", one name, "All except X, Y", or "N categories" (#786). */
@Composable
internal fun categoryFilterLabel(
    single: String?,
    selected: List<String>?,
    available: List<String>
): String? {
    if (single != null) return categoryLabel(single)
    if (selected == null) return null
    val excluded = available - selected.toSet()
    return when {
        selected.size == 1 -> categoryLabel(selected.first())
        excluded.size in 1..2 && selected.size >= 2 -> stringResource(R.string.txn_list_categories_all_except, excluded.map { categoryLabel(it) }.joinToString(", "))
        else -> pluralStringResource(R.plurals.txn_list_categories_count, selected.size, selected.size)
    }
}
