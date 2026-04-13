package com.pennywiseai.tracker.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

    private val cameraTempDir: File
        get() = File(context.cacheDir, "camera_temp").also { it.mkdirs() }

    suspend fun saveReceipt(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
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
}
