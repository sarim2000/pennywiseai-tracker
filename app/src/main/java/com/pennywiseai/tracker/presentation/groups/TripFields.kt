package com.pennywiseai.tracker.presentation.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.trip.nextTripBoundary
import com.pennywiseai.tracker.data.trip.tripWindowOf
import com.pennywiseai.tracker.presentation.goals.toUtcDate
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The Trip switch's state while a group is being created or edited; [end] null is "until I stop it". */
data class TripDraft(
    val enabled: Boolean = false,
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate? = null,
    // The stored timestamps, kept exactly while their date isn't changed in the dialog.
    val storedStart: LocalDateTime? = null,
    val storedEnd: LocalDateTime? = null
) {
    val window: Pair<LocalDateTime?, LocalDateTime?>
        get() = if (enabled) tripWindowOf(start, end, storedStart, storedEnd) else null to null

    companion object {
        fun of(group: TransactionGroupEntity) = TripDraft(
            enabled = group.tripStart != null,
            start = group.tripStart?.toLocalDate() ?: LocalDate.now(),
            end = group.tripEnd?.toLocalDate(),
            storedStart = group.tripStart,
            storedEnd = group.tripEnd
        )
    }
}

/**
 * Trip switch with its dates. Turning it on is Pro: a free user gets [onNeedUpgrade]
 * instead, unless the group already is a trip ([alreadyTrip]), which keeps working.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TripFields(
    draft: TripDraft,
    onChange: (TripDraft) -> Unit,
    isPro: Boolean,
    alreadyTrip: Boolean,
    onNeedUpgrade: () -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    val dates = AppDates.dateFormatter()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { toggle(draft, onChange, isPro, alreadyTrip, onNeedUpgrade) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                stringResource(R.string.trip_switch_title),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = draft.enabled,
                onCheckedChange = { toggle(draft, onChange, isPro, alreadyTrip, onNeedUpgrade) }
            )
        }
        if (draft.enabled) {
            Text(
                stringResource(R.string.trip_switch_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DateRow(stringResource(R.string.trip_starts), draft.start.format(dates)) { picking = PickTarget.START }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    DateRow(
                        stringResource(R.string.trip_ends),
                        draft.end?.format(dates) ?: stringResource(R.string.trip_until_stopped)
                    ) { picking = PickTarget.END }
                }
                if (draft.end != null) {
                    TextButton(onClick = { onChange(draft.copy(end = null)) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.trip_until_stopped))
                    }
                }
            }
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    picking?.let { target ->
        val initial = if (target == PickTarget.START) draft.start else draft.end ?: draft.start
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val date = ms.toUtcDate()
                        onChange(
                            if (target == PickTarget.START) {
                                draft.copy(start = date, end = draft.end?.takeIf { !it.isBefore(date) })
                            } else {
                                draft.copy(end = date.takeIf { !it.isBefore(draft.start) } ?: draft.start)
                            }
                        )
                    }
                    picking = null
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.trip_picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.group_cancel))
                }
            }
        ) { DatePicker(state = pickerState) }
    }
}

/**
 * The current time for trip status, refreshed when the app resumes and when the next start or
 * end of any of [groups] passes, so a Scheduled/Running/Ended label never goes stale on screen.
 */
@Composable
fun rememberTripNow(groups: List<TransactionGroupEntity>): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LifecycleResumeEffect(Unit) {
        now = LocalDateTime.now()
        onPauseOrDispose {}
    }
    val next = nextTripBoundary(groups, now)
    LaunchedEffect(next, now) {
        if (next != null) {
            delay(Duration.between(LocalDateTime.now(), next).toMillis().coerceAtLeast(0) + 50)
            now = LocalDateTime.now()
        }
    }
    return now
}

/** "3 Dec – 9 Dec", or "From 3 Dec" for a trip with no end yet. */
@Composable
fun tripWindowText(group: TransactionGroupEntity): String {
    val dates = AppDates.dayMonthFormatter()
    val start = group.tripStart?.toLocalDate()?.format(dates) ?: return ""
    val end = group.tripEnd?.toLocalDate()?.format(dates)
    return if (end == null) stringResource(R.string.trip_window_open, start) else stringResource(R.string.trip_window, start, end)
}

private enum class PickTarget { START, END }

private fun toggle(draft: TripDraft, onChange: (TripDraft) -> Unit, isPro: Boolean, alreadyTrip: Boolean, onNeedUpgrade: () -> Unit) {
    if (!draft.enabled && !isPro && !alreadyTrip) onNeedUpgrade() else onChange(draft.copy(enabled = !draft.enabled))
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
