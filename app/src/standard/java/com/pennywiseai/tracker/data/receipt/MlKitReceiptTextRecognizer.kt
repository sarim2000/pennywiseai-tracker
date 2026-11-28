package com.pennywiseai.tracker.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * ML Kit Text Recognition v2 with the Latin-script model bundled in the APK, so it
 * works offline: the photo and its text stay on the device. ML Kit itself still sends
 * Google usage and performance metrics (no images or text). Latin only: Arabic or
 * Devanagari text isn't read. The photo is decoded at most [MAX_SIDE] px on its long
 * side, and its text is never logged.
 */
class MlKitReceiptTextRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) : ReceiptTextRecognizer {
    override val isAvailable: Boolean = true

    // Off the main thread: creating the client loads the model from disk.
    override suspend fun recognize(uri: Uri): List<String>? = withContext(Dispatchers.Default) {
        try {
            val (bitmap, rotation) = withContext(Dispatchers.IO) { decode(uri) } ?: return@withContext null
            rows(read(bitmap, rotation).await())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Receipt text recognition failed", e)
            null
        }
    }

    /**
     * ML Kit's reading of [bitmap]. The task keeps reading the bitmap after a cancelled
     * caller stops waiting, so the bitmap and the client are released only once it's done.
     */
    private fun read(bitmap: Bitmap, rotation: Int): Task<Text> {
        val client = try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        } catch (e: Exception) {
            bitmap.recycle()
            throw e
        }
        val task = try {
            client.process(InputImage.fromBitmap(bitmap, rotation))
        } catch (e: Exception) {
            client.close()
            bitmap.recycle()
            throw e
        }
        task.addOnCompleteListener {
            client.close()
            bitmap.recycle()
        }
        return task
    }

    /** The photo, scaled down by a power of two to fit [MAX_SIDE], and how far it's turned. */
    private fun decode(uri: Uri): Pair<Bitmap, Int>? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null; it just fills in outWidth/outHeight.
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "Receipt photo couldn't be decoded")
            return null
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (bitmap == null) {
            Log.w(TAG, "Receipt photo couldn't be decoded")
            return null
        }
        // A camera photo is often stored sideways with its turn noted in EXIF.
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull()
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        return bitmap to rotation
    }

    /**
     * ML Kit groups a receipt's labels and figures into separate blocks, so "TOTAL" and
     * "9.45" come back apart. Lines at the same height are put back together as one row,
     * left to right, and the rows read top to bottom.
     */
    private fun rows(text: Text): List<String> {
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line -> line.boundingBox?.let { line.text to it } }
        val rows = mutableListOf<MutableList<Pair<String, Rect>>>()
        for (line in lines.sortedBy { it.second.centerY() }) {
            val row = rows.lastOrNull()
            val last = row?.last()?.second
            if (last != null && abs(line.second.centerY() - last.centerY()) < minOf(line.second.height(), last.height()) / 2) {
                row.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }
        return rows.map { row -> row.sortedBy { it.second.left }.joinToString("  ") { it.first } }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private companion object {
        const val TAG = "ReceiptOcr"
        const val MAX_SIDE = 2048
    }
}
