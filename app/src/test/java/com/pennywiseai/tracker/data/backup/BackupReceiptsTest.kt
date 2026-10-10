package com.pennywiseai.tracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class BackupReceiptsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    @Test
    fun safeReceiptFile_rejectsPathsOutsideReceipts() {
        val filesDir = tmp.newFolder("files")
        listOf(
            "../x.jpg",
            "/data/x.jpg",
            "receipts/../../x",
            "receipts/../x.jpg",
            "receipts/..",
            "receipts/",
            "receipts/sub/x.jpg",
            "receipts\\..\\x.jpg",
            "other/x.jpg",
            "x.jpg",
            ""
        ).forEach { assertNull(it, BackupReceipts.safeReceiptFile(filesDir, it)) }

        val ok = BackupReceipts.safeReceiptFile(filesDir, "receipts/receipt_1.jpg")
        assertNotNull(ok)
        assertEquals(File(filesDir, "receipts").canonicalFile, ok!!.parentFile)
    }

    @Test
    fun collectThenRestore_roundTripsFiles() {
        val source = tmp.newFolder("src")
        File(source, "receipts").mkdirs()
        val bytes = byteArrayOf(1, 2, 3, 4, -1)
        File(source, "receipts/receipt_1.jpg").writeBytes(bytes)

        val collected = BackupReceipts.collect(
            source,
            listOf("receipts/receipt_1.jpg", "receipts/receipt_1.jpg", "receipts/missing.jpg", "../escape.jpg")
        )
        assertEquals(1, collected.size)

        val target = tmp.newFolder("dst")
        val written = BackupReceipts.restore(target, collected, setOf("receipts/receipt_1.jpg"))
        assertEquals(1, written)
        assertTrue(bytes.contentEquals(File(target, "receipts/receipt_1.jpg").readBytes()))
    }

    @Test
    fun restore_skipsUnsafeUnreferencedAndExistingFiles() {
        val filesDir = tmp.newFolder("files")
        val receipts = listOf(
            BackupReceipt("../../evil.jpg", b64(byteArrayOf(9))),
            BackupReceipt("receipts/unreferenced.jpg", b64(byteArrayOf(9))),
            BackupReceipt("receipts/existing.jpg", b64(byteArrayOf(9))),
            BackupReceipt("receipts/bad.jpg", "not base64!!")
        )
        File(filesDir, "receipts").mkdirs()
        File(filesDir, "receipts/existing.jpg").writeBytes(byteArrayOf(7))

        val written = BackupReceipts.restore(
            filesDir,
            receipts,
            setOf("../../evil.jpg", "receipts/existing.jpg", "receipts/bad.jpg")
        )

        assertEquals(0, written)
        assertFalse(File(tmp.root, "evil.jpg").exists())
        assertFalse(File(filesDir, "receipts/unreferenced.jpg").exists())
        assertFalse(File(filesDir, "receipts/bad.jpg").exists())
        // Never overwritten.
        assertTrue(byteArrayOf(7).contentEquals(File(filesDir, "receipts/existing.jpg").readBytes()))
    }
}
