package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.model.SharedTransaction
import com.pennywiseai.shared.data.repository.SharedAccountRepository
import com.pennywiseai.shared.data.repository.SharedTransactionRepository
import com.pennywiseai.shared.data.statement.SharedPdfTextExtractor
import com.pennywiseai.shared.data.statement.SharedStatementImportResult
import com.pennywiseai.shared.data.statement.SharedStatementParserFactory
import com.pennywiseai.shared.data.util.currentTimeMillis

class ImportStatementUseCase(
    private val transactionRepository: SharedTransactionRepository,
    private val accountRepository: SharedAccountRepository
) {
    suspend fun importFromPdfPath(filePath: String): SharedStatementImportResult {
        return importFromText(SharedPdfTextExtractor.extractText(filePath))
    }

    suspend fun importFromText(statementText: String): SharedStatementImportResult {
        val parser = SharedStatementParserFactory.getParser(statementText)
            ?: return SharedStatementImportResult.Error(
                "Unsupported statement format. Currently supported: Google Pay, PhonePe."
            )

        val parsedTransactions = parser.parse(statementText)
        if (parsedTransactions.isEmpty()) {
            return SharedStatementImportResult.Error("No transactions found in the statement.")
        }

        var skippedByHash = 0
        var skippedByReference = 0
        var skippedByAmountDate = 0

        val toInsert = parsedTransactions.mapNotNull { parsed ->
            val hash = buildImportHash(parsed.rawText, parsed.amountMinor, parsed.timestampEpochMillis)
            if (transactionRepository.getByHash(hash) != null) {
                skippedByHash++
                return@mapNotNull null
            }

            if (!parsed.reference.isNullOrBlank() && transactionRepository.getByReference(parsed.reference) != null) {
                skippedByReference++
                return@mapNotNull null
            }

            val startOfDay = parsed.timestampEpochMillis - (parsed.timestampEpochMillis % DAY_MILLIS)
            val endOfDay = startOfDay + DAY_MILLIS - 1
            if (transactionRepository.getByAmountAndDate(parsed.amountMinor, startOfDay, endOfDay).isNotEmpty()) {
                skippedByAmountDate++
                return@mapNotNull null
            }

            SharedTransaction(
                amountMinor = parsed.amountMinor,
                merchantName = parsed.merchant ?: "Unknown Merchant",
                category = "Others",
                transactionType = parsed.transactionType,
                occurredAtEpochMillis = parsed.timestampEpochMillis,
                note = null,
                currency = "INR",
                transactionHash = hash,
                reference = parsed.reference,
                bankName = parsed.bankName,
                accountLast4 = parsed.accountLast4,
                balanceAfterMinor = null,
                createdAtEpochMillis = currentTimeMillis(),
                updatedAtEpochMillis = currentTimeMillis()
            )
        }

        if (toInsert.isNotEmpty()) {
            transactionRepository.insertAll(toInsert)
            autoCreateAccounts(toInsert)
        }

        return SharedStatementImportResult.Success(
            imported = toInsert.size,
            skippedDuplicates = skippedByHash + skippedByReference + skippedByAmountDate,
            totalParsed = parsedTransactions.size
        )
    }

    private suspend fun autoCreateAccounts(imported: List<SharedTransaction>) {
        val existingAccounts = accountRepository.getDistinctAccounts()
        val existingKeys = existingAccounts.map { "${it.bankName}|${it.accountLast4}" }.toSet()

        val newAccountKeys = imported
            .filter { !it.bankName.isNullOrBlank() && !it.accountLast4.isNullOrBlank() }
            .map { "${it.bankName}|${it.accountLast4}" }
            .distinct()
            .filter { it !in existingKeys }

        val now = currentTimeMillis()
        for (key in newAccountKeys) {
            val (bankName, last4) = key.split("|")
            accountRepository.insertBalance(
                SharedAccountBalanceEntity(
                    bankName = bankName,
                    accountLast4 = last4,
                    timestampEpochMillis = now,
                    balanceMinor = 0L,
                    accountType = "SAVINGS",
                    isCreditCard = false,
                    currency = "INR",
                    createdAtEpochMillis = now
                )
            )
        }
    }

    private fun buildImportHash(raw: String, amountMinor: Long, timestamp: Long): String =
        "${raw.take(120)}|$amountMinor|$timestamp".hashCode().toString()

    companion object {
        private const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}
