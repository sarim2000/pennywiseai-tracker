package com.pennywiseai.tracker.ui.components.celebration

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
        assertEquals("goal_reached:12", CelebrationKeys.goalReached(12))
        assertEquals("achievement:streak:2", CelebrationKeys.achievement("streak", 2))
    }

    @Test
    fun `a moment is celebrated once`() = runBlocking {
        assertTrue(repository.celebrateOnce(CelebrationKeys.goalReached(1)))
        assertFalse(repository.celebrateOnce(CelebrationKeys.goalReached(1)))
        assertTrue(repository.celebrateOnce(CelebrationKeys.goalReached(2)))
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
}
