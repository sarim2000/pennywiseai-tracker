package com.pennywiseai.tracker.data.preferences

import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore.Companion.isIgnored
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore.Companion.keyFor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #826: the gate that keeps a relative's account out of the app. */
class IgnoredAccountsStoreTest {

    private val ignored = setOf(keyFor("HDFC Bank", "4321"))

    @Test
    fun `an ignored account is skipped`() {
        assertTrue(isIgnored(ignored, "HDFC Bank", "4321"))
    }

    @Test
    fun `another account at the same bank still counts`() {
        assertFalse(isIgnored(ignored, "HDFC Bank", "9999"))
    }

    @Test
    fun `the same digits at a different bank still count`() {
        assertFalse(isIgnored(ignored, "ICICI Bank", "4321"))
    }

    @Test
    fun `a transaction we cannot attribute is never dropped`() {
        // Skipping on a null/blank account would silently lose real
        // transactions whose SMS had no account digits to parse.
        assertFalse(isIgnored(ignored, "HDFC Bank", null))
        assertFalse(isIgnored(ignored, null, "4321"))
        assertFalse(isIgnored(ignored, "HDFC Bank", ""))
        assertFalse(isIgnored(ignored, " ", "4321"))
    }

    @Test
    fun `nothing is ignored when the set is empty`() {
        assertFalse(isIgnored(emptySet(), "HDFC Bank", "4321"))
    }
}
