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
        assertEquals(1, collected.receipts.size)
        assertEquals(0, collected.skipped)

        val target = tmp.newFolder("dst")
        val restored = BackupReceipts.restore(target, collected.receipts, setOf("receipts/receipt_1.jpg"))
        assertEquals(BackupReceipts.Restored(written = 1, skipped = 0), restored)
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

        val restored = BackupReceipts.restore(
            filesDir,
            receipts,
            setOf("../../evil.jpg", "receipts/existing.jpg", "receipts/bad.jpg")
        )

        assertEquals(0, restored.written)
        assertFalse(File(tmp.root, "evil.jpg").exists())
        assertFalse(File(filesDir, "receipts/unreferenced.jpg").exists())
        assertFalse(File(filesDir, "receipts/bad.jpg").exists())
        // Never overwritten.
        assertTrue(byteArrayOf(7).contentEquals(File(filesDir, "receipts/existing.jpg").readBytes()))
    }

    @Test
    fun collect_leavesOutAFileOverThePerFileLimit() {
        val source = tmp.newFolder("src")
        File(source, "receipts").mkdirs()
        File(source, "receipts/small.jpg").writeBytes(ByteArray(10))
        File(source, "receipts/big.pdf").writeBytes(ByteArray(11))

        val collected = BackupReceipts.collect(
            source,
            listOf("receipts/small.jpg", "receipts/big.pdf"),
            maxFileBytes = 10
        )

        assertEquals(listOf("receipts/small.jpg"), collected.receipts.map { it.path })
        assertEquals(1, collected.skipped)
    }

    @Test
    fun collect_stopsAddingOnceTheBackupIsFull_butKeepsSmallerFilesThatFit() {
        val source = tmp.newFolder("src")
        File(source, "receipts").mkdirs()
        File(source, "receipts/a.jpg").writeBytes(ByteArray(6))
        File(source, "receipts/b.jpg").writeBytes(ByteArray(6))
        File(source, "receipts/c.jpg").writeBytes(ByteArray(4))

        val collected = BackupReceipts.collect(
            source,
            listOf("receipts/a.jpg", "receipts/b.jpg", "receipts/c.jpg"),
            maxTotalBytes = 10
        )

        assertEquals(listOf("receipts/a.jpg", "receipts/c.jpg"), collected.receipts.map { it.path })
        assertEquals(1, collected.skipped)
    }

    @Test
    fun restore_skipsAnOversizedReceiptBeforeDecodingIt() {
        val filesDir = tmp.newFolder("files")
        val receipts = listOf(
            BackupReceipt("receipts/ok.pdf", b64(ByteArray(9) { 1 })),
            // Longer than any 9-byte file encodes to; not even valid Base64, which
            // proves it was never decoded.
            BackupReceipt("receipts/huge.pdf", "A".repeat(BackupReceipts.encodedLength(9).toInt() + 1))
        )

        val restored = BackupReceipts.restore(
            filesDir,
            receipts,
            setOf("receipts/ok.pdf", "receipts/huge.pdf"),
            maxFileBytes = 9
        )

        assertEquals(BackupReceipts.Restored(written = 1, skipped = 1), restored)
        assertFalse(File(filesDir, "receipts/huge.pdf").exists())
    }

    /** A file exactly at the limit still restores: the length check matches Base64 padding. */
    @Test
    fun restore_acceptsAFileExactlyAtTheLimit() {
        val filesDir = tmp.newFolder("files")
        listOf(7L, 8L, 9L).forEach { size ->
            val path = "receipts/at_$size.pdf"
            val restored = BackupReceipts.restore(
                filesDir,
                listOf(BackupReceipt(path, b64(ByteArray(size.toInt()) { 2 }))),
                setOf(path),
                maxFileBytes = size
            )
            assertEquals("size $size", BackupReceipts.Restored(written = 1, skipped = 0), restored)
        }
    }

    /** An old backup's JPEG receipt (no size fields anywhere) restores as before. */
    @Test
    fun restore_oldJpegReceiptRestoresUnchanged() {
        val filesDir = tmp.newFolder("files")
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        val json = """{"path":"receipts/receipt_1700000000000.jpg","data_base64":"${b64(jpeg)}"}"""
        val receipt = backupJson.decodeFromString<BackupReceipt>(json)

        val restored = BackupReceipts.restore(filesDir, listOf(receipt), setOf(receipt.path))

        assertEquals(1, restored.written)
        assertTrue(jpeg.contentEquals(File(filesDir, receipt.path).readBytes()))
    }
}
