package com.pennywiseai.tracker.data.preferences

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pennywiseai.tracker.ui.components.showAmounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * #1077 review: until the user taps the eye, only Home hides amounts. Masking every
 * screen by default made a fresh import look empty.
 */
class AmountVisibilityDefaultTest {

    private class FakeContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File =
            File(System.getProperty("java.io.tmpdir"), "pennywise_amount_visibility_" + System.nanoTime()).apply { mkdirs() }
    }

    @Test
    fun `with no saved choice Home hides amounts but other screens show them`() = runBlocking {
        val repository = UserPreferencesRepository(FakeContext()) {}

        assertTrue(repository.isBalanceHidden.first())
        assertFalse(repository.isAmountHiddenEverywhere.first())
    }

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the top bars Show amounts shows them everywhere and a second tap keeps them shown`() = runBlocking {
        // Its own file: the Context constructor shares one DataStore across the test JVM.
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "user_preferences.preferences_pb") }
        val repository = UserPreferencesRepository(store) {}
        repository.toggleBalanceHidden(hiddenWhenUnset = false) // a shake hides them everywhere
        assertTrue(repository.isAmountHiddenEverywhere.first())

        showAmounts(repository)
        showAmounts(repository) // a double tap before the eye goes away

        assertFalse(repository.isAmountHiddenEverywhere.first())
        assertFalse(repository.isBalanceHidden.first())
        scope.cancel()
    }
}
