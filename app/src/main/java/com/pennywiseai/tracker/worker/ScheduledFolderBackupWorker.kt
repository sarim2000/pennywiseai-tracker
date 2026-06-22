package com.pennywiseai.tracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.backup.folder.FolderBackupWriter
import com.pennywiseai.tracker.data.backup.BackupExporter
import com.pennywiseai.tracker.data.backup.ExportBytesResult
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScheduledFolderBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupExporter: BackupExporter,
    private val folderBackupWriter: FolderBackupWriter,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!userPreferencesRepository.isScheduledFolderBackupEnabled()) {
            return Result.success()
        }

        val treeUri = userPreferencesRepository.getScheduledFolderBackupTreeUri()
        if (treeUri.isNullOrBlank()) {
            Log.w(TAG, "Scheduled folder backup enabled but no folder selected")
            return Result.failure()
        }

        if (!folderBackupWriter.canWriteToFolder(treeUri)) {
            Log.w(TAG, "Cannot write to scheduled backup folder")
            return Result.retry()
        }

        return when (val exportResult = backupExporter.exportBackupBytes()) {
            is ExportBytesResult.Success -> {
                when (val writeResult = folderBackupWriter.writeBackup(treeUri, exportResult.bytes)) {
                    is FolderBackupWriter.Result.Success -> {
                        userPreferencesRepository.setScheduledFolderBackupLastTimestamp(
                            System.currentTimeMillis()
                        )
                        Log.i(TAG, "Scheduled folder backup completed")
                        Result.success()
                    }
                    is FolderBackupWriter.Result.Failure -> {
                        Log.e(TAG, "Scheduled folder backup write failed: ${writeResult.message}")
                        Result.retry()
                    }
                }
            }
            is ExportBytesResult.Error -> {
                Log.e(TAG, "Scheduled folder backup export failed: ${exportResult.message}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduledFolderBackup"
    }
}
