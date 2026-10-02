package com.pennywiseai.tracker.data.webhook

import com.pennywiseai.tracker.BuildConfig
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.*
import com.pennywiseai.tracker.utils.countsInTotals
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookPayloadBuilder @Inject constructor(
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
    private val accounts: AccountBalanceRepository,
    private val budgetGroups: BudgetGroupRepository,
    private val preferences: UserPreferencesRepository,
    private val subscriptions: SubscriptionRepository,
    private val splits: TransactionSplitDao
) {
    suspend fun build(
        profile: WebhookProfileEntity,
        types: Set<WebhookDataType>,
        cursors: List<WebhookCursorEntity>,
        test: Boolean
    ): List<WebhookBatchPayload> {
        return buildAt(profile, types, cursors, test, LocalDateTime.now())
    }

    internal suspend fun buildAt(
        profile: WebhookProfileEntity,
        types: Set<WebhookDataType>,
        cursors: List<WebhookCursorEntity>,
        test: Boolean,
        time: LocalDateTime
    ): List<WebhookBatchPayload> {
        // SQL mutation timestamps have millisecond precision; keep inclusive cursors at that precision too.
        val now = time.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        if (test) return listOf(testPayload(profile, types, now))
        val range = resolveWebhookRange(profile, types, cursors, now)
        val transactionRange = if (profile.rangePreset == WebhookRangePreset.SINCE_LAST_SUCCESS) {
            range.copy(start = incrementalStart(cursors.firstOrNull { it.dataType == WebhookDataType.TRANSACTIONS }?.lastSuccessAt, now))
        } else range
        val rows = if (WebhookDataType.TRANSACTIONS in types) {
            val entities = if (range.preset == WebhookRangePreset.SINCE_LAST_SUCCESS) {
                transactions.getWebhookChanges(transactionRange.start, transactionRange.end, profile.currency, profile.id)
            } else {
                transactions.getWebhookTransactions(range.start, range.end, profile.currency).first() +
                    transactions.getWebhookCurrencyRemovals(profile.currency, profile.id)
            }
            entities.map { webhookTransactionPayload(it, profile.currency) }
        } else emptyList()
        val summary = if (WebhookDataType.SUMMARY in types) summary(range, profile.currency) else null
        val budgetPayloads = if (WebhookDataType.BUDGETS in types) budgetPayloads(profile.currency, now.toLocalDate()) else emptyList()
        val accountPayloads = if (WebhookDataType.ACCOUNTS in types) accountPayloads(profile.currency) else emptyList()
        val subscriptionPayloads = if (WebhookDataType.SUBSCRIPTIONS in types) subscriptionPayloads(profile.currency) else emptyList()
        val transactionsByBatch = rows.chunked(250)
        val budgetsByBatch = budgetPayloads.chunked(250)
        val accountsByBatch = accountPayloads.chunked(250)
        val subscriptionsByBatch = subscriptionPayloads.chunked(250)
        val batchCount = maxOf(1, transactionsByBatch.size, budgetsByBatch.size, accountsByBatch.size, subscriptionsByBatch.size)
        val batchId = UUID.randomUUID().toString()
        // A date-range export must not advance the updated-at cursor used by incremental exports.
        val updates = if (profile.rangePreset == WebhookRangePreset.SINCE_LAST_SUCCESS) {
            types.map { WebhookCursorUpdate(it, now, range.end) }
        } else emptyList()
        val batches = (0 until batchCount).map { index ->
            val batch = transactionsByBatch.getOrNull(index).orEmpty()
            val budgetBatch = budgetsByBatch.getOrNull(index).orEmpty()
            val accountBatch = accountsByBatch.getOrNull(index).orEmpty()
            val subscriptionBatch = subscriptionsByBatch.getOrNull(index).orEmpty()
            WebhookBatchPayload(
                envelope = WebhookEnvelope(
                    generatedAt = now.toString(),
                    app = WebhookAppInfo("PennyWise", BuildConfig.VERSION_NAME),
                    profile = WebhookProfileInfo(profile.id, profile.name),
                    request = WebhookRequestInfo(range.preset.name.lowercase(), range.start.toString(), range.end.toString(),
                        profile.currency, types.map { it.name.lowercase() }),
                    batch = WebhookBatchInfo(batchId, index + 1, batchCount),
                    summary = if (index == 0) summary else null,
                    transactions = batch,
                    budgets = budgetBatch,
                    accounts = accountBatch,
                    subscriptions = subscriptionBatch
                ),
                cursorUpdates = updates,
                itemCount = batch.size + budgetBatch.size + accountBatch.size + subscriptionBatch.size +
                    if (index == 0 && summary != null) 1 else 0
            )
        }
        val sized = batches.flatMap { splitBySize(it.envelope) }
        return sized.mapIndexed { index, envelope ->
            val numbered = envelope.copy(batch = envelope.batch.copy(index = index + 1, count = sized.size))
            WebhookBatchPayload(numbered, updates, itemCount(numbered))
        }
    }

    private fun itemCount(envelope: WebhookEnvelope): Int =
        envelope.transactions.size + envelope.budgets.size + envelope.accounts.size +
            envelope.subscriptions.size + if (envelope.summary != null) 1 else 0

    private fun splitBySize(envelope: WebhookEnvelope): List<WebhookEnvelope> {
        // Reserve the largest possible numbering before splitting so final numbering cannot exceed the limit.
        val reserved = envelope.copy(batch = envelope.batch.copy(index = Int.MAX_VALUE, count = Int.MAX_VALUE))
        if (WebhookPayloadEncoding.encode(reserved).size <= WebhookPayloadEncoding.MAX_BYTES || itemCount(envelope) <= 1) {
            return listOf(envelope)
        }
        var remaining = itemCount(envelope) / 2
        val leftSummary = if (envelope.summary != null && remaining > 0) envelope.summary else null
        if (leftSummary != null) remaining--
        fun <T> partition(rows: List<T>): Pair<List<T>, List<T>> {
            val count = minOf(remaining, rows.size)
            remaining -= count
            return rows.take(count) to rows.drop(count)
        }
        val (leftTransactions, rightTransactions) = partition(envelope.transactions)
        val (leftBudgets, rightBudgets) = partition(envelope.budgets)
        val (leftAccounts, rightAccounts) = partition(envelope.accounts)
        val (leftSubscriptions, rightSubscriptions) = partition(envelope.subscriptions)
        val left = envelope.copy(summary = leftSummary, transactions = leftTransactions, budgets = leftBudgets,
            accounts = leftAccounts, subscriptions = leftSubscriptions)
        val right = envelope.copy(summary = if (leftSummary == null) envelope.summary else null,
            transactions = rightTransactions, budgets = rightBudgets, accounts = rightAccounts, subscriptions = rightSubscriptions)
        return splitBySize(left) + splitBySize(right)
    }

    private suspend fun summary(range: WebhookDateRange, currency: String): WebhookSummaryPayload {
        val rows = splits.getTransactionsWithSplitsFiltered(range.start, range.end, currency).first()
        return webhookSummary(rows, currency, preferences.countCreditCardAsExpense.first())
    }

    private suspend fun budgetPayloads(currency: String, today: LocalDate): List<WebhookBudgetPayload> {
        val startDay = preferences.getBudgetCycleStartDay()
        return budgets.getActiveBudgets().first().filter { it.currency.equals(currency, true) }.map { budget ->
            val window = resolveBudgetWindow(budget, today, startDay)
            val spending = budgetGroups.getBudgetWindowBreakdown(budget, window, currency).totalActual
            val categories = budgets.getCategoriesForBudget(budget.id).first().map {
                WebhookBudgetCategoryPayload(it.categoryName, it.budgetAmount.asPlainStringSafe())
            }
            WebhookBudgetPayload("budget_${budget.id}", budget.name, budget.limitAmount.asPlainStringSafe(),
                budget.currency, budget.periodType.name, budget.groupType.name, window.start.toString(),
                window.end.toString(), spending.asPlainStringSafe(), categories)
        }
    }

    private suspend fun accountPayloads(currency: String): List<WebhookAccountPayload> =
        accounts.getAllLatestBalances().first().filter { it.currency.equals(currency, true) }.map {
            WebhookAccountPayload("account_${it.bankName}_${it.accountLast4}", it.bankName, it.accountLast4,
                it.balance.asPlainStringSafe(), it.currency, it.creditLimit?.asPlainStringSafe(), it.isCreditCard,
                it.accountLast4 == AccountBalanceEntity.WALLET_ACCOUNT_MARKER, it.timestamp.toString())
        }

    private suspend fun subscriptionPayloads(currency: String): List<WebhookSubscriptionPayload> =
        subscriptions.getAllSubscriptions().first().filter { it.currency.equals(currency, true) }.map {
            WebhookSubscriptionPayload("subscription_${it.id}", it.merchantName, it.amount.asPlainStringSafe(),
                it.currency, it.state.name, it.category, null, it.billingCycle, it.nextPaymentDate?.toString(), it.lastPaidAt?.toString())
        }

    private fun testPayload(profile: WebhookProfileEntity, types: Set<WebhookDataType>, now: LocalDateTime): WebhookBatchPayload {
        val envelope = WebhookEnvelope(
            generatedAt = now.toString(), app = WebhookAppInfo("PennyWise", BuildConfig.VERSION_NAME),
            profile = WebhookProfileInfo(profile.id, profile.name),
            request = WebhookRequestInfo("test", now.minusMinutes(1).toString(), now.toString(), profile.currency,
                types.map { it.name.lowercase() }), batch = WebhookBatchInfo(UUID.randomUUID().toString(), 1, 1),
            summary = if (WebhookDataType.SUMMARY in types) WebhookSummaryPayload("1200", "450", "750", profile.currency, emptyList()) else null,
            transactions = if (WebhookDataType.TRANSACTIONS in types) listOf(WebhookTransactionPayload(
                "test_txn_1", "upsert", "250", profile.currency, "Webhook Test Merchant", "Synthetic test payload",
                "Food", null, "EXPENSE", now.toString(), now.toString(), "Test Bank", "0000")) else emptyList(),
            budgets = if (WebhookDataType.BUDGETS in types) listOf(WebhookBudgetPayload("test_budget", "Test budget",
                "500", profile.currency, "MONTHLY", "SPENDING", now.toLocalDate().toString(), now.toLocalDate().toString(), "250", emptyList())) else emptyList(),
            accounts = if (WebhookDataType.ACCOUNTS in types) listOf(WebhookAccountPayload("test_account", "Test Bank",
                "0000", "1000", profile.currency, null, false, false, now.toString())) else emptyList(),
            subscriptions = if (WebhookDataType.SUBSCRIPTIONS in types) listOf(WebhookSubscriptionPayload("test_subscription",
                "Test subscription", "100", profile.currency, "ACTIVE")) else emptyList()
        )
        return WebhookBatchPayload(envelope, emptyList(), envelope.transactions.size + envelope.budgets.size +
            envelope.accounts.size + envelope.subscriptions.size + if (envelope.summary != null) 1 else 0)
    }
}

