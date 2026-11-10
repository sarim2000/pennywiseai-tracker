package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.presentation.common.filterIcon
import com.pennywiseai.tracker.presentation.common.shortLabel
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.profileIcon
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.*

@Composable
internal fun TransactionFilterHeader(
    searchQuery: String,
    categoryFilter: String?,
    selectedPeriod: TimePeriod,
    customRangeLabel: String?,
    periodChipLabel: String,
    transactionTypeFilter: TransactionTypeFilter,
    categoryLabel: String?,
    hasCategoryFilter: Boolean,
    selectedProfileName: String,
    hasProfileFilter: Boolean,
    hasAnyActiveFilter: Boolean,
    showSortMenu: Boolean,
    showPeriodMenu: Boolean,
    showTypeMenu: Boolean,
    showMoreFiltersMenu: Boolean,
    showAccountMenu: Boolean,
    showTagMenu: Boolean,
    collapsed: Boolean,
    sortOption: SortOption,
    timePeriods: List<TimePeriod>,
    availableCategories: List<String>,
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    accountOptions: List<com.pennywiseai.tracker.presentation.common.AccountOption>,
    accountFilter: String?,
    availableTags: List<String>,
    tagFilter: String?,
    onSearchQueryChange: (String) -> Unit,
    onSortClick: () -> Unit,
    onSortDismiss: () -> Unit,
    onSortSelected: (SortOption) -> Unit,
    onPeriodClick: () -> Unit,
    onPeriodDismiss: () -> Unit,
    onPeriodSelected: (TimePeriod) -> Unit,
    onTypeClick: () -> Unit,
    onTypeDismiss: () -> Unit,
    onTransactionTypeSelected: (TransactionTypeFilter) -> Unit,
    onMoreFiltersClick: () -> Unit,
    onMoreFiltersDismiss: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    selectedCategories: Set<String>,
    onCategoryToggled: (String) -> Unit,
    onProfileSelected: (Long?) -> Unit,
    onAccountClick: () -> Unit,
    onAccountDismiss: () -> Unit,
    onAccountSelected: (String?) -> Unit,
    onTagClick: () -> Unit,
    onTagDismiss: () -> Unit,
    onTagSelected: (String?) -> Unit,
    onResetFilters: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                categoryFilter = categoryFilter,
                focusRequester = focusRequester,
                trailingContent = {
                    Box {
                        IconButton(onClick = onSortClick, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = stringResource(R.string.txn_list_more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = onSortDismiss,
                            shape = MaterialTheme.shapes.large
                        ) {
                            SortOption.entries.forEachIndexed { index, option ->
                                SelectableDropdownMenuItem(
                                    selected = sortOption == option,
                                    text = { Text(stringResource(option.labelRes)) },
                                    shapes = MenuDefaults.itemShape(index, SortOption.entries.size),
                                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = null
                                        )
                                    },
                                    selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = { onSortSelected(option) }
                                )
                            }
                            if (hasAnyActiveFilter) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.txn_list_clear_filters)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        onResetFilters()
                                        onSortDismiss()
                                    }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(
            visible = !collapsed,
            enter = pwExpand(),
            exit = pwCollapse()
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                item {
                    Box {
                        ExpressiveFilterChip(
                            selected = true,
                            text = periodChipLabel,
                            icon = Icons.Default.CalendarMonth,
                            onClick = onPeriodClick
                        )

                        DropdownMenu(
                            expanded = showPeriodMenu,
                            onDismissRequest = onPeriodDismiss
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

                item {
                    Box {
                        ExpressiveFilterChip(
                            selected = transactionTypeFilter != TransactionTypeFilter.ALL,
                            text = transactionTypeFilter.shortLabel(),
                            icon = transactionTypeFilter.filterIcon(),
                            onClick = onTypeClick
                        )

                        DropdownMenu(
                            expanded = showTypeMenu,
                            onDismissRequest = onTypeDismiss,
                            shape = MaterialTheme.shapes.large
                        ) {
                            val typeFilters = TransactionTypeFilter.entries.filter { it.inTransactionsList }
                            typeFilters.forEachIndexed { index, typeFilter ->
                                SelectableDropdownMenuItem(
                                    selected = transactionTypeFilter == typeFilter,
                                    text = { Text(typeFilter.label) },
                                    shapes = MenuDefaults.itemShape(index, typeFilters.size),
                                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = typeFilter.filterIcon(),
                                            contentDescription = null
                                        )
                                    },
                                    selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = { onTransactionTypeSelected(typeFilter) }
                                )
                            }
                        }
                    }
                }

                item {
                    Box {
                        ExpressiveFilterChip(
                            selected = hasCategoryFilter || hasProfileFilter,
                            text = moreFiltersLabel(
                                categoryLabel = categoryLabel,
                                selectedProfileName = selectedProfileName,
                                hasCategoryFilter = hasCategoryFilter,
                                hasProfileFilter = hasProfileFilter
                            ),
                            icon = Icons.Default.Tune,
                            onClick = onMoreFiltersClick,
                            enabled = availableCategories.isNotEmpty() || profiles.isNotEmpty() ||
                                    hasCategoryFilter || hasProfileFilter
                        )

                        DropdownMenu(
                            expanded = showMoreFiltersMenu,
                            onDismissRequest = onMoreFiltersDismiss
                        ) {
                            SelectableDropdownMenuItem(
                                selected = !hasCategoryFilter,
                                text = { Text(stringResource(R.string.txn_list_all_categories)) },
                                shapes = MenuDefaults.itemShape(0, 1),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                onClick = { onCategorySelected(null) }
                            )
                            // Tick/untick to include or exclude (#786); the menu stays
                            // open so several can be changed in one go.
                            // Kept as checkbox rows, not CheckableDropdownMenuItem: every category
                            // is ticked by default, and the expressive item swaps the category icon
                            // for a check, leaving a wall of identical rows.
                            availableCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(categoryLabel(category), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        Checkbox(
                                            checked = category in selectedCategories,
                                            onCheckedChange = null
                                        )
                                    },
                                    trailingIcon = {
                                        CategoryIcon(category = category, size = Dimensions.Icon.small)
                                    },
                                    onClick = { onCategoryToggled(category) }
                                )
                            }
                            if (profiles.isNotEmpty()) {
                                HorizontalDivider()
                            }
                            SelectableDropdownMenuItem(
                                selected = selectedProfileId == null,
                                text = { Text(stringResource(R.string.txn_list_all_profiles)) },
                                shapes = MenuDefaults.itemShape(0, profiles.size + 1),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                leadingIcon = { Icon(Icons.Outlined.AccountBalance, contentDescription = null) },
                                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                onClick = { onProfileSelected(null) }
                            )
                            profiles.forEachIndexed { index, profile ->
                                SelectableDropdownMenuItem(
                                    selected = selectedProfileId == profile.id,
                                    text = { Text(profile.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    shapes = MenuDefaults.itemShape(index + 1, profiles.size + 1),
                                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                    leadingIcon = {
                                        Icon(
                                            profileIcon(profile),
                                            contentDescription = null
                                        )
                                    },
                                    selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = { onProfileSelected(profile.id) }
                                )
                            }
                        }
                    }
                }

                if (availableTags.isNotEmpty() || tagFilter != null) {
                    item {
                        Box {
                            ExpressiveFilterChip(
                                selected = tagFilter != null,
                                text = tagFilter ?: stringResource(R.string.txn_list_filter_tag),
                                icon = Icons.Default.Sell,
                                onClick = onTagClick
                            )

                            DropdownMenu(
                                expanded = showTagMenu,
                                onDismissRequest = onTagDismiss
                            ) {
                                SelectableDropdownMenuItem(
                                    selected = tagFilter == null,
                                    text = { Text(stringResource(R.string.txn_list_all_tags)) },
                                    shapes = MenuDefaults.itemShape(0, availableTags.size + 1),
                                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                    leadingIcon = { Icon(Icons.Default.Sell, contentDescription = null) },
                                    selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = { onTagSelected(null) }
                                )
                                availableTags.forEachIndexed { index, tag ->
                                    SelectableDropdownMenuItem(
                                        selected = tagFilter?.equals(tag, ignoreCase = true) == true,
                                        text = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        shapes = MenuDefaults.itemShape(index + 1, availableTags.size + 1),
                                        colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                        leadingIcon = { Icon(Icons.Default.Sell, contentDescription = null) },
                                        selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                        onClick = { onTagSelected(tag) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (accountOptions.isNotEmpty()) {
                    item {
                        Box {
                            val selectedAccountLabel =
                                accountOptions.firstOrNull { it.key == accountFilter }?.label
                            ExpressiveFilterChip(
                                selected = accountFilter != null,
                                text = selectedAccountLabel ?: stringResource(R.string.txn_list_filter_account),
                                icon = Icons.Outlined.AccountBalanceWallet,
                                onClick = onAccountClick
                            )

                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = onAccountDismiss
                            ) {
                                SelectableDropdownMenuItem(
                                    selected = accountFilter == null,
                                    text = { Text(stringResource(R.string.txn_list_all_accounts)) },
                                    shapes = MenuDefaults.itemShape(0, accountOptions.size + 1),
                                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                    leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                                    selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = { onAccountSelected(null) }
                                )
                                accountOptions.forEachIndexed { index, option ->
                                    SelectableDropdownMenuItem(
                                        selected = accountFilter == option.key,
                                        text = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        shapes = MenuDefaults.itemShape(index + 1, accountOptions.size + 1),
                                        colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                        leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                                        selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                        onClick = { onAccountSelected(option.key) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun moreFiltersLabel(
    categoryLabel: String?,
    selectedProfileName: String,
    hasCategoryFilter: Boolean,
    hasProfileFilter: Boolean
): String {
    return when {
        hasCategoryFilter && hasProfileFilter -> stringResource(R.string.txn_list_filters_two)
        hasCategoryFilter -> categoryLabel ?: stringResource(R.string.txn_list_filter_category)
        hasProfileFilter -> selectedProfileName
        else -> stringResource(R.string.txn_list_filters)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    categoryFilter: String? = null,
    focusRequester: FocusRequester? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.height(Dimensions.Component.minTouchTarget),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Spacing.md, end = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.txn_list_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = if (categoryFilter != null) stringResource(R.string.txn_list_search_in_category, categoryLabel(categoryFilter))
                                else stringResource(R.string.txn_list_search_placeholder),
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
            trailingContent?.invoke()
        }
    }
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
