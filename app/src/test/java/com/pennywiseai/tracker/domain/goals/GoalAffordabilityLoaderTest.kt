package com.pennywiseai.tracker.domain.goals

import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.repository.GoalRules
import com.pennywiseai.tracker.domain.review.CycleTotal
import com.pennywiseai.tracker.domain.review.PeriodDetail
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.ReviewRange
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoalAffordabilityLoaderTest {
    private val today = LocalDate.of(2026, 11, 16)

    private val goals = mockk<GoalRepository>()
    private val reviews = mockk<PeriodReviewLoader>()
    private val prefs = mockk<UserPreferencesRepository>()
    private val loader = GoalAffordabilityLoader(goals, reviews, prefs)

    private val goal = GoalRules.progress(
        GoalEntity(
            id = 1, name = "Goal", targetAmount = BigDecimal("12000"), currency = "INR",
            dueDate = today.plusMonths(12), createdAt = today.minusMonths(1).atStartOfDay(),
        ),
        emptyList(),
        today,
    )

    /** Four cycles, the last being the current one: 50,000 in and 40,000 out each. */
    private val review = PeriodReview(
        range = mockk<ReviewRange>(),
        currencies = emptyList(),
        firstRecorded = null,
        lastRecorded = null,
        hasOlder = true,
        detail = PeriodDetail(
            cycles = mapOf(
                "INR" to (3 downTo 0).map {
                    val start = today.withDayOfMonth(1).minusMonths(it.toLong())
                    CycleTotal(start, start.plusMonths(1).minusDays(1), BigDecimal("50000"), BigDecimal("40000"), emptyMap())
                }
            ),
            spendRows = emptyList(),
        ),
    )

    init {
        every { goals.observeActiveGoals(today) } returns flowOf(listOf(goal))
        every { reviews.observe(ReviewKind.MONTH, 0, today, detail = true) } returns flowOf(review)
    }

    @Test
    fun `weighs goals against every profile's surplus`() = runTest {
        every { prefs.selectedProfileId } returns flowOf(null)
        val result = loader.observe(today).first().single()
        assertEquals(BigDecimal("1000"), result.needed)
        assertEquals(BigDecimal("10000.00"), result.surplus)
    }

    @Test
    fun `nothing while one profile is selected, and no history is loaded`() = runTest {
        every { prefs.selectedProfileId } returns flowOf(1L)
        assertTrue(loader.observe(today).first().isEmpty())
        assertEquals(null, loader.goalsToWeigh(today).first())
        verify(exactly = 0) { reviews.observe(any(), any(), any(), any()) }
    }
}
