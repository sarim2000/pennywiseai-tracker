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

            // Dedup against existing rows by hash. Also guard against duplicates
            // *within* the same CSV (two identical rows) by tracking seen hashes.
            val toInsert = mutableListOf<TransactionEntity>()
            val seenHashes = HashSet<String>()
            var skippedDuplicate = 0

            for (transaction in parseResult.transactions) {
                val hash = transaction.transactionHash
                if (!seenHashes.add(hash)) {
                    skippedDuplicate++
                    continue
                }
                if (transactionRepository.getTransactionByHash(hash) != null) {
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
