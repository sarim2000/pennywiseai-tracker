package com.pennywiseai.tracker.presentation.transactions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Replacing or removing a receipt must never leave the row pointing at a deleted file. */
class PersistReceiptChangeTest {

    private val deleted = mutableListOf<String>()

    private fun change(oldPath: String?, newPath: String?, persist: suspend () -> Unit) = runBlocking {
        persistReceiptChange(oldPath, newPath, { deleted += it }, persist)
    }

    @Test
    fun replace_deletesTheOldFileOnlyAfterTheRowIsSaved() {
        var savedBeforeDelete = false
        change("receipts/old.jpg", "receipts/new.pdf") { savedBeforeDelete = deleted.isEmpty() }

        assertTrue(savedBeforeDelete)
        assertEquals(listOf("receipts/old.jpg"), deleted)
    }

    @Test
    fun failedSave_keepsTheOldFileAndDeletesTheNewOne() {
        try {
            change("receipts/old.jpg", "receipts/new.pdf") { error("disk full") }
            fail("the save failure should propagate")
        } catch (e: IllegalStateException) {
            assertEquals("disk full", e.message)
        }

        assertEquals(listOf("receipts/new.pdf"), deleted)
    }

    @Test
    fun remove_deletesTheOldFileAfterTheSave() {
        change("receipts/old.jpg", null) {}

        assertEquals(listOf("receipts/old.jpg"), deleted)
    }

    @Test
    fun failedRemove_keepsTheOldFile() {
        runCatching { change("receipts/old.jpg", null) { error("boom") } }

        assertEquals(emptyList<String>(), deleted)
    }

    @Test
    fun unchangedReceipt_deletesNothing() {
        change("receipts/same.jpg", "receipts/same.jpg") {}
        runCatching { change("receipts/same.jpg", "receipts/same.jpg") { error("boom") } }

        assertEquals(emptyList<String>(), deleted)
    }

    @Test
    fun cancelledSave_deletesNothing() {
        runCatching { change("receipts/old.jpg", "receipts/new.pdf") { throw CancellationException("left") } }

        assertEquals(emptyList<String>(), deleted)
    }
}
