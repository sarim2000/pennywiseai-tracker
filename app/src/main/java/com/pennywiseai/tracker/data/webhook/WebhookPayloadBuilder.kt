package com.pennywiseai.tracker.data.webhook

import com.pennywiseai.tracker.BuildConfig
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.*
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
        // SQL mutation timestamps have millisecond precision; keep inclusive cursors at that precision too.
        val now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        if (test) return listOf(testPayload(profile, types, now))
        val range = resolveWebhookRange(profile, types, cursors, now)
        val transactionRange = if (profile.rangePreset == WebhookRangePreset.SINCE_LAST_SUCCESS) {
            range.copy(start = cursors.firstOrNull { it.dataType == WebhookDataType.TRANSACTIONS }?.lastSuccessAt ?: EPOCH)
        } else range
        val rows = if (WebhookDataType.TRANSACTIONS in types) {
            val entities = if (range.preset == WebhookRangePreset.SINCE_LAST_SUCCESS) {
                transactions.getWebhookChanges(transactionRange.start, transactionRange.end, profile.currency)
            } else {
                transactions.getWebhookTransactions(range.start, range.end, profile.currency).first()
            }
            entities.map(::webhookTransactionPayload)
        } else emptyList()
        val summary = if (WebhookDataType.SUMMARY in types) summary(range, profile.currency) else null
        val budgetPayloads = if (WebhookDataType.BUDGETS in types) budgetPayloads(profile.currency, now.toLocalDate()) else emptyList()
        val accountPayloads = if (WebhookDataType.ACCOUNTS in types) accountPayloads(profile.currency) else emptyList()
        val subscriptionPayloads = if (WebhookDataType.SUBSCRIPTIONS in types) subscriptionPayloads(profile.currency) else emptyList()
        val batches = rows.chunked(250).ifEmpty { listOf(emptyList()) }
        val batchId = UUID.randomUUID().toString()
        // A date-range export must not advance the updated-at cursor used by incremental exports.
        val updates = if (profile.rangePreset == WebhookRangePreset.SINCE_LAST_SUCCESS) {
            types.map { WebhookCursorUpdate(it, now, range.end) }
        } else emptyList()
        return batches.mapIndexed { index, batch ->
            WebhookBatchPayload(
                envelope = WebhookEnvelope(
                    generatedAt = now.toString(),
                    app = WebhookAppInfo("PennyWise", BuildConfig.VERSION_NAME),
                    profile = WebhookProfileInfo(profile.id, profile.name),
                    request = WebhookRequestInfo(range.preset.name.lowercase(), range.start.toString(), range.end.toString(),
                        profile.currency, types.map { it.name.lowercase() }),
                    batch = WebhookBatchInfo(batchId, index + 1, batches.size),
                    summary = if (index == 0) summary else null,
                    transactions = batch,
                    budgets = if (index == 0) budgetPayloads else emptyList(),
                    accounts = if (index == 0) accountPayloads else emptyList(),
                    subscriptions = if (index == 0) subscriptionPayloads else emptyList()
                ),
                cursorUpdates = updates,
                itemCount = batch.size + if (index == 0) {
                    budgetPayloads.size + accountPayloads.size + subscriptionPayloads.size + if (summary != null) 1 else 0
                } else 0
            )
        }
    }

    private suspend fun summary(range: WebhookDateRange, currency: String): WebhookSummaryPayload {
        val rows = splits.getTransactionsWithSplitsFiltered(range.start, range.end, currency).first()
            .filterNot { it.transaction.excludedFromAnalytics }
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
                it.accountType.equals("CASH", true), it.timestamp.toString())
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
            cursors.firstOrNull { it.dataType == type }?.lastSuccessAt ?: EPOCH
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

internal fun webhookTransactionPayload(transaction: TransactionEntity): WebhookTransactionPayload =
    if (transaction.isDeleted) WebhookTransactionPayload(id = "txn_${transaction.id}", action = "delete", updatedAt = transaction.updatedAt.toString())
    else WebhookTransactionPayload("txn_${transaction.id}", "upsert", transaction.amount.asPlainStringSafe(), transaction.currency,
        transaction.merchantName, transaction.description, transaction.category, null, transaction.transactionType.name,
        transaction.dateTime.toString(), transaction.updatedAt.toString(), transaction.bankName, transaction.accountNumber?.takeLast(4))

internal fun webhookSummary(rows: List<TransactionWithSplits>, currency: String, countCredit: Boolean): WebhookSummaryPayload {
    val selected = rows.filter { it.transaction.currency == currency && !it.transaction.isDeleted && !it.transaction.excludedFromAnalytics }
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
