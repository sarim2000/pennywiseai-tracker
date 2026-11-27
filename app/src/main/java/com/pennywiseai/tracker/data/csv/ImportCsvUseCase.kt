package com.pennywiseai.tracker.data.csv

import android.content.Context
import android.net.Uri
import android.util.Log
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.statement.ImportRules
import com.pennywiseai.tracker.data.statement.ImportRulesSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports historical transactions from a CSV in PennyWise's own export format
 * (see [CsvTransactionImporter]). Opens the picked [Uri] via the ContentResolver,
 * parses it, dedups the parsed rows against what's already in the DB (by the
 * deterministic import hash) and inserts the rest. A row without a category
 * of its own ("Others") gets the learned merchant category and the user's
 * rules; block rules apply to every row. A manual account's balance follows
 * its new rows.
 *
 * Free feature (onboarding/migration) — not gated behind Pro.
 */
@Singleton
class ImportCsvUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val importer: CsvTransactionImporter,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: CurrencyPreferences,
    private val importRules: ImportRulesSource,
    private val database: PennyWiseDatabase,
    private val accountBalanceRepository: AccountBalanceRepository
) {

    sealed class Result {
        data class Success(
            val imported: Int,
            val skippedDuplicate: Int,
            val failed: Int,
            val blockedByRules: Int = 0
        ) : Result()

        data class Error(val message: String) : Result()
    }

    suspend fun execute(uri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            val parseResult = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    importer.parse(reader, baseCurrency)
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

            // Rules are read inside the write: one deleted meanwhile can't be
            // recorded against the new rows (its history row would break the batch).
            val (imported, blocked) = if (toInsert.isEmpty()) 0 to 0 else database.withTransaction {
                val (kept, blockedCount) = applyRules(toInsert, importRules.load())
                val rows = kept.map { it.entity }
                // A manual account's opening is pinned before its rows land, so the
                // recompute adds them instead of absorbing them into the opening.
                accountBalanceRepository.pinManualOpenings(*rows.toTypedArray())
                val ids = if (rows.isEmpty()) emptyList() else transactionRepository.insertTransactions(rows)
                ids.zip(kept).forEach { (id, row) -> importRules.record(id, row.applications) }
                // A row the database ignored (-1: its hash is already there) wasn't imported.
                val inserted = ids.zip(rows).filter { (id, _) -> id != -1L }.map { (_, row) -> row }
                accountBalanceRepository.recomputeManualBalances(inserted)
                inserted.size to blockedCount
            }

            Result.Success(
                imported = imported,
                skippedDuplicate = skippedDuplicate,
                failed = parseResult.failedCount,
                blockedByRules = blocked
            )
        } catch (e: Exception) {
            Log.e("ImportCsvUseCase", "CSV import failed", e)
            Result.Error("Import failed: ${e.message}")
        }
    }

    internal companion object {
        /** The rows [rules] keep, and how many they blocked. The file's own category wins. */
        fun applyRules(rows: List<TransactionEntity>, rules: ImportRules): Pair<List<ImportRules.Outcome.Kept>, Int> {
            var blocked = 0
            val kept = rows.mapNotNull { row ->
                when (val outcome = rules.apply(row, categorize = row.category == CsvTransactionImporter.DEFAULT_CATEGORY)) {
                    ImportRules.Outcome.Blocked -> null.also { blocked++ }
                    is ImportRules.Outcome.Kept -> outcome
                }
            }
            return kept to blocked
        }
    }
}
