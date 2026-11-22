package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.presentation.add.AddDatePickerDialog
import com.pennywiseai.tracker.presentation.add.PickerRow
import com.pennywiseai.tracker.presentation.add.SaveBar
import com.pennywiseai.tracker.presentation.goals.toUtcDate
import com.pennywiseai.tracker.presentation.settings.SettingsChoice
import com.pennywiseai.tracker.presentation.settings.SettingsChoiceSheet
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.ColorSwatchRow
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.RoundedField
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.orSupporting
import com.pennywiseai.tracker.ui.components.rememberAmountEntry
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.text.rememberSyncedTextFieldState
import com.pennywiseai.tracker.ui.text.withCommittedText
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.format.TextStyle

/** What a picker row opens: one sheet or dialog at a time. */
private enum class BudgetPicker { WEEKDAY, MONTH_DAY, START_DATE, END_DATE, CATEGORY }

/**
 * Create or edit a budget, in the order a person thinks about one: what it's called and how
 * much, how often it resets, then (optionally) which categories it covers. Laid out like the
 * Add forms: rounded fields, a connected choice, picker rows, Save pinned at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BudgetGroupEditScreen(
    viewModel: BudgetGroupEditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<BudgetPicker?>(null) }
    // Closing a picker would hand focus back to a text field and pop its keyboard up again.
    val focusManager = LocalFocusManager.current
    val open = { p: BudgetPicker -> focusManager.clearFocus(); picker = p }

    LaunchedEffect(uiState.saveComplete) {
        if (uiState.saveComplete) onNavigateBack()
    }

    val isEditing = (uiState.groupId ?: -1L) > 0
    val overallAmount = uiState.overallAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val amount = rememberAmountEntry(uiState.overallAmount, viewModel::updateOverallAmount, uiState.currency)
    val canSave = uiState.name.isNotBlank() && overallAmount > BigDecimal.ZERO && !uiState.isSaving && !amount.isUnfinished

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    PennyWiseScaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        customTopBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(if (isEditing) R.string.budget_edit_title_edit else R.string.budget_edit_title_new),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.budgets_back))
                    }
                },
                actionContent = {
                    if (isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.budgets_delete))
                        }
                    }
                },
                hazeState = hazeState
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                SaveBar(
                    error = null,
                    enabled = canSave,
                    loading = uiState.isSaving,
                    onClick = withCommittedText { if (!amount.isUnfinished) viewModel.save() }
                )
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@PennyWiseScaffold
        }

        val lazyListState = rememberLazyListState()
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + Spacing.Layout.scrollBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
        ) {
            item(key = "name") {
                TextField(
                    state = rememberSyncedTextFieldState(uiState.name, viewModel::updateName),
                    label = { Text(stringResource(R.string.budget_edit_name_placeholder)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedField.shape,
                    colors = RoundedField.colors()
                )
            }

            item(key = "amount") {
                // A sum too ("1200+300"), with the operator row on the keyboard.
                val sum = amount.sumHint
                TextField(
                    state = amount.state,
                    inputTransformation = amount.inputTransformation,
                    label = { Text(stringResource(R.string.add_field_amount)) },
                    prefix = { Text(CurrencyFormatter.getCurrencySymbol(uiState.currency)) },
                    textStyle = MaterialTheme.typography.headlineSmall.money(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = sum?.isError == true,
                    supportingText = sum.orSupporting(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().then(amount.modifier),
                    shape = RoundedField.shape,
                    colors = RoundedField.colors()
                )
            }

            item(key = "period") {
                PeriodSection(
                    state = uiState,
                    onPeriod = viewModel::updatePeriodType,
                    onOpen = open,
                    onRollover = viewModel::updateRollover
                )
            }

            item(key = "categories") {
                CategoriesSection(
                    state = uiState,
                    overallAmount = overallAmount,
                    onAmountChange = viewModel::updateCategoryAmount,
                    onRemove = viewModel::removeCategory,
                    onAdd = { open(BudgetPicker.CATEGORY) }
                )
            }

            // Color (#763): the dot beside the name wherever the budget appears.
            item(key = "color") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)) {
                    SectionHeaderV2(title = stringResource(R.string.budget_edit_color), topSpacing = Spacing.none)
                    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
                        ColorSwatchRow(selected = uiState.color, onSelect = viewModel::updateColor)
                    }
                }
            }
        }
    }

    val close = { picker = null }
    when (picker) {
        BudgetPicker.WEEKDAY -> SettingsChoiceSheet(
            title = stringResource(R.string.budget_edit_week_starts_on),
            choices = (1..7).map { day ->
                SettingsChoice(
                    label = dayOfWeekName(day),
                    selected = day == uiState.weekStartDay,
                    onSelect = { viewModel.updateWeekStartDay(day) }
                )
            },
            onDismiss = close
        )
        BudgetPicker.MONTH_DAY -> SettingsChoiceSheet(
            title = stringResource(R.string.budget_edit_month_starts_on),
            choices = ((1..31) + BudgetCycle.LAST_WORKING_DAY).map { day ->
                SettingsChoice(
                    label = monthDayLabel(day),
                    selected = day == uiState.monthStartDay,
                    onSelect = { viewModel.updateMonthStartDay(day) }
                )
            },
            onDismiss = close
        )
        BudgetPicker.START_DATE -> AddDatePickerDialog(
            initial = uiState.startDate,
            onPick = { viewModel.updateStartDate(it.toUtcDate()) },
            onDismiss = close
        )
        BudgetPicker.END_DATE -> AddDatePickerDialog(
            initial = uiState.endDate,
            onPick = { viewModel.updateEndDate(it.toUtcDate()) },
            onDismiss = close
        )
        BudgetPicker.CATEGORY -> CategoryPickerSheet(
            categories = uiState.availableCategories,
            typeBuckets = uiState.availableTypeBuckets,
            onPickCategory = viewModel::addCategory,
            onPickBucket = viewModel::addTypeBucket,
            onDismiss = close
        )
        null -> Unit
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.budgets_delete_title)) },
            text = { Text(stringResource(R.string.budgets_delete_message, uiState.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteGroup()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.budgets_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.budgets_action_cancel))
                }
            }
        )
    }
}

/**
 * How often the budget resets: Weekly · Monthly · One-time, then the one setting that
 * cadence needs, the window it covers right now, and rollover where it applies.
 */
