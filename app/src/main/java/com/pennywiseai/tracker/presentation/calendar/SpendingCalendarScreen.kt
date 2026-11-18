package com.pennywiseai.tracker.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.spending.Bill
import com.pennywiseai.tracker.domain.spending.BillSource
import com.pennywiseai.tracker.domain.spending.CalendarDay
import com.pennywiseai.tracker.domain.spending.SpendingCalendar
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.bestInk
import com.pennywiseai.tracker.ui.theme.isDark
import com.pennywiseai.tracker.ui.theme.spending
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.CurrencyUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * A month at a glance: each day shaded by what was spent, a dot where a payment is
 * due. Tapping a day lists both, each linking to where it lives.
 */
@Composable
fun SpendingCalendarScreen(
    onNavigateBack: () -> Unit,
    onOpenTransactions: (day: LocalDate, currency: String) -> Unit,
    onOpenBill: (BillSource) -> Unit,
    viewModel: SpendingCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The open sheet is this one value: shown while it's set, cleared on every way out.
    // Deliberately not saved: restored after a recreation, it named a day whose sheet was no
    // longer on screen, and tapping that day again set the same value, so nothing opened.
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    // The month the sheet was opened for: a month that finishes loading while it's open
    // replaces the state, and its days and leading currency aren't this sheet's.
    var sheetMonth by remember { mutableStateOf<SpendingCalendarUiState?>(null) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshDate()
        onPauseOrDispose { }
    }

    PennyWiseScaffold(
        title = stringResource(R.string.calendar_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.calendar_back))
            }
        },
        actions = {
            if (state?.isThisMonth == false) {
                // Words rather than an icon: a calendar glyph alone doesn't say where it goes.
                TextButton(onClick = viewModel::showThisMonth, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.calendar_this_month))
                }
            }
        },
    ) { padding ->
        val loaded = state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (loaded == null) {
                LoadingIndicator(Modifier.align(Alignment.Center))
            } else {
                CalendarContent(
                    state = loaded,
                    onMonth = viewModel::showMonth,
                    onSelectCurrency = viewModel::selectCurrency,
                    onDay = {
                        selectedDay = it
                        sheetMonth = loaded
                    },
                )
                selectedDay?.let { day ->
                    val month = if (YearMonth.from(day) == loaded.month) loaded else sheetMonth ?: loaded
                    DaySheet(
                        day = day,
                        today = loaded.today,
                        content = month.days[day] ?: CalendarDay(),
                        selectedCurrency = month.currency,
                        onDismiss = { selectedDay = null },
                        onOpenTransactions = { currency ->
                            selectedDay = null
                            onOpenTransactions(day, currency)
                        },
                        onOpenBill = { source ->
                            selectedDay = null
                            onOpenBill(source)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarContent(
    state: SpendingCalendarUiState,
    onMonth: (Long) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    val monthLabel = state.month.format(AppDates.pattern("MMMM yyyy"))
    LazyColumn(
        contentPadding = PaddingValues(
            start = Dimensions.Padding.content,
            end = Dimensions.Padding.content,
            bottom = Spacing.Layout.scrollBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "month") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(-1) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.calendar_previous_month),
                    )
                }
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = { onMonth(1) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.calendar_next_month),
                    )
                }
            }
        }
        if (state.currencies.size > 1 && state.currency != null) {
            item(key = "currencies") {
                ConnectedChoiceRow(
                    options = state.currencies,
                    selected = state.currency,
                    onSelect = onSelectCurrency,
                    label = { it },
                    fillMaxWidth = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
        item(key = "grid") { MonthGrid(state, onDay) }
        item(key = "legend") { Legend() }
        if (state.days.isEmpty()) {
            item(key = "empty") {
                PennyWiseEmptyState(
                    icon = Icons.Default.CalendarMonth,
                    headline = stringResource(R.string.calendar_empty_headline, monthLabel),
                    description = stringResource(R.string.calendar_empty_body),
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(state: SpendingCalendarUiState, onDay: (LocalDate) -> Unit) {
    val locale = AppDates.locale()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        // Each day reads its own weekday out, so the header is for the eye only.
        Row(modifier = Modifier.clearAndSetSemantics { }) {
            (0L until 7L).map { state.firstDayOfWeek.plus(it) }.forEach { weekday ->
                Text(
                    text = weekday.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        state.weeks.forEach { week ->
            Row {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            date = date,
                            day = state.days[date],
                            level = state.level(date),
                            isToday = date == state.today,
                            onClick = { onDay(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    day: CalendarDay?,
    level: Int,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val fill = dayFill(level)
    val ink = if (level == 0) colors.onSurface else fill.compositeOver(colors.background).bestInk()
    val hasDue = day?.planned?.isNotEmpty() == true
    val description = dayDescription(date, day)
    val todayLabel = stringResource(R.string.calendar_day_today)
    // A day is a seventh of the width: about 47dp on a 360dp phone, a hair under the 48dp
    // minimum, which a month grid accepts as Material's date picker does. The gap between
    // days is drawn inside the cell, so the whole width is the touch target.
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (isToday) stateDescription = todayLabel
            }
            .padding(horizontal = Spacing.xxs / 2)
            // Today's ring sits on the page with a gap before the fill, so it never has to
            // stand out against a shade of its own colour, in any theme or at any level.
            .then(
                if (isToday) {
                    Modifier
                        .border(Dimensions.Component.dividerThicknessEmphasis, colors.primary, MaterialTheme.shapes.small)
                        .padding(TODAY_RING_INSET)
                } else {
                    Modifier
                }
            )
            .background(fill, MaterialTheme.shapes.small)
            // The ring's inset comes out of the padding, so today is no taller than its week.
            .padding(vertical = if (isToday) Spacing.xs - TODAY_RING_INSET else Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterVertically),
    ) {
        Text(
            text = date.format(AppDates.pattern("d")),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.Bold else null,
            color = ink,
        )
        // Always takes its room, so days with and without a dot line up.
        Box(
            Modifier
                .size(Dimensions.Component.calendarDot)
                .clip(CircleShape)
                .background(if (hasDue) ink else Color.Transparent)
        )
    }
}

/** The border is drawn inside the cell, so the inset is its width plus a visible gap of page. */
private val TODAY_RING_INSET = Dimensions.Component.dividerThicknessEmphasis + Spacing.xxs

/** A day's shade for a [level] of spend; the legend draws its scale from this too. */
@Composable
@ReadOnlyComposable
private fun dayFill(level: Int): Color {
    val colors = MaterialTheme.colorScheme
    if (level == 0) return colors.surfaceContainerLow
    // A faint tint of primary all but vanishes on a dark page, so dark starts the scale
    // stronger; light keeps its even quarters. Both end at full primary.
    val first = if (colors.isDark) DARK_FIRST_SHADE else 1f / SpendingCalendar.LEVELS
    val alpha = first + (1f - first) * (level - 1) / (SpendingCalendar.LEVELS - 1)
    return colors.primary.copy(alpha = alpha)
}

/** The lightest shade's opacity in a dark theme, enough to tell from an empty day. */
private const val DARK_FIRST_SHADE = 0.4f

/** What a screen reader says for a day: the date, what was spent, how many payments are due. */
@Composable
private fun dayDescription(date: LocalDate, day: CalendarDay?): String {
    val parts = mutableListOf(
        stringResource(R.string.calendar_day_date, date.format(AppDates.pattern("EEEE")), date.format(AppDates.dayMonthFormatter()))
    )
    day?.spent?.takeIf { it.isNotEmpty() }?.let { spent ->
        val amounts = spent.entries.sortedBy { it.key }.joinToString(", ") { (currency, amount) ->
            CurrencyFormatter.formatCurrency(amount, currency)
        }
        parts += stringResource(R.string.calendar_day_spent, amounts)
    }
    day?.planned?.takeIf { it.isNotEmpty() }?.let {
        parts += pluralStringResource(R.plurals.calendar_day_due, it.size, it.size)
    }
    return parts.joinToString(". ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(
                Modifier
                    .size(Dimensions.Component.calendarDot)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
            Text(
                stringResource(R.string.calendar_legend_due),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The scale the days are shaded with, drawn from the same colours: a stronger shade
        // is darker in a light theme and brighter in a dark one, so words alone can't say it.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.calendar_legend_less),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            (1..SpendingCalendar.LEVELS).forEach { level ->
                Box(
                    Modifier
                        .size(Dimensions.Component.legendDot)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(dayFill(level))
                )
            }
            Text(
                stringResource(R.string.calendar_legend_more),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    day: LocalDate,
    today: LocalDate,
    content: CalendarDay,
    selectedCurrency: String?,
    onDismiss: () -> Unit,
    onOpenTransactions: (String) -> Unit,
    onOpenBill: (BillSource) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Scrolls, so a day with many payments, or large text, never pushes rows off the screen.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.dialog)
                .padding(bottom = Dimensions.Padding.dialog),
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent),
        ) {
            Text(
                text = stringResource(R.string.calendar_day_date, day.format(AppDates.pattern("EEEE")), day.format(AppDates.dateFormatter())),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (content.spent.isEmpty() && content.planned.isEmpty()) {
                Text(
                    text = stringResource(R.string.calendar_day_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (content.spent.isNotEmpty()) {
                SectionHeaderV2(title = stringResource(R.string.calendar_day_section_spent))
                // The currency the month is shaded in leads, then the rest in the app's usual order.
                val spent = CurrencyUtils.sortCurrencies(content.spent.keys.toList())
                    .sortedByDescending { it == selectedCurrency }
                    .map { it to content.spent.getValue(it) }
                GroupedList {
                    spent.forEachIndexed { i, (currency, amount) ->
                        GroupedRow(
                            position = ListItemPosition.from(i, spent.size),
                            onClick = { onOpenTransactions(currency) },
                        ) {
                            // Named by currency, so rows differ even while amounts are hidden.
                            RowLabels(title = stringResource(R.string.calendar_day_transactions, currency), titleMaxLines = 2)
                            Text(
                                text = CurrencyFormatter.formatCurrency(amount, currency),
                                style = PennyWiseText.amountRow,
                                color = MaterialTheme.colorScheme.spending,
                            )
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.inline))
                        }
                    }
                }
            }
            if (content.planned.isNotEmpty()) {
                SectionHeaderV2(title = stringResource(R.string.calendar_day_section_due))
                GroupedList {
                    content.planned.forEachIndexed { i, bill ->
                        DueRow(bill, ListItemPosition.from(i, content.planned.size), isOverdue = bill.dueDate.isBefore(today), onOpenBill)
                    }
                }
            }
        }
    }
}

@Composable
private fun DueRow(bill: Bill, position: ListItemPosition, isOverdue: Boolean, onOpenBill: (BillSource) -> Unit) {
    val source = bill.source
    GroupedRow(position = position, onClick = source?.let { { onOpenBill(it) } }) {
        RowLabels(
            title = bill.name,
            subtitle = when (source) {
                BillSource.Subscription -> stringResource(R.string.calendar_kind_subscription)
                BillSource.Recurring -> stringResource(R.string.calendar_kind_recurring)
                is BillSource.Card -> stringResource(if (isOverdue) R.string.calendar_kind_card_overdue else R.string.calendar_kind_card)
                null -> null
            },
            subtitleColor = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = CurrencyFormatter.formatCurrency(bill.amount.amount, bill.amount.currency),
            style = PennyWiseText.amountRow,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (source != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.inline))
        }
    }
}