private val EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0)

internal fun resolveWebhookRange(
    profile: WebhookProfileEntity, types: Set<WebhookDataType>, cursors: List<WebhookCursorEntity>, now: LocalDateTime
): WebhookDateRange {
    val today = now.toLocalDate()
    val start = when (profile.rangePreset) {
        WebhookRangePreset.SINCE_LAST_SUCCESS -> types.minOfOrNull { type ->
            incrementalStart(cursors.firstOrNull { it.dataType == type }?.lastSuccessAt, now)
        } ?: EPOCH
        WebhookRangePreset.TODAY -> today.atStartOfDay()
        WebhookRangePreset.CURRENT_WEEK -> today.startOfWeek().atStartOfDay()
        WebhookRangePreset.CURRENT_MONTH -> today.withDayOfMonth(1).atStartOfDay()
        WebhookRangePreset.PREVIOUS_MONTH -> today.minusMonths(1).withDayOfMonth(1).atStartOfDay()
        WebhookRangePreset.LAST_30_DAYS -> today.minusDays(29).atStartOfDay()
        WebhookRangePreset.CUSTOM -> requireNotNull(profile.customStart)
    }
    val end = when (profile.rangePreset) {
        WebhookRangePreset.PREVIOUS_MONTH -> today.withDayOfMonth(1).minusDays(1).atTime(LocalTime.MAX)
        WebhookRangePreset.CUSTOM -> requireNotNull(profile.customEnd)
        else -> now
    }
    return WebhookDateRange(profile.rangePreset, start, end)
}

