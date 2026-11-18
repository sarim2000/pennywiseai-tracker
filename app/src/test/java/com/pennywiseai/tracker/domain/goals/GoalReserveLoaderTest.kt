package com.pennywiseai.tracker.domain.goals

import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.repository.GoalRules
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoalReserveLoaderTest {
    private val today = LocalDate.of(2026, 11, 16)

    private val goals = mockk<GoalRepository>()
    private val prefs = mockk<UserPreferencesRepository>()
    private val loader = GoalReserveLoader(goals, prefs)

    private val goal = GoalRules.progress(
        GoalEntity(
            id = 1, name = "Goal", targetAmount = BigDecimal("12000"), currency = "INR",
            dueDate = today.plusMonths(12), createdAt = today.minusMonths(1).atStartOfDay(),
        ),
        emptyList(),
        today,
    )

    init {
        every { goals.observeActiveGoals(today) } returns flowOf(listOf(goal))
        every { goals.observeContributions() } returns flowOf(emptyList())
    }

    @Test
    fun `loads the goals in progress across every profile`() = runTest {
        every { prefs.selectedProfileId } returns flowOf(null)
        assertEquals(listOf(goal), loader.observe(today).first()?.goals)
    }

    @Test
    fun `nothing while one profile is selected`() = runTest {
        every { prefs.selectedProfileId } returns flowOf(1L)
        assertNull(loader.observe(today).first())
    }
}
