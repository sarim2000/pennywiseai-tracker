package com.pennywiseai.tracker.presentation.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A new encrypted backup needs at least eight characters, typed the same twice. */
class EncryptedExportPassphraseTest {

    @Test
    fun `a passphrase shorter than eight characters can't create a backup`() {
        assertFalse(canCreateEncryptedBackup("a", "a"))
        assertFalse(canCreateEncryptedBackup("abcdefg", "abcdefg"))
    }

    @Test
    fun `eight matching characters create a backup`() {
        assertTrue(canCreateEncryptedBackup("abcdefgh", "abcdefgh"))
    }

    @Test
    fun `a long passphrase that doesn't match its confirmation can't create a backup`() {
        assertFalse(canCreateEncryptedBackup("abcdefgh", "abcdefgi"))
        assertFalse(canCreateEncryptedBackup("abcdefgh", ""))
    }
}
