package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Loads a month of [SpendingCalendar] days over the same rows as Home and the reviews
 * (selected profile, no ignored accounts), with what's still due from [today] on.
 */
class SpendingCalendarLoader @Inject constructor(
    private val periodReviewLoader: PeriodReviewLoader,
    private val upcomingPaymentsLoader: UpcomingPaymentsLoader,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    fun observe(month: YearMonth, today: LocalDate): Flow<Map<LocalDate, CalendarDay>> {
        val end = month.atEndOfMonth()
        // A month that's over has nothing left to pay.
        val planned = if (end.isBefore(today)) flowOf(emptyList()) else upcomingPaymentsLoader.observe(today, end, keepCardCharges = true).map { it.bills }
        return combine(
            periodReviewLoader.scopedRows(month.atDay(1), end),
            planned,
            userPreferencesRepository.countCreditCardAsExpense.distinctUntilChanged(),
        ) { rows, bills, creditAsExpense ->
            SpendingCalendar.days(month, rows, bills, creditAsExpense)
        }
    }
}
