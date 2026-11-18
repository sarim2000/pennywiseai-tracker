package com.pennywiseai.tracker.data.backup

import android.util.Log
import com.pennywiseai.tracker.data.receipt.ReceiptFiles
import java.io.File
import java.util.Base64

/**
 * Receipt photos and PDFs in a backup (#839). Pure file I/O against a given `filesDir`
 * so it runs in JVM unit tests.
 */
object BackupReceipts {

    private const val TAG = "BackupReceipts"
    private const val RECEIPTS_DIR = "receipts"

    /** All PDF receipts in one backup together, before encoding (Base64 adds a third). */
    const val MAX_TOTAL_BYTES: Long = 50L * 1024 * 1024

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

    /** Receipts read for a backup, and how many PDFs were left out for being too large. */
    data class Collected(val receipts: List<BackupReceipt>, val skipped: Int)

    /**
     * Reads and encodes each receipt file one at a time. Missing, unsafe or
     * unreadable files are skipped (and logged) — a bad file never fails the
     * backup. Photos always go in, as before PDFs could be attached. A PDF over
     * [maxFileBytes], or one that would take the backup's PDFs past [maxTotalBytes],
     * is left out and counted in [Collected.skipped], so the caller can say so.
     *
     * ponytail: every encoded file is held in memory until the JSON is written;
     * the size caps keep that bounded. Move to a streaming encoder if users ask
     * for bigger backups.
     */
    fun collect(
        filesDir: File,
        paths: Collection<String>,
        maxFileBytes: Long = ReceiptFiles.MAX_RECEIPT_BYTES,
        maxTotalBytes: Long = MAX_TOTAL_BYTES
    ): Collected {
        var pdfTotal = 0L
        var skipped = 0
        val receipts = paths.distinct().mapNotNull { path ->
            val file = safeReceiptFile(filesDir, path) ?: return@mapNotNull null
            if (!file.isFile) return@mapNotNull null
            val size = file.length()
            val isPdf = ReceiptFiles.isPdfPath(path)
            if (isPdf && (size > maxFileBytes || pdfTotal + size > maxTotalBytes)) {
                Log.w(TAG, "Left a PDF receipt out of the backup: ${if (size > maxFileBytes) "file" else "backup"} too large")
                skipped++
                return@mapNotNull null
            }
            try {
                BackupReceipt(path, Base64.getEncoder().encodeToString(file.readBytes()))
                    .also { if (isPdf) pdfTotal += size }
            } catch (e: Exception) {
                Log.w(TAG, "Skipped unreadable receipt: ${e.message}")
                null
            }
        }
        return Collected(receipts, skipped)
    }

    /** Receipts written by a restore, and how many PDFs were left out for being too large. */
    data class Restored(val written: Int, val skipped: Int)

    /**
     * Writes receipts back under `filesDir`, but only those whose path a
     * transaction in the DB still points at ([referencedPaths]) — so a merge
     * restores exactly the photos of the rows it kept. An existing file is
     * never overwritten. A PDF whose Base64 text is longer than [maxFileBytes]
     * encodes to is skipped before it's decoded, so a hostile backup can't make
     * the restore allocate a huge array; one that decodes to more than
     * [maxFileBytes] anyway is skipped too. Photos restore whatever their size,
     * as they always have.
     */
    fun restore(
        filesDir: File,
        receipts: List<BackupReceipt>,
        referencedPaths: Set<String>,
        maxFileBytes: Long = ReceiptFiles.MAX_RECEIPT_BYTES
    ): Restored {
        var written = 0
        var skipped = 0
        for (receipt in receipts) {
            if (receipt.path !in referencedPaths) continue
            val target = safeReceiptFile(filesDir, receipt.path)
            if (target == null) {
                Log.w(TAG, "Rejected unsafe receipt path in backup")
                continue
            }
            val capped = ReceiptFiles.isPdfPath(receipt.path)
            if (capped && receipt.dataBase64.length > encodedLength(maxFileBytes)) {
                Log.w(TAG, "Skipped an oversized receipt in backup")
                skipped++
                continue
            }
            try {
                val bytes = Base64.getDecoder().decode(receipt.dataBase64)
                if (bytes.isEmpty()) continue
                // The length check above allows a byte or two over when the cap isn't
                // a multiple of 3 and the text drops its padding.
                if (capped && bytes.size > maxFileBytes) {
                    Log.w(TAG, "Skipped an oversized receipt in backup")
                    skipped++
                    continue
                }
                if (target.exists()) {
                    if (!target.readBytes().contentEquals(bytes)) {
                        Log.w(TAG, "Kept existing different receipt file: ${target.name}")
                    }
                    continue
                }
                target.parentFile?.mkdirs()
                // Unique temp name: a fixed suffix could be another receipt's path.
                val tmp = File.createTempFile("restore", ".tmp", target.parentFile)
                try {
                    tmp.writeBytes(bytes)
                    if (tmp.renameTo(target)) written++
                } finally {
                    tmp.delete() // no-op after a successful rename
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipped a receipt during import: ${e.message}")
            }
        }
        return Restored(written, skipped)
    }

    /** Standard Base64 length of [bytes] bytes, padding included. */
    internal fun encodedLength(bytes: Long): Long = (bytes + 2) / 3 * 4
}
