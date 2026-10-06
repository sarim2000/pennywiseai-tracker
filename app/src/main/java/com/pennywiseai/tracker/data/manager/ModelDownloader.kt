package com.pennywiseai.tracker.data.manager

import android.app.DownloadManager
import android.content.Context
import android.util.Log
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.repository.ModelState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.ModelRepository
import com.pennywiseai.tracker.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Downloads the on-device chat model over HTTPS, for Chat and Settings alike.
 *
 * This replaces Android's DownloadManager, which holds any download above the
 * device's "recommended max over mobile" size until Wi-Fi — and the model is
 * 1.5 GB, so on mobile data it sat paused forever however the request was set
 * up. Here the bytes stream into a `.part` file and continue with an HTTP Range
 * request after a network drop or the process being killed; nothing is
 * trusted until [ModelRepository.finalizeDownloadedModel] has checked the
 * pinned SHA-256.
 *
 * ponytail: runs in the app process, not a foreground service, so it pauses if
 * Android kills the app in the background and resumes from the `.part` when
 * Chat or Settings is next opened. A foreground service (dataSync) would keep
 * it going, at the cost of a Play Console foreground-service declaration.
 */
@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    enum class Failure { NO_SPACE, NETWORK, INTEGRITY }

    data class Progress(
        val downloadedBytes: Long = 0,
        val totalBytes: Long = Constants.ModelDownload.MODEL_SIZE_BYTES,
        val failure: Failure? = null,
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    private fun partFile() = File(modelRepository.getModelFile().path + PART_SUFFIX)

    /** Starts, or continues from the `.part` file. A no-op while already running. */
    fun start() {
        if (isRunning) return
        job = scope.launch(Dispatchers.IO) { download() }
    }

    /** Picks up a download the process was killed in the middle of; true if one is running. */
    fun resumeIfInterrupted(): Boolean {
        if (!isRunning && partFile().exists()) start()
        return isRunning
    }

    fun cancel() {
        job?.cancel()
        job = null
        partFile().delete()
        _progress.value = Progress()
        modelRepository.updateModelState(ModelState.NOT_DOWNLOADED)
    }

    private suspend fun download() {
        dropLegacyDownloadManagerJob()
        val part = partFile()
        if (!part.exists() && context.filesDir.usableSpace < Constants.ModelDownload.REQUIRED_SPACE_BYTES) {
            fail(Failure.NO_SPACE)
            return
        }
        modelRepository.updateModelState(ModelState.DOWNLOADING)
        _progress.value = Progress(downloadedBytes = part.length())

        var attempt = 0
        while (true) {
            val before = part.length()
            try {
                fetchInto(part)
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Progress since the last failure earns a fresh set of retries.
                if (part.length() > before) attempt = 0
                if (++attempt > MAX_ATTEMPTS) {
                    Log.w(TAG, "Model download failed", e)
                    fail(Failure.NETWORK)
                    return
                }
                delay(RETRY_DELAY_MS * attempt)
            }
        }

        val target = modelRepository.getModelFile()
        target.delete()
        if (!part.renameTo(target)) {
            fail(Failure.NETWORK)
            return
        }
        modelRepository.updateModelState(ModelState.LOADING)
        if (!modelRepository.finalizeDownloadedModel()) {
            modelRepository.updateModelState(ModelState.ERROR)
            _progress.value = Progress(failure = Failure.INTEGRITY)
        }
    }

    private suspend fun fetchInto(part: File) {
        val offset = part.length()
        val connection = URL(Constants.ModelDownload.MODEL_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val append = when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false // server ignored the range: start over
                HTTP_RANGE_NOT_SATISFIABLE ->
                    if (offset >= Constants.ModelDownload.MODEL_SIZE_BYTES) return
                    else { part.delete(); throw IOException("Range not satisfiable at $offset") }
                else -> throw IOException("HTTP $code")
            }
            val start = if (append) offset else 0L
            val total = (start + connection.contentLengthLong).takeIf { connection.contentLengthLong > 0 }
                ?: Constants.ModelDownload.MODEL_SIZE_BYTES
            var downloaded = start
            var lastPublished = 0L
            FileOutputStream(part, append).use { out ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastPublished >= PUBLISH_INTERVAL_MS) {
                            lastPublished = now
                            _progress.value = Progress(downloaded, total)
                        }
                    }
                }
            }
            _progress.value = Progress(downloaded, total)
            if (downloaded < total) throw IOException("Connection closed at $downloaded of $total bytes")
        } finally {
            connection.disconnect()
        }
    }

    private fun fail(failure: Failure) {
        _progress.value = _progress.value.copy(failure = failure)
        modelRepository.updateModelState(ModelState.NOT_DOWNLOADED)
    }

    /** Older versions queued the model with DownloadManager; drop such a job so two downloads don't race. */
    private suspend fun dropLegacyDownloadManagerJob() {
        val id = userPreferencesRepository.getActiveDownloadId() ?: return
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
        userPreferencesRepository.clearActiveDownloadId()
    }

    private companion object {
        const val TAG = "ModelDownloader"
        const val PART_SUFFIX = ".part"
        const val MAX_ATTEMPTS = 5
        const val RETRY_DELAY_MS = 3_000L
        const val TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 256 * 1024
        const val PUBLISH_INTERVAL_MS = 500L
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
