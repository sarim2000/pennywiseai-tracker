package com.pennywiseai.tracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.pennywiseai.tracker.core.TransactionWidgets
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The preference writes that change what the widgets show refresh them through
 * [TransactionWidgets], once each, and other writes don't.
 */
class PreferenceWidgetRefreshTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var refreshes = 0
    private val widgets = TransactionWidgets { refreshes++ }
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "user_preferences.preferences_pb") }
        )
        repository = UserPreferencesRepository(store, widgets)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `the budget cycle start day refreshes the widgets`() = runBlocking {
        repository.updateBudgetCycleStartDay(5)
        assertEquals(1, refreshes)
    }

    @Test
    fun `the hide-amounts eye refreshes the widgets`() = runBlocking {
        repository.setBalanceHidden(true)
        assertEquals(1, refreshes)
        repository.toggleBalanceHidden(hiddenWhenUnset = true)
        assertEquals(2, refreshes)
    }

    @Test
    fun `the selected profile refreshes the widgets`() = runBlocking {
        repository.updateSelectedProfileId(7L)
        repository.updateSelectedProfileId(null)
        assertEquals(2, refreshes)
    }

    @Test
    fun `an unrelated setting does not refresh the widgets`() = runBlocking {
        repository.updateBaseCurrency("EUR")
        assertEquals(0, refreshes)
    }

    @Test
    fun `ignoring accounts refreshes the widgets`() {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putStringSet(any(), any()) } returns editor
        val prefs = mockk<SharedPreferences> { every { edit() } returns editor }
        val context = mockk<Context> { every { getSharedPreferences(any(), any()) } returns prefs }
        val ignored = IgnoredAccountsStore(context, widgets)

        ignored.replaceAll(setOf(IgnoredAccountsStore.keyFor("Zqt Bank", "4321")))

        verify(exactly = 1) { editor.apply() }
        assertEquals(1, refreshes)
    }
}
