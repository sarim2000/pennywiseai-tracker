package com.pennywiseai.shared

import com.pennywiseai.shared.data.SharedDataGraph
import com.pennywiseai.shared.data.local.entity.SharedBudgetCategoryEntity
import com.pennywiseai.shared.data.local.entity.SharedBudgetEntity
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

data class SharedCategoryItem(
    val id: Long,
    val name: String,
    val colorHex: String,
    val isSystem: Boolean,
    val isIncome: Boolean,
    val displayOrder: Int
)

data class SharedBudgetItem(
    val id: Long,
    val name: String,
    val limitMinor: Long,
    val spentMinor: Long,
    val periodType: String,
    val groupType: String,
    val currency: String,
    val isActive: Boolean,
    val categoryBreakdowns: List<SharedBudgetCategoryBreakdown>
)

data class SharedBudgetCategoryBreakdown(
    val categoryName: String,
    val limitMinor: Long,
    val spentMinor: Long
)

data class SharedAccountItem(
    val bankName: String,
    val accountLast4: String,
    val balanceMinor: Long,
    val currency: String,
    val accountType: String?,
    val isCreditCard: Boolean,
    val lastUpdatedEpochMillis: Long
)

data class SharedCardItem(
    val id: Long,
    val cardLast4: String,
    val cardType: String,
    val bankName: String,
    val accountLast4: String?,
    val isActive: Boolean,
    val lastBalanceMinor: Long?,
    val currency: String
)

