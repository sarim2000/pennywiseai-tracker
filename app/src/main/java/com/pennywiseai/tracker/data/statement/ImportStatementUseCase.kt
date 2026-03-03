package com.pennywiseai.tracker.data.statement

import android.content.Context
import android.net.Uri
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

class ImportStatementUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    @ApplicationContext private val context: Context
) {
    suspend fun import(uri: Uri): StatementImportResult = withContext(Dispatchers.IO) {
        try {
            val text = PdfTextExtractor.extractText(context, uri)

            val parser = PdfParserFactory.getParser(text)
                ?: return@withContext StatementImportResult.Error(
                    "Unsupported statement format. Currently supported: Google Pay, PhonePe."
                )

            val parsedTransactions = parser.parse(text)
            if (parsedTransactions.isEmpty()) {
                return@withContext StatementImportResult.Error(
                    "No transactions found in the statement."
                )
            }

            var skippedByHash = 0
            var skippedByReference = 0
            var skippedByAmountDate = 0

            val toInsert = parsedTransactions.mapNotNull { parsed ->
                val hash = parsed.transactionHash?.takeIf { it.isNotBlank() }
                    ?: parsed.generateTransactionId()

                // Tier 0: Exact re-import detection via hash
                if (transactionRepository.getTransactionByHash(hash) != null) {
                    skippedByHash++
                    return@mapNotNull null
                }

                // Tier 1: Cross-source dedup via UPI reference
                val ref = parsed.reference
                if (ref != null && transactionRepository.getTransactionByReference(ref) != null) {
                    skippedByReference++
                    return@mapNotNull null
                }

                // Tier 2: Cross-source dedup via amount + same calendar day
                val dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(parsed.timestamp),
                    ZoneId.systemDefault()
                )
                val startOfDay = dateTime.toLocalDate().atStartOfDay()
                val endOfDay = dateTime.toLocalDate().atTime(LocalTime.MAX)

                val amountMatches = transactionRepository.getTransactionByAmountAndDate(
                    parsed.amount, startOfDay, endOfDay
                )
                if (amountMatches.isNotEmpty()) {
                    skippedByAmountDate++
                    return@mapNotNull null
                }

                parsed.toEntity()
            }

            if (toInsert.isNotEmpty()) {
                transactionRepository.insertTransactions(toInsert)
            }

            val skippedDuplicates = skippedByHash + skippedByReference + skippedByAmountDate

            StatementImportResult.Success(
                imported = toInsert.size,
                skippedDuplicates = skippedDuplicates,
                skippedByReference = skippedByReference,
                skippedByAmountDate = skippedByAmountDate,
                skippedByHash = skippedByHash,
                totalParsed = parsedTransactions.size
            )
        } catch (e: Exception) {
            StatementImportResult.Error(
                e.message ?: "Failed to import statement."
            )
        }
    }
}
