package com.pennywiseai.tracker.data.receipt

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The PdfRenderer half of the picked-PDF check, which a JVM test can't reach: a
 * damaged or password-protected file is turned away, a plain one gets through.
 * All files are synthetic, built here.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptPdfCheckTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = ReceiptManager(context)
    private val dir = File(context.cacheDir, "receipt_check_test").also { it.mkdirs() }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun pdf(name: String, bytes: ByteArray): Uri = Uri.fromFile(File(dir, name).apply { writeBytes(bytes) })

    @Test
    fun aPlainOnePagePdfIsAccepted() = runBlocking {
        assertNull(manager.checkReceipt(pdf("plain.pdf", onePagePdf())))
    }

    @Test
    fun aTruncatedPdfIsUnreadable() = runBlocking {
        val whole = onePagePdf()
        assertEquals(
            ReceiptManager.PdfProblem.UNREADABLE,
            manager.checkReceipt(pdf("truncated.pdf", whole.copyOf(whole.size / 4)))
        )
    }

    @Test
    fun aPasswordProtectedPdfIsUnreadable() = runBlocking {
        assertEquals(ReceiptManager.PdfProblem.UNREADABLE, manager.checkReceipt(pdf("locked.pdf", onePagePdf(encrypted = true))))
    }

    @Test
    fun aPdfOverTheLimitIsTooLarge() = runBlocking {
        val big = "%PDF-1.4\n".toByteArray() + ByteArray(ReceiptFiles.MAX_RECEIPT_BYTES.toInt())
        assertEquals(ReceiptManager.PdfProblem.TOO_LARGE, manager.checkReceipt(pdf("big.pdf", big)))
    }

    /**
     * A minimal one-page PDF with a correct xref. [encrypted] adds a Standard security
     * handler whose keys match no password, so it opens only with one nobody has.
     */
    private fun onePagePdf(encrypted: Boolean = false): ByteArray {
        val objects = mutableListOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>"
        )
        val hex32 = "AB".repeat(32)
        if (encrypted) objects += "<< /Filter /Standard /V 1 /R 2 /O <$hex32> /U <$hex32> /P -4 >>"

        val out = StringBuilder("%PDF-1.4\n")
        val offsets = objects.mapIndexed { i, body ->
            out.length.also { out.append("${i + 1} 0 obj\n$body\nendobj\n") }
        }
        val xref = out.length
        out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", it)) }
        val encrypt = if (encrypted) " /Encrypt ${objects.size} 0 R /ID [<$hex32> <$hex32>]" else ""
        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R$encrypt >>\nstartxref\n$xref\n%%EOF\n")
        return out.toString().toByteArray(Charsets.US_ASCII)
    }
}
