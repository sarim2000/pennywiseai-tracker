package com.pennywiseai.shared

import com.pennywiseai.shared.data.SharedDataGraph
import com.pennywiseai.shared.data.local.entity.SharedSubscriptionEntity
import com.pennywiseai.shared.data.model.SharedTransactionType
import com.pennywiseai.shared.data.statement.SharedStatementImportResult
import com.pennywiseai.shared.data.util.currentTimeMillis
import com.pennywiseai.shared.data.util.monthStartEpochMillis
import com.pennywiseai.shared.domain.usecase.CreateManualTransactionUseCase
import com.pennywiseai.shared.domain.usecase.ImportStatementUseCase
import com.pennywiseai.shared.domain.usecase.ManualTransactionInput
import com.pennywiseai.shared.domain.usecase.TransactionMutationResult
import com.pennywiseai.shared.domain.usecase.UpdateManualTransactionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

data class SharedHomeSnapshot(
    val categories: List<String>,
    val recentTransactions: List<SharedRecentTransactionItem>,
    val recentTransactionSummaries: List<String>,
    val transactionCount: Int,
    val subscriptionCount: Int,
    val budgetCount: Int,
    val accountCount: Int,
    val monthlyIncomeMinor: Long = 0L,
    val monthlyExpenseMinor: Long = 0L,
    val monthlyNetMinor: Long = 0L,
    val lastImportImported: Int = 0,
    val lastImportParsed: Int = 0,
    val lastImportSkipped: Int = 0,
    val lastError: String? = null
)

data class SharedRecentTransactionItem(
    val transactionId: Long,
    val merchantName: String,
    val category: String,
    val amountMinor: Long,
    val currency: String,
    val transactionType: String,
    val occurredAtEpochMillis: Long,
    val note: String?,
    val bankName: String?,
    val accountLast4: String?,
    val summary: String
)

class PennyWiseSharedFacade {
    private val graph by lazy { SharedDataGraph.create() }
    private val createUseCase by lazy { CreateManualTransactionUseCase(graph.transactionRepository) }
    private val updateUseCase by lazy { UpdateManualTransactionUseCase(graph.transactionRepository) }
    private val importStatementUseCase by lazy { ImportStatementUseCase(graph.transactionRepository) }

    fun initializeAndLoadHome(): SharedHomeSnapshot = safeSnapshot {
        runBlocking {
            graph.initialize()
            loadHomeSnapshot()
        }
    }

    fun addExpenseAndLoadHome(
        merchantName: String,
        category: String,
        amountMinor: Long,
        note: String? = null,
        currency: String = "INR"
    ): SharedHomeSnapshot = safeSnapshot {
        addTransactionAndLoadHome(
            merchantName = merchantName,
            category = category,
            amountMinor = amountMinor,
            note = note,
            currency = currency,
            transactionType = "EXPENSE",
            occurredAtEpochMillis = com.pennywiseai.shared.data.util.currentTimeMillis(),
            bankName = null,
            accountLast4 = null
        )
    }

    fun addTransactionAndLoadHome(
        merchantName: String,
        category: String,
        amountMinor: Long,
        note: String? = null,
        currency: String = "INR",
        transactionType: String = "EXPENSE",
        occurredAtEpochMillis: Long,
        bankName: String? = null,
        accountLast4: String? = null
    ): SharedHomeSnapshot = safeSnapshot {
        runBlocking {
            when (
                val result = createUseCase.execute(
                    ManualTransactionInput(
                        amountMinor = amountMinor,
                        merchantName = merchantName,
                        category = category,
                        note = note,
                        currency = currency,
                        transactionType = parseType(transactionType),
                        occurredAtEpochMillis = occurredAtEpochMillis,
                        bankName = bankName,
                        accountLast4 = accountLast4
                    )
                )
            ) {
                is TransactionMutationResult.Success -> loadHomeSnapshot()
                is TransactionMutationResult.ValidationError -> loadHomeSnapshot(result.message)
                is TransactionMutationResult.NotFound -> loadHomeSnapshot(result.message)
            }
        }
    }

    fun updateExpenseAndLoadHome(
        transactionId: Long,
        merchantName: String,
        category: String,
        amountMinor: Long,
        note: String? = null,
        currency: String = "INR"
    ): SharedHomeSnapshot = safeSnapshot {
        updateTransactionAndLoadHome(
            transactionId = transactionId,
            merchantName = merchantName,
            category = category,
            amountMinor = amountMinor,
            note = note,
            currency = currency,
            transactionType = "EXPENSE",
            occurredAtEpochMillis = com.pennywiseai.shared.data.util.currentTimeMillis(),
            bankName = null,
            accountLast4 = null
        )
    }

    fun updateTransactionAndLoadHome(
        transactionId: Long,
        merchantName: String,
        category: String,
        amountMinor: Long,
        note: String? = null,
        currency: String = "INR",
        transactionType: String = "EXPENSE",
        occurredAtEpochMillis: Long,
        bankName: String? = null,
        accountLast4: String? = null
    ): SharedHomeSnapshot = safeSnapshot {
        runBlocking {
            when (
                val result = updateUseCase.execute(
                    transactionId = transactionId,
                    input = ManualTransactionInput(
                        amountMinor = amountMinor,
                        merchantName = merchantName,
                        category = category,
                        note = note,
                        currency = currency,
                        transactionType = parseType(transactionType),
                        occurredAtEpochMillis = occurredAtEpochMillis,
                        bankName = bankName,
                        accountLast4 = accountLast4
                    )
                )
            ) {
                is TransactionMutationResult.Success -> loadHomeSnapshot()
                is TransactionMutationResult.ValidationError -> loadHomeSnapshot(result.message)
                is TransactionMutationResult.NotFound -> loadHomeSnapshot(result.message)
            }
        }
    }

