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
     * process) and returns its file URI. It is decoded, turned upright, scaled to fill
     * [MAX_PX] and re-encoded, which also drops its EXIF (location included). Each pick
     * gets its own file name, so the image loader can't serve the previous photo from
     * its cache. Null, with a toast, when the photo can't be decoded or written.
     */
    suspend fun import(context: Context, source: Uri): String? {
        val request = ImageRequest.Builder(context)
            .data(source)
            .size(MAX_PX)
            .scale(Scale.FILL)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        val uri = bitmap?.let { save(it, context.filesDir) }
        if (uri == null) Toast.makeText(context, R.string.settings_profile_photo_failed, Toast.LENGTH_SHORT).show()
        return uri
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
