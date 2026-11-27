package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.backup.folder.FolderBackupWriter
import com.pennywiseai.tracker.data.backup.BackupExporter
import com.pennywiseai.tracker.data.backup.ExportResult
import com.pennywiseai.tracker.data.backup.FolderBackupPassphrase
import com.pennywiseai.tracker.data.preferences.BackupPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes one backup to the backup folder, for the nightly worker and "Back up now" alike.
 * With a saved passphrase the backup is encrypted. A plain backup is written only when the
 * stored setting says so: a setting that can't be read, or a passphrase that can't be
 * unlocked, writes nothing.
 *
 * One backup at a time, and never while encryption is being turned on or off: the nightly
 * worker and Settings share one temp file in the folder, and a backup that read "plain"
 * must not land after the user's first encrypted one. Everything here runs on IO: the
 * Keystore and SAF copies block.
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

        /** The saved passphrase couldn't be unlocked. Nothing was written; a later try, or entering it again, may work. */
        data object PassphraseLocked : Result()

        /** Whether to encrypt couldn't be read, so nothing was written. */
        data object SettingUnreadable : Result()

        data class Failure(val message: String) : Result()
    }

    private val lock = Mutex()

    /** Encrypts folder backups with [passphrase] from now on, once no backup is being written. */
    suspend fun saveEncryption(passphrase: CharArray) = locked { folderBackupPassphrase.save(passphrase) }

    /** Folder backups go out plain from now on, once no backup is being written. */
    suspend fun clearEncryption() = locked { folderBackupPassphrase.clear() }

    suspend fun run(treeUri: String): Result = locked { write(treeUri) }

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }

    private suspend fun write(treeUri: String): Result {
        // Read under the lock, so it holds until this backup is in the folder.
        val passphrase = try {
            folderBackupPassphrase.load()
        } catch (e: FolderBackupPassphrase.LockedException) {
            return Result.PassphraseLocked
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Usually an IOException from DataStore: never read as "plain".
            return Result.SettingUnreadable
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