    fun importStatementTextAndLoadHome(statementText: String): SharedHomeSnapshot = safeSnapshot {
        runBlocking {
            when (val result = importStatementUseCase.importFromText(statementText)) {
                is SharedStatementImportResult.Success -> {
                    loadHomeSnapshot(
                        lastError = null,
                        imported = result.imported,
                        parsed = result.totalParsed,
                        skipped = result.skippedDuplicates
                    )
                }
                is SharedStatementImportResult.Error -> loadHomeSnapshot(result.message)
            }
        }
    }

    fun getAllTransactions(): List<SharedRecentTransactionItem> {
        return try {
            runBlocking {
                graph.transactionRepository.observeTransactions().first().map { it.toItem() }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun getTransactionById(transactionId: Long): SharedRecentTransactionItem? {
        return try {
            runBlocking {
                graph.transactionRepository.getById(transactionId)?.toItem()
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun deleteTransaction(transactionId: Long): Boolean {
        return try {
            runBlocking {
                graph.transactionRepository.softDelete(
                    transactionId,
                    com.pennywiseai.shared.data.util.currentTimeMillis()
                )
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun searchTransactions(query: String): List<SharedRecentTransactionItem> {
        return try {
            runBlocking {
                val lowerQuery = query.lowercase()
                graph.transactionRepository.observeTransactions().first()
                    .filter {
                        it.merchantName.lowercase().contains(lowerQuery) ||
                            (it.note?.lowercase()?.contains(lowerQuery) == true) ||
                            it.category.lowercase().contains(lowerQuery)
                    }
                    .map { it.toItem() }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun addSubscriptionAndLoadHome(
        merchantName: String,
        amountMinor: Long,
        category: String? = null,
        currency: String = "INR"
    ): SharedHomeSnapshot = safeSnapshot {
        runBlocking {
            val now = com.pennywiseai.shared.data.util.currentTimeMillis()
            graph.subscriptionRepository.upsert(
                SharedSubscriptionEntity(
                    merchantName = merchantName,
                    amountMinor = amountMinor,
                    category = category,
                    currency = currency,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
            )
            loadHomeSnapshot()
        }
    }

    private suspend fun loadHomeSnapshot(
        lastError: String? = null,
        imported: Int = 0,
        parsed: Int = 0,
        skipped: Int = 0
    ): SharedHomeSnapshot {
        val categories = graph.categoryRepository.observeCategories().first().map { it.name }
        val transactions = graph.transactionRepository.observeTransactions().first()
        val subscriptions = graph.subscriptionRepository.observeAll().first()
        val budgets = graph.budgetRepository.observeBudgets().first()
        val accounts = graph.accountRepository.observeBalances().first()
        val recentTransactions = transactions.take(10).map { it.toItem() }

        val monthStart = monthStartEpochMillis()
        val thisMonthTxns = transactions.filter { it.occurredAtEpochMillis >= monthStart }
        val monthlyIncome = thisMonthTxns
            .filter { it.transactionType == SharedTransactionType.INCOME }
            .sumOf { it.amountMinor }
        val monthlyExpense = thisMonthTxns
            .filter { it.transactionType != SharedTransactionType.INCOME }
            .sumOf { it.amountMinor }

        return SharedHomeSnapshot(
            categories = categories,
            recentTransactions = recentTransactions,
            recentTransactionSummaries = recentTransactions.map { it.summary },
            transactionCount = transactions.size,
            subscriptionCount = subscriptions.size,
            budgetCount = budgets.size,
            accountCount = accounts.size,
            monthlyIncomeMinor = monthlyIncome,
            monthlyExpenseMinor = monthlyExpense,
            monthlyNetMinor = monthlyIncome - monthlyExpense,
            lastImportImported = imported,
            lastImportParsed = parsed,
            lastImportSkipped = skipped,
            lastError = lastError
        )
    }

    private fun parseType(value: String): com.pennywiseai.shared.data.model.SharedTransactionType {
        return com.pennywiseai.shared.data.model.SharedTransactionType.entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: com.pennywiseai.shared.data.model.SharedTransactionType.EXPENSE
    }

    private inline fun safeSnapshot(block: () -> SharedHomeSnapshot): SharedHomeSnapshot {
        return try {
            block()
        } catch (t: Throwable) {
            SharedHomeSnapshot(
                categories = listOf("Others"),
                recentTransactions = emptyList(),
                recentTransactionSummaries = emptyList(),
                transactionCount = 0,
                subscriptionCount = 0,
                budgetCount = 0,
                accountCount = 0,
                lastImportImported = 0,
                lastImportParsed = 0,
                lastImportSkipped = 0,
                lastError = "Shared error: ${t.message ?: "Unknown"}"
            )
        }
    }

    private fun com.pennywiseai.shared.data.model.SharedTransaction.toItem() =
        SharedRecentTransactionItem(
            transactionId = id,
            merchantName = merchantName,
            category = category,
            amountMinor = amountMinor,
            currency = currency,
            transactionType = transactionType.name,
            occurredAtEpochMillis = occurredAtEpochMillis,
            note = note,
            bankName = bankName,
            accountLast4 = accountLast4,
            summary = "$merchantName - $category - $currency ${formatAmountMinor(amountMinor)}"
        )

    private fun formatAmountMinor(amountMinor: Long): String {
        val whole = amountMinor / 100
        val fraction = amountMinor % 100
        val fractionText = fraction.toString().padStart(2, '0')
        return "$whole.$fractionText"
    }
}
