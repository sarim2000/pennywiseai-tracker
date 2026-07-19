package com.pennywiseai.tracker.data.csv

import android.content.Context
import android.net.Uri
import android.util.Log
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports historical transactions from a CSV in PennyWise's own export format
 * (see [CsvTransactionImporter]). Opens the picked [Uri] via the ContentResolver,
 * parses it, dedups the parsed rows against what's already in the DB (by the
 * deterministic import hash) and inserts the rest.
 *
 * Free feature (onboarding/migration) — not gated behind Pro.
 */
@Singleton
class ImportCsvUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val importer: CsvTransactionImporter,
    private val transactionRepository: TransactionRepository
) {

    sealed class Result {
        data class Success(
            val imported: Int,
            val skippedDuplicate: Int,
            val failed: Int
        ) : Result()

        data class Error(val message: String) : Result()
    }

    suspend fun execute(uri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val parseResult = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    importer.parse(reader)
                }
            } ?: return@withContext Result.Error("Failed to read the selected file")

            // A structural problem (e.g. wrong file / missing required columns)
            // yields no rows and a reason — surface it instead of a bare "Imported 0".
            if (parseResult.transactions.isEmpty() && parseResult.failureReasons.isNotEmpty()) {
                return@withContext Result.Error(parseResult.failureReasons.first())
            }

            // Dedup against existing rows by hash — one batch query for the whole
            // file instead of a per-row lookup (a large CSV would otherwise issue
            // N queries). Also guard against duplicates *within* the same CSV.
            val existingHashes = transactionRepository
                .getExistingHashes(parseResult.transactions.map { it.transactionHash })
                .toHashSet()
            val toInsert = mutableListOf<TransactionEntity>()
            val seenHashes = HashSet<String>()
            var skippedDuplicate = 0

            for (transaction in parseResult.transactions) {
                val hash = transaction.transactionHash
                if (!seenHashes.add(hash) || hash in existingHashes) {
                    skippedDuplicate++
                    continue
                }
                toInsert.add(transaction)
            }

            if (toInsert.isNotEmpty()) {
                transactionRepository.insertTransactions(toInsert)
            }

            Result.Success(
                imported = toInsert.size,
                skippedDuplicate = skippedDuplicate,
                failed = parseResult.failedCount
            )
        } catch (e: Exception) {
            Log.e("ImportCsvUseCase", "CSV import failed", e)
            Result.Error("Import failed: ${e.message}")
        }
    }
}
