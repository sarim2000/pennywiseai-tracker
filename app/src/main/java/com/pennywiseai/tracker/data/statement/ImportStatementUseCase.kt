package com.pennywiseai.tracker.data.statement

import android.content.Context
import android.net.Uri
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDateTime
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

            StatementImportProcessor(repositoryStore()).process(parsedTransactions)
        } catch (e: Exception) {
            StatementImportResult.Error(
                e.message ?: "Failed to import statement."
            )
        }
    }

    private fun repositoryStore() = object : StatementImportProcessor.TransactionStore {
        override suspend fun getTransactionByHash(transactionHash: String): TransactionEntity? =
            transactionRepository.getTransactionByHash(transactionHash)

        override suspend fun findStatementMergeCandidate(
            transaction: TransactionEntity
        ): TransactionEntity? =
            transactionRepository.findStatementMergeCandidate(transaction)

        override suspend fun updateTransaction(transaction: TransactionEntity) {
            transactionRepository.updateTransaction(transaction)
        }

        override suspend fun getTransactionByAmountAndDate(
            amount: BigDecimal,
            dateStart: LocalDateTime,
            dateEnd: LocalDateTime
        ): List<TransactionEntity> =
            transactionRepository.getTransactionByAmountAndDate(amount, dateStart, dateEnd)

        override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
            transactionRepository.insertTransactions(transactions)
        }
    }
}
