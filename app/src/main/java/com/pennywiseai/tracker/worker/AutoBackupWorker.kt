package com.pennywiseai.tracker.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.data.backup.BackupExporter
import com.pennywiseai.tracker.data.backup.ExportResult
import com.pennywiseai.tracker.data.backup.ExportUriResult
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val backupExporter: BackupExporter
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val WORK_NAME = "auto_backup_work"
        private const val BACKUPS_DIR = "backups"
        private const val MAX_BACKUPS = 3

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = userPreferencesRepository.userPreferences.first()
        if (!prefs.isAutoBackupEnabled) {
            Log.d(TAG, "Auto-backup is disabled. Skipping.")
            return Result.success()
        }

        val password = userPreferencesRepository.getBackupPassword()
        if (password.isNullOrBlank()) {
            Log.d(TAG, "No backup password configured. Skipping.")
            return Result.success()
        }

        val customUri = prefs.backupDirectoryUri
        Log.d(TAG, "Starting automated encrypted backup... Custom SAF URI: $customUri")
        try {
            if (!customUri.isNullOrBlank()) {
                val result = backupExporter.exportToUri(customUri, password.toCharArray())
                if (result is ExportUriResult.Success) {
                    Log.d(TAG, "Auto-backup successfully exported to custom SAF destination: ${result.uri}")
                    cleanUpOldDocumentBackups(customUri)
                    return Result.success()
                } else if (result is ExportUriResult.Error) {
                    Log.w(TAG, "Auto-backup to custom SAF directory failed: ${result.message}. Falling back to default storage.")
                }
            }

            // Fallback to local cache export
            val result = backupExporter.exportBackup(password.toCharArray())
            if (result is ExportResult.Success) {
                val cacheFile = result.file
                
                // Copy to external files dir (or fallback to filesDir if external is null/not mounted)
                val targetDir = appContext.getExternalFilesDir(BACKUPS_DIR) ?: File(appContext.filesDir, BACKUPS_DIR)
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                
                val targetFile = File(targetDir, cacheFile.name)
                cacheFile.copyTo(targetFile, overwrite = true)
                Log.d(TAG, "Auto-backup successfully exported to: ${targetFile.absolutePath}")
                
                // Clean up old backups (keep last 3)
                cleanUpOldBackups(targetDir)
                
                // Clean up cache file
                cacheFile.delete()
            } else if (result is ExportResult.Error) {
                Log.e(TAG, "Auto-backup export failed: ${result.message}")
                return Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-backup encountered unexpected error", e)
            return Result.failure()
        }

        return Result.success()
    }

    private fun cleanUpOldBackups(directory: File) {
        val backupFiles = directory.listFiles { file -> 
            file.isFile && file.name.endsWith(".pennywisebackup") 
        } ?: return
        
        if (backupFiles.size > MAX_BACKUPS) {
            // Sort by modified time, oldest first
            val sortedFiles = backupFiles.sortedBy { it.lastModified() }
            val filesToDelete = sortedFiles.size - MAX_BACKUPS
            for (i in 0 until filesToDelete) {
                val file = sortedFiles[i]
                if (file.delete()) {
                    Log.d(TAG, "Deleted old auto-backup: ${file.name}")
                }
            }
        }
    }

    private fun cleanUpOldDocumentBackups(treeUriStr: String) {
        if (treeUriStr.isBlank()) return
        try {
            val treeUri = Uri.parse(treeUriStr)
            val directory = DocumentFile.fromTreeUri(appContext, treeUri) ?: return
            val backupFiles = directory.listFiles().filter { docFile ->
                docFile.isFile && docFile.name?.endsWith(".pennywisebackup") == true
            }
            if (backupFiles.size > MAX_BACKUPS) {
                val sortedFiles = backupFiles.sortedBy { it.lastModified() }
                val filesToDelete = sortedFiles.size - MAX_BACKUPS
                for (i in 0 until filesToDelete) {
                    val file = sortedFiles[i]
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old auto-backup document: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up old SAF document backups", e)
        }
    }
}
