package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.presentation.common.shortLabel
import com.pennywiseai.tracker.ui.components.ExpressiveFilterChip
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.displayName
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

private enum class FilterField(val title: Int) {
    TYPE(R.string.insights_filter_type),
    ACCOUNT(R.string.analytics_filter_account),
    CATEGORY(R.string.analytics_filter_category),
    PROFILE(R.string.txn_list_filter_profile)
}

private data class FilterChoice(val label: String, val selected: Boolean, val choose: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AnalyticsFilters(viewModel: AnalyticsViewModel, availableCategories: List<String>) {
    val type by viewModel.transactionTypeFilter.collectAsStateWithLifecycle()
    val defaultType by viewModel.defaultTypeFilter.collectAsStateWithLifecycle()
    val account by viewModel.accountFilter.collectAsStateWithLifecycle()
    val accounts by viewModel.accountOptions.collectAsStateWithLifecycle()
    val category by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val profileId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val currencyScope by viewModel.currencyScope.collectAsStateWithLifecycle()
    val currency = currencyScope.currency
    val currencies by viewModel.availableCurrencies.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showCurrencies by remember { mutableStateOf(false) }

    val accountLabel = accounts.firstOrNull { it.key == account }?.label
    val activeCategoryLabel = category?.let { categoryLabel(it) }
    val profileLabel = profiles.firstOrNull { it.id == profileId }?.displayName()
    val summary = listOfNotNull(type.shortLabel(), accountLabel, activeCategoryLabel, profileLabel).joinToString(" · ")
    val count = listOf(type != defaultType, account != null, category != null, profileId != null).count { it }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = count > 0,
            onClick = { showFilters = true },
            modifier = Modifier.heightIn(min = Dimensions.Component.minTouchTarget),
            shapes = FilterChipDefaults.shapes(
                shape = MaterialTheme.shapes.extraLarge,
                selectedShape = MaterialTheme.shapes.medium
            ),
            label = {
                Text(
                    if (count == 0) stringResource(R.string.insights_filters_default, summary)
                    else stringResource(R.string.insights_filters_active, count, summary)
                )
            },
            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, Modifier.size(Dimensions.Icon.small)) }
        )
        if (!currencyScope.unified && currencies.size > 1) {
            Box {
                ExpressiveFilterChip(
                    selected = currency != currencies.first(),
                    text = currency,
                    icon = Icons.Default.CurrencyExchange,
                    modifier = Modifier.heightIn(min = Dimensions.Component.minTouchTarget),
                    onClick = { showCurrencies = true }
                )
                DropdownMenu(
                    expanded = showCurrencies,
                    onDismissRequest = { showCurrencies = false },
                    shape = MaterialTheme.shapes.large
                ) {
                    currencies.forEachIndexed { index, option ->
                        SelectableDropdownMenuItem(
                            selected = option == currency,
                            text = { Text(option) },
                            shapes = MenuDefaults.itemShape(index, currencies.size),
                            colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                            selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = {
                                viewModel.selectCurrency(option)
                                showCurrencies = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (!showFilters) return
    var expanded by rememberSaveable { mutableStateOf<FilterField?>(null) }
    val allAccounts = stringResource(R.string.analytics_filter_all_accounts)
    val allCategories = stringResource(R.string.analytics_filter_all_categories)
    val allProfiles = stringResource(R.string.insights_filter_all_profiles)
    val currentLabels = mapOf(
        FilterField.TYPE to type.label,
        FilterField.ACCOUNT to (accountLabel ?: allAccounts),
        FilterField.CATEGORY to (activeCategoryLabel ?: allCategories),
        FilterField.PROFILE to (profileLabel ?: allProfiles)
    )
    val activeField = expanded
    val choices = when (activeField) {
        FilterField.TYPE -> TransactionTypeFilter.entries.map { option ->
            FilterChoice(option.label, option == type) { viewModel.setTransactionTypeFilter(option) }
        }
        FilterField.ACCOUNT -> listOf(FilterChoice(allAccounts, account == null) { viewModel.setAccountFilter(null) }) +
            accounts.map { option ->
                FilterChoice(option.label, option.key == account) { viewModel.setAccountFilter(option.key) }
            }
        FilterField.CATEGORY -> listOf(FilterChoice(allCategories, category == null, viewModel::clearCategoryFilter)) +
            (availableCategories + listOfNotNull(category)).distinct().map { option ->
                FilterChoice(categoryLabel(option), option == category) { viewModel.setCategoryFilter(option) }
            }
        FilterField.PROFILE -> listOf(FilterChoice(allProfiles, profileId == null) { viewModel.selectProfile(null) }) +
            profiles.map { option ->
                FilterChoice(option.displayName(), option.id == profileId) { viewModel.selectProfile(option.id) }
            }
        null -> emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = { showFilters = false },
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimensions.Padding.dialog,
                end = Dimensions.Padding.dialog,
                bottom = Dimensions.Padding.dialog
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
        ) {
            item {
                SectionHeaderV2(
                    title = stringResource(activeField?.title ?: R.string.txn_list_filters),
                    topSpacing = Spacing.none,
                    leading = if (activeField != null) {
                        {
                            IconButton(onClick = { expanded = null }, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.txn_list_filters)
                                )
                            }
                        }
                    } else null,
                    action = if (activeField == null && count > 0) {
                        {
                            TextButton(
                                onClick = {
                                    viewModel.resetTransactionTypeFilter()
                                    viewModel.clearCategoryFilter()
                                    viewModel.setAccountFilter(null)
                                    if (profileId != null) viewModel.selectProfile(null)
                                    expanded = null
                                },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text(stringResource(R.string.analytics_filter_clear))
                            }
                        }
                    } else null
                )
            }
            if (activeField == null) {
                item {
                    GroupedList {
                        FilterField.entries.forEachIndexed { index, field ->
                            GroupedRow(
                                position = ListItemPosition.from(index, FilterField.entries.size),
                                onClick = { expanded = field }
                            ) {
                                RowLabels(
                                    title = stringResource(field.title),
                                    subtitle = currentLabels.getValue(field),
                                    titleMaxLines = Int.MAX_VALUE,
                                    subtitleMaxLines = Int.MAX_VALUE
                                )
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    }
                }
            } else {
                items(choices.size) { index ->
                    val choice = choices[index]
                    GroupedRow(
                        position = ListItemPosition.from(index, choices.size),
                        modifier = Modifier.semantics {
                            role = Role.RadioButton
                            selected = choice.selected
                        },
                        onClick = {
                            choice.choose()
                            expanded = null
                        }
                    ) {
                        RowLabels(title = choice.label, titleMaxLines = Int.MAX_VALUE)
                        if (choice.selected) Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            }
        }
    }
}
