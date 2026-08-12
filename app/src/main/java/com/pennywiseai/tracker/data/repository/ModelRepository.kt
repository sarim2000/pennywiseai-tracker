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

    // Length of the file last confirmed to match the pinned hash, so we don't
    // re-hash 1.5GB on every message once verified within this process.
    @Volatile
    private var lastVerifiedLength: Long = -1L
    
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
     * by streaming its SHA-256. Returns true only when the file exists and matches
     * (or when no hash is pinned, in which case verification is intentionally
     * disabled and a warning is logged). Result is cached per process by file length.
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
            return@withContext false
        }

        val length = file.length()
        if (length > 0 && length == lastVerifiedLength) {
            return@withContext true
        }

        val actual = try {
            file.sha256Hex()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash model file", e)
            return@withContext false
        }

        val matches = actual == expected
        if (matches) {
            lastVerifiedLength = length
            Log.d(TAG, "Model integrity verified (sha256=$actual)")
        } else {
            lastVerifiedLength = -1L
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

    fun updateModelState(state: ModelState) {
        Log.d("ModelRepository", "Updating model state from ${_modelState.value} to $state")
        _modelState.value = state
    }
    
    fun checkModelState() {
        val newState = if (isModelDownloaded()) {
            ModelState.READY
        } else {
            ModelState.NOT_DOWNLOADED
        }
        Log.d("ModelRepository", "checkModelState: setting state to $newState")
        _modelState.value = newState
    }
    
    fun deleteModel(): Boolean {
        val modelFile = getModelFile()
        lastVerifiedLength = -1L
        return if (modelFile.exists()) {
            val deleted = modelFile.delete()
            if (deleted) {
                _modelState.value = ModelState.NOT_DOWNLOADED
            }
            deleted
        } else {
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