@Composable
private fun PeriodSection(
    state: BudgetGroupEditUiState,
    onPeriod: (BudgetPeriodType) -> Unit,
    onOpen: (BudgetPicker) -> Unit,
    onRollover: (Boolean) -> Unit
) {
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        SectionHeaderV2(title = stringResource(R.string.budget_edit_period), topSpacing = Spacing.none)
        ConnectedChoiceRow(
            options = listOf(BudgetPeriodType.WEEKLY, BudgetPeriodType.MONTHLY, BudgetPeriodType.CUSTOM),
            selected = state.periodType,
            onSelect = onPeriod,
            label = { period ->
                stringResource(
                    when (period) {
                        BudgetPeriodType.WEEKLY -> R.string.add_sub_cycle_weekly
                        BudgetPeriodType.MONTHLY -> R.string.add_sub_cycle_monthly
                        BudgetPeriodType.CUSTOM -> R.string.budget_edit_period_one_time
                    }
                )
            }
        )
        GroupedList {
            when (state.periodType) {
                BudgetPeriodType.WEEKLY -> PickerRow(
                    position = ListItemPosition.Top,
                    label = stringResource(R.string.budget_edit_week_starts_on),
                    value = dayOfWeekName(state.weekStartDay),
                    onClick = { onOpen(BudgetPicker.WEEKDAY) },
                    leading = { Icon(Icons.Default.EventRepeat, contentDescription = null, tint = iconTint) }
                )
                BudgetPeriodType.MONTHLY -> PickerRow(
                    position = ListItemPosition.Top,
                    label = stringResource(R.string.budget_edit_month_starts_on),
                    value = monthDayLabel(state.monthStartDay),
                    onClick = { onOpen(BudgetPicker.MONTH_DAY) },
                    leading = { Icon(Icons.Default.EventRepeat, contentDescription = null, tint = iconTint) }
                )
                BudgetPeriodType.CUSTOM -> {
                    val formatter = AppDates.dateFormatter()
                    PickerRow(
                        position = ListItemPosition.Top,
                        label = stringResource(R.string.budget_edit_start_date),
                        value = state.startDate.format(formatter),
                        onClick = { onOpen(BudgetPicker.START_DATE) },
                        leading = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = iconTint) }
                    )
                    PickerRow(
                        position = ListItemPosition.Middle,
                        label = stringResource(R.string.budget_edit_end_date),
                        value = state.endDate.format(formatter),
                        onClick = { onOpen(BudgetPicker.END_DATE) },
                        leading = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = iconTint) }
                    )
                }
            }

            // The window the budget tracks now, as Home and the widget resolve it.
            val anchorCaption = when (state.periodType) {
                BudgetPeriodType.WEEKLY ->
                    stringResource(R.string.budget_edit_resets_every, dayOfWeekName(state.weekStartDay))
                BudgetPeriodType.MONTHLY ->
                    if (BudgetCycle.isLastWorkingDay(state.monthStartDay)) {
                        stringResource(R.string.budget_edit_resets_last_working_day)
                    } else {
                        stringResource(R.string.budget_edit_resets_on_day, state.monthStartDay)
                    }
                BudgetPeriodType.CUSTOM -> stringResource(R.string.budget_edit_runs_once)
            }
            val dayMonth = AppDates.dayMonthFormatter()
            GroupedRow(position = if (state.rolloverAvailable) ListItemPosition.Middle else ListItemPosition.Bottom) {
                Icon(Icons.Default.EventAvailable, contentDescription = null, tint = iconTint)
                RowLabels(
                    title = stringResource(
                        R.string.budgets_date_range,
                        state.startDate.format(dayMonth),
                        state.endDate.format(dayMonth)
                    ),
                    subtitle = anchorCaption,
                    titleMaxLines = Int.MAX_VALUE
                )
            }

            if (state.rolloverAvailable) {
                RolloverRow(
                    checked = state.rolloverEnabled,
                    onCheckedChange = onRollover,
                    startsNext = when {
                        !state.rolloverStartsNextPeriod() -> null
                        state.periodType == BudgetPeriodType.WEEKLY ->
                            stringResource(R.string.budget_rollover_starts_next_week)
                        else -> stringResource(R.string.budget_rollover_starts_next_month)
                    }
                )
            }
        }
    }
}

