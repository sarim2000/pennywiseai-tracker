package com.pennywiseai.tracker.data.receipt

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Plain file rules for receipts, kept free of Android so they run in JVM tests. */
object ReceiptFiles {

    /** The largest PDF a transaction can carry, and the largest receipt a backup holds. */
    const val MAX_RECEIPT_BYTES: Long = 5L * 1024 * 1024

    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)

    /**
     * Copies [input] to [output] and stops as soon as more than [maxBytes] arrive, so
     * a provider that misreports a file's size can't slip a bigger one through.
     * Returns false when the input was too large; [output] then holds a partial copy.
     */
    fun copyCapped(input: InputStream, output: OutputStream, maxBytes: Long = MAX_RECEIPT_BYTES): Boolean {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return true
            total += read
            if (total > maxBytes) return false
            output.write(buffer, 0, read)
        }
    }

    /** True when [file] starts like a PDF does (`%PDF-`), whatever its name or MIME type says. */
    fun hasPdfMagic(file: File): Boolean {
        if (file.length() < PDF_MAGIC.size) return false
        val head = ByteArray(PDF_MAGIC.size)
        val read = file.inputStream().use { it.read(head) }
        return read == PDF_MAGIC.size && head.contentEquals(PDF_MAGIC)
    }

    /** A stored receipt path names a PDF; every other receipt is a JPEG photo. */
    fun isPdfPath(path: String): Boolean = path.endsWith(".pdf", ignoreCase = true)
}
