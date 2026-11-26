package com.pennywiseai.tracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.backup.folder.FolderBackupWriter
import com.pennywiseai.tracker.data.preferences.BackupPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScheduledFolderBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val folderBackupWriter: FolderBackupWriter,
    private val folderBackupRunner: FolderBackupRunner,
    private val userPreferencesRepository: BackupPreferences
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

        return when (val result = folderBackupRunner.run(treeUri)) {
            is FolderBackupRunner.Result.Success -> {
                Log.i(TAG, "Scheduled folder backup completed")
                Result.success()
            }
            // Retrying can't help, and a plain backup isn't an option: Settings asks for the passphrase again.
            FolderBackupRunner.Result.PassphraseLost -> {
                Log.e(TAG, "Scheduled folder backup skipped: the backup passphrase can't be unlocked")
                Result.failure()
            }
            is FolderBackupRunner.Result.Failure -> {
                Log.e(TAG, "Scheduled folder backup failed: ${result.message}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduledFolderBackup"
    }
}
