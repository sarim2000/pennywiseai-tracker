package com.pennywiseai.tracker.backup.folder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
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

    fun writeBackup(treeUri: String, bytes: ByteArray): Result {
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: return Result.Failure("Backup folder is no longer accessible")

        if (!folder.isDirectory || !folder.canWrite()) {
            return Result.Failure("Cannot write to the selected backup folder")
        }

        val backupFile = folder.findFile(ScheduledFolderBackupConstants.BACKUP_FILE_NAME)
            ?: folder.createFile("application/octet-stream", ScheduledFolderBackupConstants.BACKUP_FILE_NAME)
            ?: return Result.Failure("Could not create backup file in the selected folder")

        if (!backupFile.canWrite()) {
            return Result.Failure("Cannot write to the backup file")
        }

        return try {
            context.contentResolver.openOutputStream(backupFile.uri, "wt")?.use { stream ->
                stream.write(bytes)
            } ?: return Result.Failure("Could not open backup file for writing")
            Result.Success
        } catch (e: Exception) {
            Result.Failure("Failed to write backup: ${e.message ?: "unknown error"}")
        }
    }

    sealed class Result {
        data object Success : Result()
        data class Failure(val message: String) : Result()
    }
}