/** Rollover on or off; the whole row toggles it. [startsNext]: when it begins, while this period carries nothing in. */
@Composable
private fun RolloverRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, startsNext: String?) {
    GroupedRow(
        position = ListItemPosition.Bottom,
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        }
    ) {
        Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        RowLabels(
            title = stringResource(R.string.budget_edit_rollover),
            subtitle = listOfNotNull(stringResource(R.string.budget_edit_rollover_subtitle), startsNext).joinToString("\n"),
            titleMaxLines = Int.MAX_VALUE,
            subtitleMaxLines = Int.MAX_VALUE
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Which spending counts. None picked means all of it, said in the empty row rather than
 * left to guess; each picked category can carry its own limit inside the total.
 */
@Composable
private fun CategoriesSection(
    state: BudgetGroupEditUiState,
    overallAmount: BigDecimal,
    onAmountChange: (String, BigDecimal) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit
) {
    val categories = state.categories
    val canAdd = state.availableCategories.isNotEmpty() || state.availableTypeBuckets.isNotEmpty()
    Column(
        modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
    ) {
        SectionHeaderV2(title = stringResource(R.string.budget_edit_category_limits), topSpacing = Spacing.none)
        GroupedList {
            if (categories.isEmpty()) {
                GroupedRow(position = ListItemPosition.Top) {
                    Icon(Icons.Default.DonutLarge, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    RowLabels(title = stringResource(R.string.budgets_tracking_all), titleMaxLines = Int.MAX_VALUE)
                }
            }
            categories.forEachIndexed { index, cat ->
                CategoryLimitRow(
                    item = cat,
                    position = if (index == 0) ListItemPosition.Top else ListItemPosition.Middle,
                    currency = state.currency,
                    onAmountChange = { onAmountChange(cat.categoryName, it) },
                    onRemove = { onRemove(cat.categoryName) }
                )
            }
            GroupedRow(position = ListItemPosition.Bottom, onClick = onAdd, enabled = canAdd) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = if (canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.budget_edit_add_category),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // What the category limits leave of the total, or how far past it they go.
        if (categories.isNotEmpty() && overallAmount > BigDecimal.ZERO) {
            val diff = overallAmount - categories.fold(BigDecimal.ZERO) { acc, c -> acc + c.amount }
            if (diff.signum() != 0) {
                Text(
                    text = if (diff > BigDecimal.ZERO) {
                        stringResource(R.string.budget_edit_unallocated, CurrencyFormatter.formatCurrency(diff, state.currency))
                    } else {
                        stringResource(R.string.budget_edit_over_allocated, CurrencyFormatter.formatCurrency(diff.abs(), state.currency))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (diff > BigDecimal.ZERO) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
            }
        }
    }
}

@Composable
private fun CategoryBadge(categoryName: String) {
    val color = CategoryMapping.colorFor(categoryName)
    Box(
        modifier = Modifier
            .size(Dimensions.Icon.avatar)
            .clip(CircleShape)
            .background(color.copy(alpha = Dimensions.Alpha.categoryTint)),
        contentAlignment = Alignment.Center
    ) {
        CategoryIcon(category = categoryName, size = Dimensions.Icon.inline, tint = color)
    }
}

/** A category in the budget: what it spent this month, its optional limit, and remove. */
@Composable
private fun CategoryLimitRow(
    item: CategoryBudgetItem,
    position: ListItemPosition,
    currency: String,
    onAmountChange: (BigDecimal) -> Unit,
    onRemove: () -> Unit
) {
    // Local text, so a "0" typed on the way to "0.5" isn't wiped by the parsed zero coming back.
    var amountText by remember(item.amount) {
        mutableStateOf(if (item.amount.signum() == 0) "" else item.amount.toPlainString())
    }
    val label = if (item.matchType != null) {
        stringResource(R.string.budget_edit_type_bucket_all, categoryLabel(item.categoryName))
    } else categoryLabel(item.categoryName)
    GroupedRow(position = position, horizontalArrangement = Arrangement.spacedBy(Spacing.smd)) {
        CategoryBadge(item.categoryName)
        RowLabels(
            title = label,
            subtitle = item.currentSpending.takeIf { it.signum() > 0 }?.let {
                stringResource(R.string.budget_edit_spent, CurrencyFormatter.formatCurrency(it, currency))
            }
        )
        val entry = rememberAmountEntry(
            amountText,
            { value ->
                if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                    amountText = value
                    onAmountChange(value.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                }
            },
            currency
        )
        val sum = entry.sumHint
        TextField(
            state = entry.state,
            inputTransformation = entry.inputTransformation,
            label = { Text(stringResource(R.string.plan_budget_category_limit)) },
            prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency)) },
            isError = sum?.isError == true,
            supportingText = sum.orSupporting(),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.weight(0.9f).then(entry.modifier),
            textStyle = MaterialTheme.typography.bodyLarge.money(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedField.shape,
            colors = RoundedField.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.budget_edit_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Picks a category (or a whole transaction type) to add; picking adds it and closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerSheet(
    categories: List<String>,
    typeBuckets: List<TypeBucketOption>,
    onPickCategory: (String) -> Unit,
    onPickBucket: (TypeBucketOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val pick: (() -> Unit) -> Unit = { action ->
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    // Type buckets first: there are few of them, and they'd be lost under a long category list.
    val rows: List<Pair<String, () -> Unit>> =
        typeBuckets.map { it.displayName to { pick { onPickBucket(it) } } } +
            categories.map { it to { pick { onPickCategory(it) } } }
    val bucketCount = typeBuckets.size
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            stringResource(R.string.budget_edit_add_category),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
        )
        Spacer(Modifier.height(Spacing.sm))
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                bottom = Spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
        ) {
            itemsIndexed(rows) { index, (name, onClick) ->
                GroupedRow(position = ListItemPosition.from(index, rows.size), onClick = onClick) {
                    CategoryBadge(name)
                    RowLabels(
                        title = if (index < bucketCount) {
                            stringResource(R.string.budget_edit_type_bucket_all, categoryLabel(name))
                        } else categoryLabel(name)
                    )
                }
            }
        }
    }
}

// App locale, not Locale.getDefault(), so it matches the per-app language.
@Composable
@ReadOnlyComposable
private fun dayOfWeekName(value: Int): String =
    DayOfWeek.of(value.coerceIn(1, 7)).getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])

@Composable
@ReadOnlyComposable
private fun monthDayLabel(day: Int): String =
    if (BudgetCycle.isLastWorkingDay(day)) {
        stringResource(R.string.settings_budget_cycle_last_working_day_short)
    } else {
        day.toString()
    }
