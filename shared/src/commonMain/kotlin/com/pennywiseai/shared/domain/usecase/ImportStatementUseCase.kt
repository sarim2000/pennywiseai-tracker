package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.model.SharedTransaction
import com.pennywiseai.shared.data.model.SharedTransactionType
import com.pennywiseai.shared.data.repository.SharedAccountRepository
import com.pennywiseai.shared.data.repository.SharedTransactionRepository
import com.pennywiseai.shared.data.statement.SharedPdfTextExtractor
import com.pennywiseai.shared.data.statement.SharedStatementImportResult
import com.pennywiseai.shared.data.statement.SharedStatementParserFactory
import com.pennywiseai.shared.data.repository.SharedRuleRepository
import com.pennywiseai.shared.data.util.currentTimeMillis
import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import com.pennywiseai.shared.domain.rules.SharedRuleEvaluator
import kotlinx.coroutines.flow.first

class ImportStatementUseCase(
    private val transactionRepository: SharedTransactionRepository,
    private val accountRepository: SharedAccountRepository,
    private val ruleRepository: SharedRuleRepository? = null
) {
    suspend fun importFromPdfPath(filePath: String): SharedStatementImportResult {
        return importFromText(SharedPdfTextExtractor.extractText(filePath))
    }

    suspend fun importFromText(statementText: String): SharedStatementImportResult {
        val parser = SharedStatementParserFactory.getParser(statementText)
            ?: return SharedStatementImportResult.Error(
                "Unsupported statement format. Currently supported: Google Pay, PhonePe, slice."
            )

        val parsedTransactions = parser.parse(statementText)
        if (parsedTransactions.isEmpty()) {
            return SharedStatementImportResult.Error("No transactions found in the statement.")
        }

        var skippedByHash = 0
        var skippedByReference = 0
        var skippedByAmountDate = 0

        // Smart Rules run on parsed data (this pipeline), never on manual
        // entry where the user picked the category themselves. Loaded once per
        // import, applied per transaction after the default category mapping.
        val rules = ruleRepository?.observeRules()?.first().orEmpty()

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

            val merchant = parsed.merchant ?: "Unknown Merchant"
            val adjustment = SharedRuleEvaluator.firstMatch(rules, merchant)
            val ruleType = adjustment?.transactionType?.let { raw ->
                // An unknown type string in a stored action must not sink the
                // import — fall back to the parsed type.
                SharedTransactionType.entries.firstOrNull { it.name == raw }
            }

            SharedTransaction(
                amountMinor = parsed.amountMinor,
                merchantName = merchant,
                category = adjustment?.category ?: SharedCategoryMapping.determineCategory(
                    merchant,
                    parsed.transactionType.name
                ),
                transactionType = ruleType ?: parsed.transactionType,
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
