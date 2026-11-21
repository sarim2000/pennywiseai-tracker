package com.pennywiseai.tracker.ui.components.celebration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.presentation.budgetgroups.UNDER_BUDGET_CARD_DAYS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException

/** Each moment cheers once, and a first Achievements visit never cheers for history. */
class CelebrationClaimsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val repository by lazy {
        UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "prefs.preferences_pb") })
        ) {}
    }

    @After
    fun tearDown() = scope.cancel()

    /** What AchievementsViewModel.onShown does with the earned tiers. */
    private suspend fun visitAchievements(vararg earned: String): Boolean =
        CelebrationKeys.celebratesAchievements(repository.claimCelebrations(earned.toList() + CelebrationKeys.ACHIEVEMENTS_SEEDED))

    @Test
    fun `keys name the moment`() {
        assertEquals("goal_reached:uid-12", CelebrationKeys.goalReached("uid-12"))
        assertEquals("achievement:streak:2", CelebrationKeys.achievement("streak", 2))
    }

    @Test
    fun `a moment is celebrated once`() = runBlocking {
        assertTrue(repository.celebrateOnce(CelebrationKeys.goalReached("a")))
        assertFalse(repository.celebrateOnce(CelebrationKeys.goalReached("a")))
        assertTrue(repository.celebrateOnce(CelebrationKeys.goalReached("b")))
    }

    @Test
    fun `the first visit records earned tiers without cheering, then a new tier cheers once`() = runBlocking {
        val a1 = CelebrationKeys.achievement("streak", 1)
        val a2 = CelebrationKeys.achievement("streak", 2)
        assertFalse(visitAchievements(a1))
        assertFalse(visitAchievements(a1))
        assertTrue(visitAchievements(a2))
        assertFalse(visitAchievements(a2))
    }

    @Test
    fun `a first visit with nothing earned still lets the first badge cheer`() = runBlocking {
        assertFalse(visitAchievements())
        assertTrue(visitAchievements(CelebrationKeys.achievement("first_steps", 1)))
    }

    @Test
    fun `a full disk celebrates nothing so the next visit can retry`() = runBlocking {
        val broken = UserPreferencesRepository(diskFull()) {}
        val key = CelebrationKeys.goalReached("a")
        assertEquals(emptySet<String>(), broken.claimCelebrations(listOf(key)))
        assertFalse(broken.celebrateOnce(key))
    }

    @Test
    fun `cancellation still propagates from a celebration claim`() = runBlocking {
        val cancelled = UserPreferencesRepository(object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                throw CancellationException("cancelled")
            }
        }) {}
        var escaped = false
        try {
            cancelled.claimCelebrations(listOf(CelebrationKeys.goalReached("a")))
        } catch (e: CancellationException) {
            escaped = e.message == "cancelled"
        }
        assertTrue(escaped)
    }

    @Test
    fun `dismissing one currency-month leaves the other, and an old month is dropped`() = runBlocking {
        val octJod = CelebrationKeys.budgetMonth(YearMonth.of(2026, 10), "JOD")
        val octUsd = CelebrationKeys.budgetMonth(YearMonth.of(2026, 10), "USD")
        val sep = CelebrationKeys.budgetMonth(YearMonth.of(2026, 9), "JOD")
        val nov3 = LocalDate.parse("2026-11-03")
        repository.dismissBudgetMonthCard(octJod, nov3, UNDER_BUDGET_CARD_DAYS)
        repository.dismissBudgetMonthCard(octUsd, nov3, UNDER_BUDGET_CARD_DAYS)
        repository.dismissBudgetMonthCard(sep, nov3, UNDER_BUDGET_CARD_DAYS)
        assertEquals(setOf(octJod, octUsd), repository.budgetMonthCardsDismissed.first())
        repository.pruneBudgetMonthDismissals(LocalDate.parse("2026-11-08"), UNDER_BUDGET_CARD_DAYS)
        assertEquals(emptySet<String>(), repository.budgetMonthCardsDismissed.first())
    }

    private fun diskFull() = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            throw IOException("disk full")
        }
    }
}
