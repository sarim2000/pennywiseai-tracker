package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.backup.folder.FolderBackupWriter
import com.pennywiseai.tracker.data.backup.BackupExporter
import com.pennywiseai.tracker.data.backup.ExportResult
import com.pennywiseai.tracker.data.backup.FolderBackupPassphrase
import com.pennywiseai.tracker.data.preferences.BackupPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes one backup to the backup folder, for the nightly worker and "Back up now" alike.
 * With a saved passphrase the backup is encrypted; when that passphrase can't be unlocked,
 * nothing is written rather than a plain backup.
 */
@Singleton
class FolderBackupRunner @Inject constructor(
    private val backupExporter: BackupExporter,
    private val folderBackupWriter: FolderBackupWriter,
    private val folderBackupPassphrase: FolderBackupPassphrase,
    private val preferences: BackupPreferences
) {
    sealed class Result {
        /** Written; [skippedReceipts] PDFs were too large to include. */
        data class Success(val skippedReceipts: Int) : Result()

        /** The saved passphrase can't be unlocked: the user has to enter it again. Nothing was written. */
        data object PassphraseLost : Result()

        /** The Keystore failed this time; nothing was written and a later try may work. */
        data object PassphraseUnavailable : Result()

        data class Failure(val message: String) : Result()
    }

    suspend fun run(treeUri: String): Result {
        val passphrase = try {
            folderBackupPassphrase.load()
        } catch (e: FolderBackupPassphrase.LostException) {
            return Result.PassphraseLost
        } catch (e: FolderBackupPassphrase.UnavailableException) {
            return Result.PassphraseUnavailable
        }
        val exportResult = try {
            backupExporter.exportBackup(
                includeReceipts = preferences.folderBackupIncludeReceipts.first(),
                passphrase = passphrase
            )
        } finally {
            passphrase?.fill('\u0000')
        }
        return when (exportResult) {
            is ExportResult.Error -> Result.Failure(exportResult.message)
            is ExportResult.Success -> {
                val writeResult = try {
                    folderBackupWriter.writeBackup(treeUri, exportResult.file)
                } finally {
                    exportResult.file.delete()
                }
                when (writeResult) {
                    is FolderBackupWriter.Result.Success -> {
                        // Settings' "Last backup" line names left-out PDFs: nobody reads a log at 2 AM.
                        preferences.recordScheduledFolderBackup(System.currentTimeMillis(), exportResult.skippedReceipts)
                        Result.Success(exportResult.skippedReceipts)
                    }
                    is FolderBackupWriter.Result.Failure -> Result.Failure(writeResult.message)
                }
            }
        }
    }
}
