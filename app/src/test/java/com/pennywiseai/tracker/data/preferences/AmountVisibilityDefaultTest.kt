package com.pennywiseai.tracker.data.preferences

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
        val repository = UserPreferencesRepository(FakeContext())

        assertTrue(repository.isBalanceHidden.first())
        assertFalse(repository.isAmountHiddenEverywhere.first())
    }
}
