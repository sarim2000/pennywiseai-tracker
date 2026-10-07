package com.pennywiseai.tracker.data.manager

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Writing failed because the disk is full; retrying can't help. */
internal class DiskFullException(cause: IOException) : IOException(cause.message, cause)

/**
 * One HTTP attempt at filling [part] up to [expectedBytes], continuing from
 * whatever it already holds via a `Range` request. Throws [IOException] when the
 * attempt doesn't finish (the caller retries), [DiskFullException] when writing
 * hits a full disk, and returns once [part] is complete.
 */
internal class ResumableFetch(
    private val url: String,
    private val expectedBytes: Long,
    private val timeoutMs: Int = 30_000,
    private val onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
) {
    suspend fun into(part: File) {
        val offset = part.length()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val append = when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false // server ignored the range: start over
                HTTP_RANGE_NOT_SATISFIABLE ->
                    if (offset >= expectedBytes) return
                    else { part.delete(); throw IOException("Range not satisfiable at $offset") }
                else -> throw IOException("HTTP $code")
            }
            // A cancelled caller may only now be getting its response: never touch
            // (let alone truncate) the file once cancelled.
            coroutineContext.ensureActive()
            val start = if (append) offset else 0L
            val total = connection.contentLengthLong.takeIf { it > 0 }?.let { start + it } ?: expectedBytes
            var downloaded = start
            var lastPublished = 0L
            FileOutputStream(part, append).use { out ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        try {
                            out.write(buffer, 0, read)
                        } catch (e: IOException) {
                            if (e.isDiskFull()) throw DiskFullException(e) else throw e
                        }
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastPublished >= PUBLISH_INTERVAL_MS) {
                            lastPublished = now
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            onProgress(downloaded, total)
            if (downloaded < total) throw IOException("Connection closed at $downloaded of $total bytes")
        } finally {
            connection.disconnect()
        }
    }

    private fun IOException.isDiskFull(): Boolean =
        message?.let { it.contains("ENOSPC") || it.contains("No space left", ignoreCase = true) } == true

    private companion object {
        const val BUFFER_BYTES = 256 * 1024
        const val PUBLISH_INTERVAL_MS = 500L
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
