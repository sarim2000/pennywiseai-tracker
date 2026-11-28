package com.pennywiseai.tracker.backup.folder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderBackupWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun canWriteToFolder(treeUri: String): Boolean {
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return false
        if (!folder.isDirectory || !folder.canWrite()) return false

        val existing = folder.findFile(ScheduledFolderBackupConstants.BACKUP_FILE_NAME)
        return existing == null || existing.canWrite()
    }

    /**
     * Copies the backup [file] into the folder, streamed rather than loaded into memory.
     * Success means the backup file now holds it under its own name. A delete the provider
     * refuses never stops the backup. A renamed copy whose name can't be confirmed is kept,
     * never deleted: it is the newest backup, and the old one may already be gone.
     */
    fun writeBackup(treeUri: String, file: File): Result {
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: return Result.Failure("Backup folder is no longer accessible")

        if (!folder.isDirectory || !folder.canWrite()) {
            return Result.Failure("Cannot write to the selected backup folder")
        }

        // Write to a sibling temp file first, then swap it into place. SAF has no atomic
        // replace, so writing straight to the backup file would truncate the previous
        // (good) backup on open and leave nothing behind if the write is interrupted.
        // A temp left by an interrupted run is never put in place: this run writes its own,
        // under a suffixed name if the provider won't delete the stale one.
        folder.findFile(ScheduledFolderBackupConstants.TEMP_BACKUP_FILE_NAME)?.delete()
        val tempFile = folder.createFile(
            "application/octet-stream",
            ScheduledFolderBackupConstants.TEMP_BACKUP_FILE_NAME
        ) ?: return Result.Failure("Could not create backup file in the selected folder")

        val writeError = copyFile(file, tempFile.uri)
        if (writeError != null) {
            tempFile.delete()
            return Result.Failure(writeError)
        }

        // Temp now holds a complete backup. Remove the old file, then rename temp -> final.
        // If interrupted between the two, the fresh data stays under the temp name until the next run.
        val oldFile = folder.findFile(ScheduledFolderBackupConstants.BACKUP_FILE_NAME)
        if (oldFile != null && !oldFile.delete()) {
            // The provider won't delete it: write over it instead, so the name stays the
            // backup file's. The temp is kept until that write is done, as a complete copy;
            // if it fails, the rename below still puts this backup in the folder.
            if (copyFile(file, oldFile.uri) == null) {
                tempFile.delete()
                return Result.Success
            }
        }
        if (tempFile.renameTo(ScheduledFolderBackupConstants.BACKUP_FILE_NAME)) {
            // A suffixed name ("name (1)") or none at all (a failed query) can't be confirmed.
            // Either way the copy stays: deleting it could leave the folder with no backup.
            return if (tempFile.name == ScheduledFolderBackupConstants.BACKUP_FILE_NAME) {
                Result.Success
            } else {
                Result.NameUnconfirmed
            }
        }

        // Some SAF providers don't support rename. Fall back to a direct write of the
        // final file; the temp is left in place as a recoverable copy on failure.
        val finalFile = folder.createFile(
            "application/octet-stream",
            ScheduledFolderBackupConstants.BACKUP_FILE_NAME
        ) ?: return Result.Failure("Could not finalize backup file")
        if (finalFile.name != ScheduledFolderBackupConstants.BACKUP_FILE_NAME) {
            finalFile.delete()
            return Result.Failure("Could not finalize backup file")
        }

        val finalError = copyFile(file, finalFile.uri)
        if (finalError != null) {
            return Result.Failure(finalError)
        }
        tempFile.delete()
        return Result.Success
    }

    /** Copies [file] to [uri], truncating. Returns null on success or an error message. */
    private fun copyFile(file: File, uri: Uri): String? {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                file.inputStream().use { it.copyTo(stream) }
            } ?: return "Could not open backup file for writing"
            null
        } catch (e: Exception) {
            "Failed to write backup: ${e.message ?: "unknown error"}"
        }
    }

    sealed class Result {
        data object Success : Result()

        /** Written and kept, but under a name that couldn't be confirmed as the backup file's. */
        data object NameUnconfirmed : Result()
        data class Failure(val message: String) : Result()
    }
}
