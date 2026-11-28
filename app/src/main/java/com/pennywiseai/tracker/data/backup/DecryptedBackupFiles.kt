package com.pennywiseai.tracker.data.backup

import android.util.Log
import java.io.File
import java.io.IOException

/**
 * The private folder an encrypted backup is decrypted into before it's parsed. A file
 * left there (the app died mid-restore) is swept at the start of the next import or
 * export, but never while a restore is still reading its own.
 */
internal object DecryptedBackupFiles {
    private const val DIR = "backup_restore"
    private const val TAG = "DecryptedBackupFiles"
    private var inUse = 0

    fun dir(cacheDir: File) = File(cacheDir, DIR)

    fun sweep(cacheDir: File) = synchronized(this) {
        if (inUse == 0) sweepLocked(dir(cacheDir))
    }

    /** Runs [block] with a new file in the folder, and deletes it afterwards. */
    fun <T> withTempFile(cacheDir: File, block: (File) -> T): T {
        val file = synchronized(this) {
            val dir = dir(cacheDir)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Couldn't create the restore folder")
            File.createTempFile("decrypted_", ".json", dir).also { inUse++ }
        }
        try {
            return block(file)
        } finally {
            if (!file.delete() && file.exists()) Log.w(TAG, "Couldn't delete a decrypted backup; the next import or export retries")
            synchronized(this) { inUse-- }
        }
    }

    private fun sweepLocked(dir: File) {
        dir.listFiles()?.forEach { stale ->
            if (!stale.delete() && stale.exists()) Log.w(TAG, "Couldn't delete a decrypted backup left from an earlier restore")
        }
    }
}
