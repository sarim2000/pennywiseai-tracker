package com.pennywiseai.tracker.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import com.pennywiseai.tracker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The profile photos the user picks, kept as small JPEGs in app storage. */
object ProfilePhotos {
    private const val PREFIX = "profile_photo_"

    /** Where onboarding used to save its pick, before it shared [import]. */
    private const val LEGACY_NAME = "profile_image.jpg"

    /** Side of the stored square-ish photo; the biggest avatar is 96dp. */
    private const val MAX_PX = 512

    /**
     * Copies a picked photo into app storage (the picker's grant doesn't outlive the
     * process) and returns its file URI. It is decoded, turned upright, scaled so neither
     * side exceeds [MAX_PX], and re-encoded, which also drops its EXIF (location included).
     * Fit subsamples the long side during decode, so a very tall photo is not allocated in
     * full. [fitWithin] still bounds a decode that comes back slightly over. Each pick gets
     * its own file name, so the image loader can't serve the previous photo from its cache.
     * Null, with a toast, when the photo can't be decoded or written.
     */
    suspend fun import(context: Context, source: Uri): String? {
        val request = ImageRequest.Builder(context)
            .data(source)
            .size(MAX_PX)
            .scale(Scale.FIT)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        val fitted = bitmap?.let { fitWithin(it, MAX_PX) }
        val uri = fitted?.let { save(it, context.filesDir) }
        if (bitmap != null && fitted != null && fitted !== bitmap) bitmap.recycle()
        if (uri == null) Toast.makeText(context, R.string.settings_profile_photo_failed, Toast.LENGTH_SHORT).show()
        return uri
    }

    /**
     * Scales [bitmap] so neither side exceeds [maxPx]. A photo already inside the bound,
     * including a decode Coil has fitted to about [maxPx], is returned as it is.
     */
    private fun fitWithin(bitmap: Bitmap, maxPx: Int): Bitmap {
        val (width, height) = boundedSides(bitmap.width, bitmap.height, maxPx)
        if (width == bitmap.width && height == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private suspend fun save(bitmap: Bitmap, dir: File): String? = withContext(Dispatchers.IO) {
        val file = File(dir, "$PREFIX${System.currentTimeMillis()}.jpg")
        val tmp = File(dir, "${file.name}.tmp")
        try {
            val written = tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (written && tmp.renameTo(file)) file.toUri().toString() else null
        } catch (_: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

    /**
     * Width and height that keep [width] and [height]'s aspect ratio with neither side
     * above [maxPx]. A zero side, or a photo already inside the bound, is unchanged.
     */
    internal fun boundedSides(width: Int, height: Int, maxPx: Int): Pair<Int, Int> {
        val longSide = maxOf(width, height)
        if (width <= 0 || height <= 0 || longSide <= maxPx) return width to height
        val fittedWidth = (width.toLong() * maxPx / longSide).toInt().coerceAtLeast(1)
        val fittedHeight = (height.toLong() * maxPx / longSide).toInt().coerceAtLeast(1)
        return fittedWidth to fittedHeight
    }

    /**
     * Deletes every profile photo in [dir] but [keepUri]'s, so replaced and abandoned
     * picks don't pile up. Does nothing while the current picture is unknown (null).
     */
    fun sweep(dir: File, keepUri: String?) {
        if (keepUri == null) return
        val keep = keepUri.substringAfterLast('/')
        dir.listFiles { f -> f.name == LEGACY_NAME || f.name.startsWith(PREFIX) }
            ?.filter { it.name != keep }
            ?.forEach { it.delete() }
    }
}
