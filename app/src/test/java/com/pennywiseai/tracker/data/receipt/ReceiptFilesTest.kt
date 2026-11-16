package com.pennywiseai.tracker.data.receipt

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ReceiptFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun copyCapped_copiesAFileAtTheLimit() {
        val bytes = ByteArray(1000) { it.toByte() }
        val out = ByteArrayOutputStream()

        assertTrue(ReceiptFiles.copyCapped(ByteArrayInputStream(bytes), out, maxBytes = 1000))
        assertArrayEquals(bytes, out.toByteArray())
    }

    @Test
    fun copyCapped_rejectsOneByteOver() {
        val out = ByteArrayOutputStream()

        assertFalse(ReceiptFiles.copyCapped(ByteArrayInputStream(ByteArray(1001)), out, maxBytes = 1000))
    }

    /** The cap holds while copying: an endless stream is cut off, whatever size was reported. */
    @Test
    fun copyCapped_stopsReadingPastTheLimit() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 0.also { served++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int = len.also { served += it }
        }

        assertFalse(ReceiptFiles.copyCapped(endless, ByteArrayOutputStream(), maxBytes = 10_000))
        assertTrue(served < 10_000 + 2 * DEFAULT_BUFFER_SIZE)
    }

    @Test
    fun copyCapped_defaultLimitIsFiveMegabytes() {
        assertEquals(5L * 1024 * 1024, ReceiptFiles.MAX_RECEIPT_BYTES)
    }

    @Test
    fun hasPdfMagic_acceptsAPdfHeader() {
        val file = tmp.newFile("bill.bin").apply { writeBytes("%PDF-1.7\n%synthetic".toByteArray()) }

        assertTrue(ReceiptFiles.hasPdfMagic(file))
    }

    @Test
    fun hasPdfMagic_rejectsOtherFilesWhateverTheirName() {
        val jpeg = tmp.newFile("bill.pdf").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0)) }
        val short = tmp.newFile("short.pdf").apply { writeBytes("%PD".toByteArray()) }
        val empty = tmp.newFile("empty.pdf")
        val html = tmp.newFile("page.pdf").apply { writeBytes("<html>%PDF-</html>".toByteArray()) }

        listOf(jpeg, short, empty, html).forEach { assertFalse(it.name, ReceiptFiles.hasPdfMagic(it)) }
    }

    @Test
    fun isPdfPath_goesByExtension() {
        assertTrue(ReceiptFiles.isPdfPath("receipts/receipt_1.pdf"))
        assertTrue(ReceiptFiles.isPdfPath("receipts/receipt_1.PDF"))
        assertFalse(ReceiptFiles.isPdfPath("receipts/receipt_1.jpg"))
        assertFalse(ReceiptFiles.isPdfPath("receipts/receipt_pdf"))
    }
}
