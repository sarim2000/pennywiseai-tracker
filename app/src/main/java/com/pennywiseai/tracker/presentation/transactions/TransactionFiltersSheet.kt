package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.AccountOption
import com.pennywiseai.tracker.presentation.common.filterIcon
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.displayName
import com.pennywiseai.tracker.ui.components.profileIcon
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/** The quick chips' types first, then the rest of the list's types. */
private val SHEET_TYPE_FILTERS = listOf(
    TransactionTypeFilter.ALL,
    TransactionTypeFilter.SPENDING,
    TransactionTypeFilter.INCOME,
    TransactionTypeFilter.TRANSFER,
    TransactionTypeFilter.EXPENSE,
    TransactionTypeFilter.CREDIT,
    TransactionTypeFilter.INVESTMENT
)

/**
 * Every filter past the period and the quick type chips, in one sheet opened from the
 * search field's tune button: sort, type, account, profile, tag, then categories (the
 * longest, last). Picks apply as they're made, so the list behind is always what the
 * sheet says; the button at the bottom just closes it and says how many rows match.
 *
 * [selectedCategories] empty means every category. Picking one narrows the list to it,
 * picking more adds them, and unpicking the last goes back to all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionFiltersSheet(
    resultCount: Int,
    hasAnyFilter: Boolean,
    sortOption: SortOption,
    onSortSelected: (SortOption) -> Unit,
    transactionTypeFilter: TransactionTypeFilter,
    onTypeSelected: (TransactionTypeFilter) -> Unit,
    accountOptions: List<AccountOption>,
    accountFilter: String?,
    onAccountSelected: (String?) -> Unit,
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    onProfileSelected: (Long?) -> Unit,
    availableTags: List<String>,
    tagFilter: String?,
    onTagSelected: (String?) -> Unit,
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    onCategoryToggled: (String) -> Unit,
    onAllCategories: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimensions.Padding.dialog, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.txn_list_filters),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            TextButton(onClick = onClearAll, enabled = hasAnyFilter, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.txn_list_clear_filters))
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = Dimensions.Padding.dialog, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            item(key = "sort") {
                FilterSection(stringResource(R.string.txn_filters_sort_by)) {
                    SortOption.entries.forEach { option ->
                        SheetChip(
                            selected = sortOption == option,
                            text = stringResource(option.labelRes),
                            icon = Icons.AutoMirrored.Filled.Sort,
                            onClick = { onSortSelected(option) }
                        )
                    }
                }
            }
            item(key = "type") {
                FilterSection(stringResource(R.string.txn_filters_type)) {
                    SHEET_TYPE_FILTERS.forEach { type ->
                        SheetChip(
                            selected = transactionTypeFilter == type,
                            text = type.label,
                            icon = type.filterIcon(),
                            onClick = { onTypeSelected(type) }
                        )
                    }
                }
            }
            if (accountOptions.isNotEmpty()) {
                item(key = "account") {
                    FilterSection(stringResource(R.string.txn_list_filter_account)) {
                        SheetChip(
                            selected = accountFilter == null,
                            text = stringResource(R.string.txn_list_all_accounts),
                            icon = Icons.Outlined.AccountBalanceWallet,
                            onClick = { onAccountSelected(null) }
                        )
                        accountOptions.forEach { option ->
                            SheetChip(
                                selected = accountFilter == option.key,
                                text = option.label,
                                icon = Icons.Outlined.AccountBalanceWallet,
                                onClick = { onAccountSelected(option.key) }
                            )
                        }
                    }
                }
            }
            if (profiles.isNotEmpty()) {
                item(key = "profile") {
                    FilterSection(stringResource(R.string.txn_list_filter_profile)) {
                        SheetChip(
                            selected = selectedProfileId == null,
                            text = stringResource(R.string.txn_list_all_profiles),
                            icon = Icons.Outlined.AccountBalance,
                            onClick = { onProfileSelected(null) }
                        )
                        profiles.forEach { profile ->
                            SheetChip(
                                selected = selectedProfileId == profile.id,
                                text = profile.displayName(),
                                icon = profileIcon(profile),
                                onClick = { onProfileSelected(profile.id) }
                            )
                        }
                    }
                }
            }
            if (availableTags.isNotEmpty() || tagFilter != null) {
                item(key = "tag") {
                    FilterSection(stringResource(R.string.txn_list_filter_tag)) {
                        SheetChip(
                            selected = tagFilter == null,
                            text = stringResource(R.string.txn_list_all_tags),
                            icon = Icons.Default.Sell,
                            onClick = { onTagSelected(null) }
                        )
                        availableTags.forEach { tag ->
                            SheetChip(
                                selected = tagFilter?.equals(tag, ignoreCase = true) == true,
                                text = tag,
                                icon = Icons.Default.Sell,
                                onClick = { onTagSelected(tag) }
                            )
                        }
                    }
                }
            }
            if (availableCategories.isNotEmpty() || selectedCategories.isNotEmpty()) {
                item(key = "category") {
                    FilterSection(
                        title = stringResource(R.string.txn_list_filter_category),
                        hint = stringResource(R.string.txn_filters_category_hint)
                    ) {
                        SheetChip(
                            selected = selectedCategories.isEmpty(),
                            text = stringResource(R.string.txn_list_all_categories),
                            onClick = onAllCategories
                        )
                        // A picked category the period has no rows in stays, so it can be unpicked.
                        (availableCategories + selectedCategories.filter { it !in availableCategories }).forEach { category ->
                            SheetChip(
                                selected = category in selectedCategories,
                                text = categoryLabel(category),
                                leading = { CategoryIcon(category = category, size = FilterChipDefaults.IconSize) },
                                onClick = { onCategoryToggled(category) }
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = onDismiss,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.dialog)
                .padding(top = Spacing.sm, bottom = Spacing.md)
        ) {
            Text(pluralStringResource(R.plurals.txn_filters_show_results, resultCount, resultCount))
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    hint: String? = null,
    chips: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            chips()
        }
    }
}

/** A sheet choice: its own icon at rest, a check once picked. */
@Composable
private fun SheetChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } }
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shapes = FilterChipDefaults.shapes(shape = MaterialTheme.shapes.medium),
        label = { Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            leading
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected)
    )
}