data class SharedSubscriptionItem(
    val id: Long,
    val merchantName: String,
    val amountMinor: Long,
    val category: String?,
    val currency: String,
    val state: String,
    val nextPaymentEpochMillis: Long?,
    val createdAtEpochMillis: Long
)

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
    private val createUseCase by lazy { CreateManualTransactionUseCase(graph.transactionRepository, graph.accountRepository) }
    private val updateUseCase by lazy { UpdateManualTransactionUseCase(graph.transactionRepository, graph.accountRepository) }
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

    fun getAllCategories(): List<SharedCategoryItem> {
        return try {
            runBlocking {
                graph.categoryRepository.observeCategories().first().map { it.toCategoryItem() }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun createCategory(name: String, colorHex: String, isIncome: Boolean): Boolean {
        return try {
            runBlocking {
                val now = currentTimeMillis()
                graph.categoryRepository.insertCategory(
                    com.pennywiseai.shared.data.model.SharedCategory(
                        name = name,
                        colorHex = colorHex,
                        isSystem = false,
                        isIncome = isIncome,
                        displayOrder = 999,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                )
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun updateCategory(id: Long, name: String, colorHex: String, isIncome: Boolean): Boolean {
        return try {
            runBlocking {
                val existing = graph.categoryRepository.getById(id) ?: return@runBlocking false
                if (existing.isSystem) return@runBlocking false
                graph.categoryRepository.updateCategory(
                    existing.copy(
                        name = name,
                        colorHex = colorHex,
                        isIncome = isIncome,
                        updatedAtEpochMillis = currentTimeMillis()
                    )
                )
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun deleteCategory(id: Long): Boolean {
        return try {
            runBlocking {
                graph.categoryRepository.deleteCategory(id)
            }
        } catch (_: Throwable) {
            false
        }
    }

    // ── Budget methods ──────────────────────────────────────────

    fun getAllBudgets(): List<SharedBudgetItem> {
        return try {
            runBlocking {
                val budgets = graph.budgetRepository.observeBudgets().first()
                val transactions = graph.transactionRepository.observeTransactions().first()
                budgets.map { budget ->
                    val categories = graph.budgetRepository.observeBudgetCategories(budget.id).first()
                    val categoryNames = categories.map { it.categoryName }.toSet()
                    val periodTxns = transactions.filter { txn ->
                        txn.occurredAtEpochMillis in budget.startEpochMillis..budget.endEpochMillis &&
                            txn.transactionType != SharedTransactionType.INCOME &&
                            (categoryNames.isEmpty() || txn.category in categoryNames)
                    }
                    val totalSpent = periodTxns.sumOf { it.amountMinor }
                    val breakdowns = categories.map { cat ->
                        val catSpent = periodTxns.filter { it.category == cat.categoryName }.sumOf { it.amountMinor }
                        SharedBudgetCategoryBreakdown(cat.categoryName, cat.budgetAmountMinor, catSpent)
                    }
                    SharedBudgetItem(
                        id = budget.id,
                        name = budget.name,
                        limitMinor = budget.limitMinor,
                        spentMinor = totalSpent,
                        periodType = budget.periodType,
                        groupType = budget.groupType,
                        currency = budget.currency,
                        isActive = budget.isActive,
                        categoryBreakdowns = breakdowns
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun getBudgetDetail(budgetId: Long): SharedBudgetItem? {
        return getAllBudgets().firstOrNull { it.id == budgetId }
    }

    fun createBudget(
        name: String,
        limitMinor: Long,
        periodType: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        groupType: String = "LIMIT",
        currency: String = "INR",
        categoryLimits: List<SharedBudgetCategoryBreakdown> = emptyList()
    ): Long {
        return try {
            runBlocking {
                val now = currentTimeMillis()
                val budgetId = graph.budgetRepository.upsertBudget(
                    SharedBudgetEntity(
                        name = name.trim(),
                        limitMinor = limitMinor,
                        periodType = periodType,
                        startEpochMillis = startEpochMillis,
                        endEpochMillis = endEpochMillis,
                        groupType = groupType,
                        currency = currency,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                )
                if (categoryLimits.isNotEmpty()) {
                    graph.budgetRepository.replaceBudgetCategories(
                        budgetId = budgetId,
                        categories = categoryLimits.map { cl ->
                            SharedBudgetCategoryEntity(
                                budgetId = budgetId,
                                categoryName = cl.categoryName,
                                budgetAmountMinor = cl.limitMinor
                            )
                        }
                    )
                }
                budgetId
            }
        } catch (_: Throwable) {
            -1L
        }
    }

    fun updateBudget(
        id: Long,
        name: String,
        limitMinor: Long,
        periodType: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        groupType: String = "LIMIT",
        currency: String = "INR",
        categoryLimits: List<SharedBudgetCategoryBreakdown> = emptyList()
    ): Boolean {
        return try {
            runBlocking {
                val now = currentTimeMillis()
                graph.budgetRepository.upsertBudget(
                    SharedBudgetEntity(
                        id = id,
                        name = name.trim(),
                        limitMinor = limitMinor,
                        periodType = periodType,
                        startEpochMillis = startEpochMillis,
                        endEpochMillis = endEpochMillis,
                        groupType = groupType,
                        currency = currency,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                )
                graph.budgetRepository.replaceBudgetCategories(
                    budgetId = id,
                    categories = categoryLimits.map { cl ->
                        SharedBudgetCategoryEntity(
                            budgetId = id,
                            categoryName = cl.categoryName,
                            budgetAmountMinor = cl.limitMinor
                        )
                    }
                )
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun deleteBudget(id: Long): Boolean {
        return try {
            runBlocking {
                graph.budgetRepository.deleteBudget(id)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    // ── Account methods ────────────────────────────────────────

    fun getAllAccounts(): List<SharedAccountItem> {
        return try {
            runBlocking {
                graph.accountRepository.getDistinctAccounts().map { it.toAccountItem() }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun createAccount(
        bankName: String,
        accountLast4: String,
        accountType: String = "SAVINGS",
        balanceMinor: Long = 0L,
        currency: String = "INR"
    ): Boolean {
        return try {
            runBlocking {
                val now = currentTimeMillis()
                graph.accountRepository.insertBalance(
                    com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity(
                        bankName = bankName.trim(),
                        accountLast4 = accountLast4.trim(),
                        timestampEpochMillis = now,
                        balanceMinor = balanceMinor,
                        accountType = accountType,
                        isCreditCard = accountType.equals("CREDIT", ignoreCase = true),
                        currency = currency,
                        createdAtEpochMillis = now
                    )
                )
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun deleteAccount(bankName: String, accountLast4: String): Boolean {
        return try {
            runBlocking {
                graph.accountRepository.deleteByAccount(bankName, accountLast4)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun getAccountTransactions(bankName: String, accountLast4: String): List<SharedRecentTransactionItem> {
        return try {
            runBlocking {
                graph.transactionRepository.observeTransactions().first()
                    .filter { it.bankName == bankName && it.accountLast4 == accountLast4 }
                    .map { it.toItem() }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    // ── Card methods ─────────────────────────────────────────

    fun getAllCards(): List<SharedCardItem> {
        return try {
            runBlocking {
                graph.accountRepository.observeCards().first().map { it.toCardItem() }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun createCard(
        cardLast4: String,
        cardType: String = "CREDIT",
        bankName: String,
        accountLast4: String? = null
    ): Boolean {
        return try {
            runBlocking {
                val now = currentTimeMillis()
                graph.accountRepository.upsertCard(
                    com.pennywiseai.shared.data.local.entity.SharedCardEntity(
                        cardLast4 = cardLast4.trim(),
                        cardType = cardType,
                        bankName = bankName.trim(),
                        accountLast4 = accountLast4?.trim(),
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                )
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun deleteCard(cardId: Long): Boolean {
        return try {
            runBlocking {
                graph.accountRepository.deleteCardById(cardId)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    // ── Analytics methods ────────────────────────────────────

    fun getTransactionsForPeriod(
        startDateMs: Long,
        endDateMs: Long,
        type: String? = null
    ): List<SharedRecentTransactionItem> {
        return try {
            runBlocking {
                graph.transactionRepository.observeTransactions().first()
                    .filter { txn ->
                        txn.occurredAtEpochMillis in startDateMs..endDateMs &&
                            (type == null || txn.transactionType.name.equals(type, ignoreCase = true))
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

    // ── Subscription CRUD methods ──────────────────────────────

    fun getAllSubscriptions(): List<SharedSubscriptionItem> {
        return try {
            runBlocking {
                graph.subscriptionRepository.observeAll().first().map { it.toSubscriptionItem() }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun createSubscription(
        merchantName: String,
        amountMinor: Long,
        category: String? = null,
        currency: String = "INR"
    ): Boolean {
        return try {
            runBlocking {
                val now = currentTimeMillis()
                graph.subscriptionRepository.upsert(
                    SharedSubscriptionEntity(
                        merchantName = merchantName.trim(),
                        amountMinor = amountMinor,
                        category = category,
                        currency = currency,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                )
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun updateSubscription(
        id: Long,
        merchantName: String,
        amountMinor: Long,
        category: String? = null,
        currency: String = "INR"
    ): Boolean {
        return try {
            runBlocking {
                val now = currentTimeMillis()
                graph.subscriptionRepository.upsert(
                    SharedSubscriptionEntity(
                        id = id,
                        merchantName = merchantName.trim(),
                        amountMinor = amountMinor,
                        category = category,
                        currency = currency,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                )
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun deleteSubscription(id: Long): Boolean {
        return try {
            runBlocking {
                graph.subscriptionRepository.deleteById(id)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun SharedSubscriptionEntity.toSubscriptionItem() =
        SharedSubscriptionItem(
            id = id,
            merchantName = merchantName,
            amountMinor = amountMinor,
            category = category,
            currency = currency,
            state = state,
            nextPaymentEpochMillis = nextPaymentEpochMillis,
            createdAtEpochMillis = createdAtEpochMillis
        )

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

    private fun com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity.toAccountItem() =
        SharedAccountItem(
            bankName = bankName,
            accountLast4 = accountLast4,
            balanceMinor = balanceMinor,
            currency = currency,
            accountType = accountType,
            isCreditCard = isCreditCard,
            lastUpdatedEpochMillis = timestampEpochMillis
        )

    private fun com.pennywiseai.shared.data.local.entity.SharedCardEntity.toCardItem() =
        SharedCardItem(
            id = id,
            cardLast4 = cardLast4,
            cardType = cardType,
            bankName = bankName,
            accountLast4 = accountLast4,
            isActive = isActive,
            lastBalanceMinor = lastBalanceMinor,
            currency = currency
        )

    private fun com.pennywiseai.shared.data.model.SharedCategory.toCategoryItem() =
        SharedCategoryItem(
            id = id,
            name = name,
            colorHex = colorHex,
            isSystem = isSystem,
            isIncome = isIncome,
            displayOrder = displayOrder
        )
}
