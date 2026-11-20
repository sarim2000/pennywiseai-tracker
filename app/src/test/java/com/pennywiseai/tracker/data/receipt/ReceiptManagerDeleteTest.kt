package com.pennywiseai.tracker.data.receipt

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A restored backup's receipt path is untrusted: deleting it must never leave `receipts/`. */
class ReceiptManagerDeleteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager(filesDir: File) = ReceiptManager(
        mockk<Context> {
            every { this@mockk.filesDir } returns filesDir
            every { cacheDir } returns tmp.newFolder("cache")
        }
    )

    @Test
    fun deleteReceipt_refusesAPathOutsideReceipts() {
        val filesDir = tmp.newFolder("files")
        val database = File(tmp.root, "databases/pennywise_database").apply {
            parentFile!!.mkdirs()
            writeText("db")
        }
        val sibling = File(filesDir, "settings.json").apply { writeText("{}") }
        // Present, so `receipts/../…` resolves: the guard is tested, not a missing directory.
        File(filesDir, "receipts").mkdirs()
        val manager = manager(filesDir)

        listOf(
            "../databases/pennywise_database",
            "receipts/../../databases/pennywise_database",
            database.absolutePath,
            "settings.json",
            "receipts/../settings.json"
        ).forEach { manager.deleteReceipt(it) }

        assertTrue(database.exists())
        assertTrue(sibling.exists())
    }

    @Test
    fun deleteReceipt_deletesAStoredReceipt() {
        val filesDir = tmp.newFolder("files")
        val receipt = File(filesDir, "receipts/receipt_1.pdf").apply {
            parentFile!!.mkdirs()
            writeText("%PDF-")
        }

        manager(filesDir).deleteReceipt("receipts/receipt_1.pdf")

        assertFalse(receipt.exists())
    }
}
