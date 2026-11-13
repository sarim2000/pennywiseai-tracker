package com.pennywiseai.tracker.presentation.common

import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.utils.AppDates
import java.time.LocalDate

/** The dates of the current cycle if it starts on [startDay], e.g. "Nov 15 – Dec 14". */
internal fun budgetCycleRange(startDay: Int): String {
    val (start, end) = BudgetCycle.currentCycle(LocalDate.now(), startDay)
    val formatter = AppDates.dayMonthFormatter()
    return "${formatter.format(start)} – ${formatter.format(end)}"
}
