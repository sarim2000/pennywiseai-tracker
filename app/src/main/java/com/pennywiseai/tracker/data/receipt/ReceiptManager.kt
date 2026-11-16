package com.pennywiseai.tracker.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val receiptsDir: File
        get() = File(context.filesDir, "receipts").also { it.mkdirs() }

    private val pdfStagingDir: File
        get() = File(context.cacheDir, "receipt_staging").also { it.mkdirs() }

    private val cameraTempDir: File
        get() = File(context.cacheDir, "camera_temp").also { it.mkdirs() }

    /** Why a picked PDF can't be attached. */
    enum class PdfProblem { TOO_LARGE, UNREADABLE }

    /**
     * Checks a picked receipt before it's shown as the pending one, so the user hears
     * about a too-large, damaged or password-protected PDF straight away. A photo
     * isn't checked. Null = fine.
     */
    suspend fun checkReceipt(sourceUri: Uri): PdfProblem? = withContext(Dispatchers.IO) {
        if (!isPdf(context, sourceUri)) return@withContext null
        // ponytail: savePdf copies and opens it once more at Save; a pick is rare enough not to cache.
        val staged = try {
            File.createTempFile("check", ".pdf", pdfStagingDir)
        } catch (_: Exception) {
            return@withContext PdfProblem.UNREADABLE
        }
        try {
            copyPdf(sourceUri, staged)
        } finally {
            staged.delete()
        }
    }

    /**
     * Saves the receipt and returns its path relative to `filesDir`, or null when the
     * file can't be used. A photo is scaled and re-encoded as JPEG; a PDF is copied
     * byte for byte, after the same checks as [checkReceipt].
     */
    suspend fun saveReceipt(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        if (isPdf(context, sourceUri)) return@withContext savePdf(sourceUri)
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext null

            // Decode bounds first to calculate sample size
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val tempBytes = inputStream.use { it.readBytes() }
            BitmapFactory.decodeByteArray(tempBytes, 0, tempBytes.size, options)

            val maxDimension = 1920
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxDimension)

            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(tempBytes, 0, tempBytes.size, decodeOptions)
                ?: return@withContext null

            val fileName = "receipt_${System.currentTimeMillis()}.jpg"
            val outputFile = File(receiptsDir, fileName)

            outputFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()

            "receipts/$fileName"
        } catch (_: Exception) {
            null
        }
    }

    private fun savePdf(sourceUri: Uri): String? {
        val staged = try {
            File.createTempFile("save", ".pdf", pdfStagingDir)
        } catch (_: Exception) {
            return null
        }
        try {
            if (copyPdf(sourceUri, staged) != null) return null
            val outputFile = File(receiptsDir, "receipt_${System.currentTimeMillis()}.pdf")
            // Same filesystem, so a rename; copy if it ever isn't.
            if (!staged.renameTo(outputFile)) staged.copyTo(outputFile, overwrite = true)
            return "receipts/${outputFile.name}"
        } catch (_: Exception) {
            return null
        } finally {
            staged.delete() // no-op after a successful rename
        }
    }

    /**
     * Copies [sourceUri] into [target], capped at [ReceiptFiles.MAX_RECEIPT_BYTES] while
     * copying (a reported size can lie), then checks it really is a PDF that opens:
     * the magic bytes first, then [PdfRenderer], which rejects damaged and
     * password-protected files.
     */
    private fun copyPdf(sourceUri: Uri, target: File): PdfProblem? = try {
        val fits = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { ReceiptFiles.copyCapped(input, it) }
        }
        when {
            fits == null -> PdfProblem.UNREADABLE
            !fits -> PdfProblem.TOO_LARGE
            !ReceiptFiles.hasPdfMagic(target) -> PdfProblem.UNREADABLE
            !opensAsPdf(target) -> PdfProblem.UNREADABLE
            else -> null
        }
    } catch (_: Exception) {
        PdfProblem.UNREADABLE
    }

    private fun opensAsPdf(file: File): Boolean = try {
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount > 0 }
    } catch (_: Exception) {
        // IOException for a damaged file, SecurityException for a password-protected one.
        false
    }

    fun deleteReceipt(receiptPath: String) {
        val file = File(context.filesDir, receiptPath)
        if (file.exists()) file.delete()
    }

    fun getReceiptFile(receiptPath: String): File {
        return File(context.filesDir, receiptPath)
    }

    fun createCameraUri(): Uri {
        val file = File(cameraTempDir, "camera_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun deleteAllReceipts() {
        if (receiptsDir.exists()) receiptsDir.deleteRecursively()
    }

    /** A FileProvider link to a stored receipt, for handing it to another app with a read grant. */
    fun shareableUri(receiptPath: String): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        getReceiptFile(receiptPath)
    )

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        if (width > maxDimension || height > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / sampleSize) >= maxDimension || (halfHeight / sampleSize) >= maxDimension) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    companion object {
        const val PDF_MIME = "application/pdf"

        /** A stored receipt is a `file:` Uri named `.pdf`; a picked one says so in its MIME type. */
        fun isPdf(context: Context, uri: Uri): Boolean =
            if (uri.scheme == "file") ReceiptFiles.isPdfPath(uri.path.orEmpty())
            else context.contentResolver.getType(uri) == PDF_MIME
    }
}
