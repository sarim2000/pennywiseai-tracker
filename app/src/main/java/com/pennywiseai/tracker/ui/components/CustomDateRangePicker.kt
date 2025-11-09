package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (startDate: LocalDate, endDate: LocalDate) -> Unit,
    initialStartDate: LocalDate? = null,
    initialEndDate: LocalDate? = null,
    modifier: Modifier = Modifier
) {
    // Convert LocalDate to milliseconds using epoch day to avoid timezone issues
    // Using epoch day (days since 1970-01-01) ensures consistent date handling across timezones
    val initialStartMillis = initialStartDate?.toEpochDay()?.let { it * 86400000L }
    val initialEndMillis = initialEndDate?.toEpochDay()?.let { it * 86400000L }

    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startMillis = dateRangePickerState.selectedStartDateMillis
                    val endMillis = dateRangePickerState.selectedEndDateMillis

                    if (startMillis != null && endMillis != null) {
                        // Convert milliseconds to LocalDate using epoch day to avoid timezone issues
                        val startDate = LocalDate.ofEpochDay(startMillis / 86400000L)
                        val endDate = LocalDate.ofEpochDay(endMillis / 86400000L)

                        // Validate date range (should always be true with Material DateRangePicker, but be defensive)
                        if (startDate <= endDate) {
                            onConfirm(startDate, endDate)
                        }
                    }
                },
                enabled = dateRangePickerState.selectedStartDateMillis != null &&
                         dateRangePickerState.selectedEndDateMillis != null
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            modifier = Modifier,
            title = {
                Text(
                    text = "Select Date Range",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            },
            headline = {
                DateRangePickerDefaults.DateRangePickerHeadline(
                    selectedStartDateMillis = dateRangePickerState.selectedStartDateMillis,
                    selectedEndDateMillis = dateRangePickerState.selectedEndDateMillis,
                    displayMode = dateRangePickerState.displayMode,
                    dateFormatter = DatePickerDefaults.dateFormatter(),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)
                )
            },
            showModeToggle = true, // Allow switching between calendar and text input
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                headlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                weekdayContentColor = MaterialTheme.colorScheme.onSurface,
                subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                yearContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                currentYearContentColor = MaterialTheme.colorScheme.primary,
                selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
                selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                dayContentColor = MaterialTheme.colorScheme.onSurface,
                disabledDayContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                disabledSelectedDayContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                disabledSelectedDayContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                todayContentColor = MaterialTheme.colorScheme.primary,
                todayDateBorderColor = MaterialTheme.colorScheme.primary,
                dayInSelectionRangeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        )
    }
}
