package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.backup.folder.FolderBackupWriter
import com.pennywiseai.tracker.data.backup.BackupExporter
import com.pennywiseai.tracker.data.backup.ExportResult
import com.pennywiseai.tracker.data.backup.FolderBackupPassphrase
import com.pennywiseai.tracker.data.preferences.BackupPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes one backup to the backup folder, for the nightly worker and "Back up now" alike.
 * With a saved passphrase the backup is encrypted; when that passphrase can't be unlocked,
 * nothing is written rather than a plain backup.
 *
 * One backup at a time, and never while encryption is being turned on or off: the nightly
 * worker and Settings share one temp file in the folder, and a backup that read "plain"
 * must not land after the user's first encrypted one.
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

    private val lock = Mutex()

    /** Encrypts folder backups with [passphrase] from now on, once no backup is being written. */
    suspend fun saveEncryption(passphrase: CharArray) = lock.withLock { folderBackupPassphrase.save(passphrase) }

    /** Folder backups go out plain from now on, once no backup is being written. */
    suspend fun clearEncryption() = lock.withLock { folderBackupPassphrase.clear() }

    suspend fun run(treeUri: String): Result = lock.withLock { write(treeUri) }

    private suspend fun write(treeUri: String): Result {
        // Read under the lock, so it holds until this backup is in the folder.
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
