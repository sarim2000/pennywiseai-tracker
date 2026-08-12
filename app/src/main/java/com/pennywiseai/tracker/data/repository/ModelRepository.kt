package com.pennywiseai.tracker.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.pennywiseai.tracker.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _modelState = MutableStateFlow(ModelState.NOT_DOWNLOADED)
    val modelState: Flow<ModelState> = _modelState.asStateFlow()

    fun getModelFile(): File {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), Constants.ModelDownload.MODEL_FILE_NAME)
        Log.d("ModelRepository", "Model file path: ${file.absolutePath}")
        return file
    }
    
    fun isModelDownloaded(): Boolean {
        val modelFile = getModelFile()
        val exists = modelFile.exists()
        val size = if (exists) modelFile.length() else 0
        val expectedSize = Constants.ModelDownload.MODEL_SIZE_BYTES
        // Allow 5% variance in file size as download sizes can vary
        // But also accept any file over 2GB as models can vary in size
        val minSize = minOf((expectedSize * 0.95).toLong(), 2L * 1024L * 1024L * 1024L) // 95% of expected or 2GB minimum
        val isDownloaded = exists && size >= minSize
        
        Log.d("ModelRepository", "Checking model: exists=$exists, size=$size bytes (${size/1024/1024}MB), expectedSize=$expectedSize, minSize=$minSize, isDownloaded=$isDownloaded")
        return isDownloaded
    }
    
    /**
     * Verifies the downloaded model against the pinned [Constants.ModelDownload.MODEL_SHA256]
     * by streaming its full SHA-256 — every call actually re-hashes the bytes on disk.
     *
     * This is the load-time security boundary, so it must NOT be short-circuited by a
     * cheap proxy (size, mtime, a cached "already verified" flag): the model has a
     * fixed length, so a same-length replacement would otherwise pass without ever
     * being read. On a match it drops a verification marker that [isModelVerified] may
     * read for UI state only — never as a substitute for this hash.
     *
     * Returns true when the bytes match, or when no hash is pinned (verification
     * intentionally disabled, logged).
     */
    suspend fun verifyModelIntegrity(): Boolean = withContext(Dispatchers.IO) {
        val expected = Constants.ModelDownload.MODEL_SHA256.trim().lowercase()
        if (expected.isEmpty()) {
            Log.w(TAG, "MODEL_SHA256 is blank — model integrity verification is DISABLED")
            return@withContext true
        }

        val file = getModelFile()
        if (!file.exists()) {
            Log.w(TAG, "verifyModelIntegrity: model file missing")
            clearVerifiedMarker()
            return@withContext false
        }

        val actual = try {
            file.sha256Hex()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash model file", e)
            return@withContext false
        }

        val matches = actual == expected
        if (matches) {
            writeVerifiedMarker(expected)
            Log.d(TAG, "Model integrity verified (sha256=$actual)")
        } else {
            clearVerifiedMarker()
            Log.e(TAG, "Model integrity check FAILED: expected=$expected actual=$actual")
        }
        matches
    }

    private fun File.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifiedMarkerFile(): File =
        File(getModelFile().parentFile, Constants.ModelDownload.MODEL_FILE_NAME + ".verified")

    private fun writeVerifiedMarker(hash: String) {
        try {
            verifiedMarkerFile().writeText(hash)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write verification marker", e)
        }
    }

    private fun clearVerifiedMarker() {
        try {
            val marker = verifiedMarkerFile()
            if (marker.exists()) marker.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear verification marker", e)
        }
    }

    /**
     * Cheap check (no hashing) of whether the model on disk has previously passed
     * [verifyModelIntegrity]. For driving UI state ONLY — the load path always
     * re-hashes via [verifyModelIntegrity] and never trusts this marker.
     */
    fun isModelVerified(): Boolean {
        if (!isModelDownloaded()) return false
        val expected = Constants.ModelDownload.MODEL_SHA256.trim().lowercase()
        if (expected.isEmpty()) return true // verification disabled → any downloaded file counts
        val marker = verifiedMarkerFile()
        return try {
            marker.exists() && marker.readText().trim().lowercase() == expected
        } catch (e: Exception) {
            false
        }
    }

    fun updateModelState(state: ModelState) {
        Log.d("ModelRepository", "Updating model state from ${_modelState.value} to $state")
        _modelState.value = state
    }

    /**
     * Resolves the model state on app open. A previously-verified file goes straight to
     * READY; a present-but-unverified file (a download whose post-download verification
     * was interrupted, or a model carried over from an older build) is re-hashed before
     * it is trusted, and deleted if it fails. Never reports READY for an unverified file.
     */
    suspend fun refreshModelState() {
        when {
            isModelVerified() -> updateModelState(ModelState.READY)
            isModelDownloaded() -> {
                updateModelState(ModelState.LOADING)
                if (verifyModelIntegrity()) {
                    updateModelState(ModelState.READY)
                } else {
                    Log.e(TAG, "Present model failed verification on open — deleting")
                    deleteModel()
                }
            }
            else -> updateModelState(ModelState.NOT_DOWNLOADED)
        }
    }

    /**
     * Verifies a freshly-completed download before trusting it. Promotes to READY on a
     * hash match, otherwise deletes the file and reports failure. Returns true iff the
     * bytes matched the pinned hash.
     */
    suspend fun finalizeDownloadedModel(): Boolean {
        return if (verifyModelIntegrity()) {
            updateModelState(ModelState.READY)
            true
        } else {
            Log.e(TAG, "Downloaded model failed verification — deleting")
            deleteModel()
            false
        }
    }

    fun deleteModel(): Boolean {
        val modelFile = getModelFile()
        clearVerifiedMarker()
        return if (modelFile.exists()) {
            val deleted = modelFile.delete()
            if (deleted) {
                _modelState.value = ModelState.NOT_DOWNLOADED
            }
            deleted
        } else {
            _modelState.value = ModelState.NOT_DOWNLOADED
            false
        }
    }

    private companion object {
        const val TAG = "ModelRepository"
    }
}

enum class ModelState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    LOADING,
    ERROR
}