// A backward clock change invalidates timestamp cursors. Replay before committing a new cursor.
private fun incrementalStart(cursor: LocalDateTime?, now: LocalDateTime): LocalDateTime =
    if (cursor == null || cursor.isAfter(now)) EPOCH else cursor

internal fun webhookTransactionPayload(transaction: TransactionEntity, currency: String = transaction.currency): WebhookTransactionPayload =
    if (transaction.isDeleted || transaction.currency != currency) WebhookTransactionPayload(id = "txn_${transaction.id}", action = "delete", updatedAt = transaction.updatedAt.toString())
    else WebhookTransactionPayload("txn_${transaction.id}", "upsert", transaction.amount.asPlainStringSafe(), transaction.currency,
        transaction.merchantName, transaction.description, transaction.category, null, transaction.transactionType.name,
        transaction.dateTime.toString(), transaction.updatedAt.toString(), transaction.bankName, transaction.accountNumber?.takeLast(4))

internal fun webhookSummary(rows: List<TransactionWithSplits>, currency: String, countCredit: Boolean): WebhookSummaryPayload {
    val selected = rows.filter { it.transaction.currency == currency && !it.transaction.isDeleted && it.transaction.countsInTotals() }
    val income = selected.filter { it.transaction.transactionType == TransactionType.INCOME }.sumOf { it.transaction.amount }
    val expenses = selected.filter { it.transaction.transactionType == TransactionType.EXPENSE ||
        (countCredit && it.transaction.transactionType == TransactionType.CREDIT) }
    val expense = expenses.sumOf { it.transaction.amount }
    val categories = expenses.flatMap { row -> row.getAmountByCategory().map { it.key to it.value } }
        .groupBy({ it.first }, { it.second }).map { (category, amounts) ->
            WebhookCategorySummaryPayload(category, null, amounts.size, amounts.fold(BigDecimal.ZERO, BigDecimal::add).asPlainStringSafe())
        }.sortedByDescending { it.amount.toBigDecimal() }
    return WebhookSummaryPayload(income.asPlainStringSafe(), expense.asPlainStringSafe(), (income - expense).asPlainStringSafe(), currency, categories)
}
