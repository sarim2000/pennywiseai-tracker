package com.pennywiseai.tracker.data.manager

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.ModelRepository
import com.pennywiseai.tracker.data.repository.ModelState
import com.pennywiseai.tracker.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the on-device chat model over HTTPS, for Chat and Settings alike.
 *
 * This replaces Android's DownloadManager, which holds any download above the
 * device's "recommended max over mobile" size until Wi-Fi — and the model is
 * 1.5 GB, so on mobile data it sat paused forever however the request was set
 * up. Here the bytes stream into a `.part` file and continue with an HTTP Range
 * request after a network drop or the process being killed ([ResumableFetch]);
 * nothing is trusted until [ModelRepository.finalizeDownloadedModel] has checked
 * the pinned SHA-256. Like the old request, it never downloads while roaming.
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
    enum class Failure { NO_SPACE, NETWORK, INTEGRITY, ROAMING }

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

    /**
     * Starts, or continues from the `.part` file. A no-op while already running.
     * After a cancel, the new download waits for the old one to finish unwinding
     * (it deletes its `.part` on the way out), so the two never share the file.
     */
    fun start() {
        if (isRunning) return
        val previous = job
        job = scope.launch(Dispatchers.IO) {
            previous?.join()
            download()
        }
    }

    /** Picks up a download the process was killed in the middle of; true if one is running. */
    fun resumeIfInterrupted(): Boolean {
        if (!isRunning && partFile().exists()) start()
        return isRunning
    }

    /**
     * Older versions queued the model with DownloadManager, which writes the final
     * file name directly. A finished one is left for the normal verification; an
     * unfinished one is cancelled, its partial file deleted, and the download
     * restarted here. Returns true if a download is now running.
     */
    suspend fun adoptLegacyDownload(): Boolean {
        val id = userPreferencesRepository.getActiveDownloadId() ?: return false
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val finished = manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            cursor.moveToFirst() &&
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        } ?: false
        userPreferencesRepository.clearActiveDownloadId()
        if (finished) return false
        manager.remove(id)
        modelRepository.getModelFile().delete()
        start()
        return true
    }

    /** Stops the download and discards what it fetched. */
    fun cancel() {
        val running = job?.takeIf { it.isActive } ?: return
        running.cancel(DiscardDownload())
        _progress.value = Progress()
        modelRepository.updateModelState(ModelState.NOT_DOWNLOADED)
    }

    private suspend fun download() {
        val part = partFile()
        try {
            if (!hasSpaceFor(part)) {
                fail(Failure.NO_SPACE)
                return
            }
            modelRepository.updateModelState(ModelState.DOWNLOADING)
            _progress.value = Progress(downloadedBytes = part.length())
            val fetch = ResumableFetch(
                url = Constants.ModelDownload.MODEL_URL,
                expectedBytes = Constants.ModelDownload.MODEL_SIZE_BYTES,
                onProgress = { downloaded, total -> _progress.value = Progress(downloaded, total) },
            )

            var attempt = 0
            while (true) {
                if (isRoaming()) {
                    fail(Failure.ROAMING)
                    return
                }
                val before = part.length()
                try {
                    fetch.into(part)
                    break
                } catch (e: DiskFullException) {
                    fail(Failure.NO_SPACE)
                    return
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
        } catch (e: CancellationException) {
            if (e is DiscardDownload) withContext(NonCancellable) { part.delete() }
            throw e
        }
    }

    /** Room for what's still to come, with the same 2× headroom a fresh download needs. */
    private fun hasSpaceFor(part: File): Boolean {
        val dir = part.parentFile ?: return true
        dir.mkdirs()
        return dir.usableSpace >= Constants.ModelDownload.REQUIRED_SPACE_BYTES - part.length()
    }

    private fun isRoaming(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // NET_CAPABILITY_NOT_ROAMING only exists from Android 9; before that every
        // network lacks it, so use the older per-network roaming flag there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            return cm.activeNetworkInfo?.isRoaming == true
        }
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
    }

    private fun fail(failure: Failure) {
        _progress.value = _progress.value.copy(failure = failure)
        modelRepository.updateModelState(ModelState.NOT_DOWNLOADED)
    }

    /** Cancellation cause meaning "the user cancelled: delete the partial file". */
    private class DiscardDownload : CancellationException("Model download cancelled")

    private companion object {
        const val TAG = "ModelDownloader"
        const val PART_SUFFIX = ".part"
        const val MAX_ATTEMPTS = 5
        const val RETRY_DELAY_MS = 3_000L
    }
}
