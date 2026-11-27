package com.pennywiseai.tracker.backup.folder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderBackupWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun canWriteToFolder(treeUri: String): Boolean {
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return false
        if (!folder.isDirectory || !folder.canWrite()) return false

        val existing = folder.findFile(FINAL_NAME)
        return existing == null || existing.canWrite()
    }

    /**
     * Copies the backup [file] into the folder, streamed rather than loaded into memory.
     * Success means the backup file now holds it: a refused delete or a provider that renames
     * the copy to "name (1)" is a failure, never a new file left beside the old one.
     */
    fun writeBackup(treeUri: String, file: File): Result {
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: return Result.Failure("Backup folder is no longer accessible")

        if (!folder.isDirectory || !folder.canWrite()) {
            return Result.Failure("Cannot write to the selected backup folder")
        }

        // A temp with no backup beside it is the last complete backup, stranded after the old
        // file was removed: put it in place rather than delete it. Next to a backup it's a
        // leftover, safe to remove.
        folder.findFile(TEMP_NAME)?.let { leftover ->
            val error = if (folder.findFile(FINAL_NAME) == null) {
                moveIntoPlace(folder, leftover)
            } else if (!leftover.delete()) {
                "Could not remove the old temporary backup file"
            } else {
                null
            }
            if (error != null) return Result.Failure(error)
        }

        // Write to a sibling temp file first, then swap it into place. SAF has no atomic
        // replace, so writing straight to the backup file would truncate the previous
        // (good) backup on open and leave nothing behind if the write is interrupted.
        val tempFile = createNamed(folder, TEMP_NAME)
            ?: return Result.Failure("Could not create backup file in the selected folder")

        val writeError = copy({ file.inputStream() }, tempFile.uri)
        if (writeError != null) {
            tempFile.delete()
            return Result.Failure(writeError)
        }

        // Temp now holds a complete backup. Remove the old file, then put temp in its place;
        // if that's interrupted, the next run finds the temp alone and finishes the job.
        val old = folder.findFile(FINAL_NAME)
        if (old != null && !old.delete()) {
            tempFile.delete()
            return Result.Failure("Could not replace the previous backup file")
        }
        return moveIntoPlace(folder, tempFile)?.let { Result.Failure(it) } ?: Result.Success
    }

    /**
     * Makes [temp] the backup file, the old one already gone; null on success, or an error
     * with [temp] kept as the recoverable copy.
     */
    private fun moveIntoPlace(folder: DocumentFile, temp: DocumentFile): String? {
        if (temp.renameTo(FINAL_NAME)) {
            if (temp.name == FINAL_NAME) return null
            // The provider gave it a suffixed name, so something still holds the backup's name.
            // Name it back so the next run finds it.
            temp.renameTo(TEMP_NAME)
            return "Could not replace the previous backup file"
        }

        // Some SAF providers don't support rename. Copy it to a new backup file instead,
        // keeping the temp until that copy is complete.
        val finalFile = createNamed(folder, FINAL_NAME) ?: return "Could not finalize backup file"
        val error = copy(
            { context.contentResolver.openInputStream(temp.uri) ?: throw IOException("can't read the temporary backup") },
            finalFile.uri
        )
        if (error != null) {
            // A partial backup file would make the next run discard the good temp.
            finalFile.delete()
            return error
        }
        temp.delete()
        return null
    }

    /** Creates [name] in [folder], or null when it can't, or when the provider picked another name. */
    private fun createNamed(folder: DocumentFile, name: String): DocumentFile? {
        val created = folder.createFile(MIME_TYPE, name) ?: return null
        if (created.name == name) return created
        created.delete()
        return null
    }

    /** Copies what [open] opens to [uri], truncating. Returns null on success or an error message. */
    private fun copy(open: () -> InputStream, uri: Uri): String? {
        return try {
            open().use { source ->
                context.contentResolver.openOutputStream(uri, "wt")?.use { source.copyTo(it) }
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

    private companion object {
        const val MIME_TYPE = "application/octet-stream"
        const val FINAL_NAME = ScheduledFolderBackupConstants.BACKUP_FILE_NAME
        const val TEMP_NAME = ScheduledFolderBackupConstants.TEMP_BACKUP_FILE_NAME
    }
}
