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
    fun collect_leavesOutAPdfOverThePerFileLimit_butNotAPhoto() {
        val source = tmp.newFolder("src")
        File(source, "receipts").mkdirs()
        File(source, "receipts/big.jpg").writeBytes(ByteArray(11))
        File(source, "receipts/big.pdf").writeBytes(ByteArray(11))

        val collected = BackupReceipts.collect(
            source,
            listOf("receipts/big.jpg", "receipts/big.pdf"),
            maxFileBytes = 10
        )

        assertEquals(listOf("receipts/big.jpg"), collected.receipts.map { it.path })
        assertEquals(1, collected.skipped)
    }

    @Test
    fun collect_stopsAddingPdfsOnceTheirTotalIsFull_butKeepsSmallerPdfsThatFit() {
        val source = tmp.newFolder("src")
        File(source, "receipts").mkdirs()
        File(source, "receipts/a.pdf").writeBytes(ByteArray(6))
        File(source, "receipts/b.pdf").writeBytes(ByteArray(6))
        File(source, "receipts/c.pdf").writeBytes(ByteArray(4))

        val collected = BackupReceipts.collect(
            source,
            listOf("receipts/a.pdf", "receipts/b.pdf", "receipts/c.pdf"),
            maxTotalBytes = 10
        )

        assertEquals(listOf("receipts/a.pdf", "receipts/c.pdf"), collected.receipts.map { it.path })
        assertEquals(1, collected.skipped)
    }

    /** Photos backed up without a cap before PDFs came; they still do, and don't use the PDFs' room. */
    @Test
    fun collect_keepsEveryPhotoWhateverTheTotal() {
        val source = tmp.newFolder("src")
        File(source, "receipts").mkdirs()
        val photos = (1..5).map { "receipts/p$it.jpg" }
        photos.forEach { File(source, it).writeBytes(ByteArray(6)) }
        File(source, "receipts/a.pdf").writeBytes(ByteArray(10))

        val collected = BackupReceipts.collect(source, photos + "receipts/a.pdf", maxTotalBytes = 10)

        assertEquals(photos + "receipts/a.pdf", collected.receipts.map { it.path })
        assertEquals(0, collected.skipped)
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

    @Test
    fun restore_keepsAPhotoOverTheLimit_asBeforePdfs() {
        val filesDir = tmp.newFolder("files")
        val path = "receipts/old.jpg"

        val restored = BackupReceipts.restore(filesDir, listOf(BackupReceipt(path, b64(ByteArray(12) { 4 }))), setOf(path), maxFileBytes = 8)

        assertEquals(BackupReceipts.Restored(written = 1, skipped = 0), restored)
        assertEquals(12L, File(filesDir, path).length())
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

    /**
     * Unpadded Base64 of one byte over the cap fits the cap's padded length when the
     * cap isn't a multiple of 3 (8 bytes -> 12 chars, as do 9 bytes), so the decoded
     * size is checked too.
     */
    @Test
    fun restore_skipsAReceiptOneByteOverTheLimitThatPassesTheLengthCheck() {
        val filesDir = tmp.newFolder("files")
        val path = "receipts/over.pdf"
        val text = b64(ByteArray(9) { 3 })
        assertTrue(text.length <= BackupReceipts.encodedLength(8))

        val restored = BackupReceipts.restore(filesDir, listOf(BackupReceipt(path, text)), setOf(path), maxFileBytes = 8)

        assertEquals(BackupReceipts.Restored(written = 0, skipped = 1), restored)
        assertFalse(File(filesDir, path).exists())
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
