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
     * Success means the backup file now holds it under its own name: a refused delete, or a
     * provider that names the copy "name (1)", is a failure.
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
        // A temp left by an interrupted run is never put in place: this run writes its own.
        folder.findFile(ScheduledFolderBackupConstants.TEMP_BACKUP_FILE_NAME)?.let { stale ->
            if (!stale.delete()) return Result.Failure("Could not remove the old temporary backup file")
        }
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
            tempFile.delete()
            return Result.Failure("Could not replace the previous backup file")
        }
        if (tempFile.renameTo(ScheduledFolderBackupConstants.BACKUP_FILE_NAME)) {
            if (tempFile.name == ScheduledFolderBackupConstants.BACKUP_FILE_NAME) return Result.Success
            // The provider suffixed the name, so the old file is still there: drop the extra copy.
            tempFile.delete()
            return Result.Failure("Could not replace the previous backup file")
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
        data class Failure(val message: String) : Result()
    }
}
