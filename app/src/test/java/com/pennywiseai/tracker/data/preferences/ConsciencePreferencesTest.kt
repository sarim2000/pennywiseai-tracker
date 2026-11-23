package com.pennywiseai.tracker.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.pennywiseai.tracker.domain.conscience.ConscienceCandidate
import com.pennywiseai.tracker.domain.conscience.ConscienceCategory
import com.pennywiseai.tracker.domain.conscience.ConscienceFact
import com.pennywiseai.tracker.domain.conscience.ConscienceFrequency
import com.pennywiseai.tracker.domain.conscience.ConscienceKind
import com.pennywiseai.tracker.domain.conscience.ConscienceLine
import com.pennywiseai.tracker.domain.conscience.ConscienceRemark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/** How the conscience's settings and remarks behave in the real store, on its own file per test. */
class ConsciencePreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: UserPreferencesRepository

    private val enabledKey = booleanPreferencesKey("conscience_enabled")
    private val mutedKey = stringSetPreferencesKey("conscience_muted_kinds")
    private val addedLater = setOf(
        ConscienceKind.COMEBACK, ConscienceKind.WEEKLY, ConscienceKind.RECORD_DAY, ConscienceKind.SPIKE, ConscienceKind.HELLO
    )

    private val hour = 60 * 60 * 1000L
    private val now = System.currentTimeMillis()

    private suspend fun claim(fact: ConscienceFact, lineId: String, atMillis: Long, notified: Boolean): ConscienceRemark? =
        repository.tryClaimConscience(
            listOf(ConscienceCandidate(fact, listOf(ConscienceLine(lineId, 0)))),
            "en",
            atMillis,
            atMillis / (24 * hour),
            Random(1)
        ) { choice ->
            ConscienceRemark(atMillis, atMillis, "line", choice.fact.kind, choice.lineKey, notified = notified)
        }

    @Before
    fun setUp() {
        store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "user_preferences.preferences_pb") })
        repository = UserPreferencesRepository(store) {}
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `an opt-in from before the other kinds keeps only the first three`() = runBlocking {
        store.edit { it[enabledKey] = true }

        assertEquals(addedLater, repository.conscienceMutedKinds.first())
    }

    @Test
    fun `an earlier opt-in keeps its kinds through off and on`() = runBlocking {
        store.edit { it[enabledKey] = true }

        repository.setConscienceEnabled(false)
        repository.setConscienceEnabled(true)

        assertEquals(addedLater, repository.conscienceMutedKinds.first())
        assertEquals(addedLater.map { it.name }.toSet(), store.data.first()[mutedKey])
    }

    @Test
    fun `an earlier opt-in turns a new kind on from the screen`() = runBlocking {
        store.edit { it[enabledKey] = true }

        repository.setConscienceKindEnabled(ConscienceKind.SPIKE, true)

        assertEquals(addedLater - ConscienceKind.SPIKE, repository.conscienceMutedKinds.first())
    }

    @Test
    fun `a new opt-in gets every kind`() = runBlocking {
        assertEquals(emptySet<ConscienceKind>(), repository.conscienceMutedKinds.first())

        repository.setConscienceEnabled(true)

        assertEquals(emptySet<ConscienceKind>(), repository.conscienceMutedKinds.first())
        assertEquals(emptySet<String>(), store.data.first()[mutedKey])
    }

    @Test
    fun `a remark only saved overnight leaves the day's slot`() = runBlocking {
        repository.setConscienceEnabled(true)
        repository.setConscienceFrequency(ConscienceFrequency.LESS)

        assertNotNull(claim(ConscienceFact.LateNight(ConscienceCategory.FOOD), "late_01", now - 12 * hour, notified = false))
        assertNotNull(claim(ConscienceFact.Repeat(ConscienceCategory.FOOD, 3, 6), "repeat_01", now, notified = true))
        // That one notified: the week's only slot is gone now.
        assertNull(claim(ConscienceFact.Repeat(ConscienceCategory.SHOPPING, 3, 6), "repeat_02", now + hour, notified = true))
        assertEquals(listOf(false, true), repository.conscienceRemarks.first().map { it.notified }.reversed())
    }
}
