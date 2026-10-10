package com.pennywiseai.tracker.data.backup

import android.util.Log
import java.io.File
import java.util.Base64

/**
 * Receipt photos in a backup (#839). Pure file I/O against a given `filesDir`
 * so it runs in JVM unit tests.
 */
object BackupReceipts {

    private const val TAG = "BackupReceipts"
    private const val RECEIPTS_DIR = "receipts"

    /**
     * Resolves a backup's receipt [path] to a file directly inside
     * `filesDir/receipts`, or null if it's anything else. The path comes from
     * an untrusted file, so absolute paths, `..`, nested dirs and anything that
     * canonicalises (symlinks included) outside the receipts dir are rejected.
     */
    fun safeReceiptFile(filesDir: File, path: String): File? {
        if (path.isBlank() || path.startsWith("/") || path.contains('\\')) return null
        val parts = path.split('/')
        if (parts.size != 2 || parts[0] != RECEIPTS_DIR) return null
        val name = parts[1]
        if (name.isEmpty() || name == "." || name == "..") return null
        return try {
            val dir = File(filesDir, RECEIPTS_DIR).canonicalFile
            val file = File(dir, name).canonicalFile
            file.takeIf { it.parentFile == dir }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads and encodes each receipt file one at a time. Missing, unsafe or
     * unreadable files are skipped (and logged) — a bad photo never fails the
     * backup.
     *
     * ponytail: every encoded photo is held in memory until the JSON is
     * written; move to a streaming encoder if users with hundreds of receipts
     * hit OOM.
     */
    fun collect(filesDir: File, paths: Collection<String>): List<BackupReceipt> =
        paths.distinct().mapNotNull { path ->
            val file = safeReceiptFile(filesDir, path) ?: return@mapNotNull null
            if (!file.isFile) return@mapNotNull null
            try {
                BackupReceipt(path, Base64.getEncoder().encodeToString(file.readBytes()))
            } catch (e: Exception) {
                Log.w(TAG, "Skipped unreadable receipt: ${e.message}")
                null
            }
        }

    /**
     * Writes receipts back under `filesDir`, but only those whose path a
     * transaction in the DB still points at ([referencedPaths]) — so a merge
     * restores exactly the photos of the rows it kept. An existing file is
     * never overwritten. Returns how many files were written.
     */
    fun restore(filesDir: File, receipts: List<BackupReceipt>, referencedPaths: Set<String>): Int {
        var written = 0
        for (receipt in receipts) {
            if (receipt.path !in referencedPaths) continue
            val target = safeReceiptFile(filesDir, receipt.path)
            if (target == null) {
                Log.w(TAG, "Rejected unsafe receipt path in backup")
                continue
            }
            try {
                val bytes = Base64.getDecoder().decode(receipt.dataBase64)
                if (bytes.isEmpty()) continue
                if (target.exists()) {
                    if (!target.readBytes().contentEquals(bytes)) {
                        Log.w(TAG, "Kept existing different receipt file: ${target.name}")
                    }
                    continue
                }
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, "${target.name}.restoring")
                tmp.writeBytes(bytes)
                if (tmp.renameTo(target)) written++ else tmp.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Skipped a receipt during import: ${e.message}")
            }
        }
        return written
    }
}
