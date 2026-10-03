package com.pennywiseai.tracker.data.webhook

import com.pennywiseai.tracker.data.database.entity.WebhookDataType
import com.pennywiseai.tracker.data.database.entity.WebhookRangePreset
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class WebhookSyncReason {
    MANUAL,
    INTERVAL,
    SCHEDULED,
    TEST
}

@Serializable
data class WebhookHeader(
    val key: String,
    val value: String
)

data class WebhookDateRange(
    val preset: WebhookRangePreset,
    val start: LocalDateTime,
    val end: LocalDateTime
)

data class WebhookProfileDraft(
    val id: String? = null,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val dataTypes: Set<WebhookDataType>,
    val rangePreset: WebhookRangePreset,
    val customStart: LocalDateTime? = null,
    val customEnd: LocalDateTime? = null,
    val currency: String,
    val headers: List<WebhookHeader>
)

@Serializable
data class WebhookEnvelope(
    @SerialName("schema_version")
    val schemaVersion: String = "1.0",
    @SerialName("generated_at")
    val generatedAt: String,
    val app: WebhookAppInfo,
    val profile: WebhookProfileInfo,
    val request: WebhookRequestInfo,
    val batch: WebhookBatchInfo,
    val summary: WebhookSummaryPayload? = null,
    val transactions: List<WebhookTransactionPayload> = emptyList(),
    val budgets: List<WebhookBudgetPayload> = emptyList(),
    val accounts: List<WebhookAccountPayload> = emptyList(),
    val subscriptions: List<WebhookSubscriptionPayload> = emptyList()
)

@Serializable
data class WebhookAppInfo(
    val name: String,
    val version: String
)

@Serializable
data class WebhookProfileInfo(
    val id: String,
    val name: String
)

@Serializable
data class WebhookRequestInfo(
    val range: String,
    val start: String,
    val end: String,
    val currency: String,
    @SerialName("data_types")
    val dataTypes: List<String>
)

@Serializable
data class WebhookBatchInfo(
    val id: String,
    val index: Int,
    val count: Int
)

@Serializable
data class WebhookSummaryPayload(
    @SerialName("total_income")
    val totalIncome: String,
    @SerialName("total_expense")
    val totalExpense: String,
    @SerialName("net_amount")
    val netAmount: String,
    val currency: String,
    val categories: List<WebhookCategorySummaryPayload>
)

@Serializable
data class WebhookCategorySummaryPayload(
    val category: String,
    val subcategory: String? = null,
    @SerialName("transaction_count")
    val transactionCount: Int,
    val amount: String
)

@Serializable
data class WebhookTransactionPayload(
    val id: String,
    val action: String,
    val amount: String? = null,
    val currency: String? = null,
    val merchant: String? = null,
    val description: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    @SerialName("transaction_type")
    val transactionType: String? = null,
    @SerialName("date_time")
    val dateTime: String? = null,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("bank_name")
    val bankName: String? = null,
    @SerialName("account_last4")
    val accountLast4: String? = null
)

@Serializable
data class WebhookBudgetPayload(
    val id: String,
    val name: String,
    val amount: String,
    val currency: String,
    @SerialName("period_type")
    val periodType: String,
    @SerialName("budget_type")
    val budgetType: String,
    @SerialName("start_date")
    val startDate: String,
    @SerialName("end_date")
    val endDate: String,
    val spending: String,
    val categories: List<WebhookBudgetCategoryPayload>
)

@Serializable
data class WebhookBudgetCategoryPayload(
    val category: String,
    val limit: String
)

@Serializable
data class WebhookAccountPayload(
    val id: String,
    @SerialName("bank_name")
    val bankName: String,
    @SerialName("account_last4")
    val accountLast4: String,
    val balance: String,
    val currency: String,
    @SerialName("credit_limit")
    val creditLimit: String? = null,
    @SerialName("is_credit_card")
    val isCreditCard: Boolean,
    @SerialName("is_wallet")
    val isWallet: Boolean,
    val timestamp: String
)

@Serializable
data class WebhookSubscriptionPayload(
    val id: String,
    val merchant: String,
    val amount: String,
    val currency: String,
    val state: String,
    val category: String? = null,
    val subcategory: String? = null,
    @SerialName("billing_cycle")
    val billingCycle: String? = null,
    @SerialName("next_payment_date")
    val nextPaymentDate: String? = null,
    @SerialName("last_paid_date")
    val lastPaidDate: String? = null
)

data class WebhookBatchPayload(
    val envelope: WebhookEnvelope,
    val cursorUpdates: List<WebhookCursorUpdate>,
    val itemCount: Int
)

data class WebhookCursorUpdate(
    val dataType: WebhookDataType,
    val successAt: LocalDateTime,
    val rangeEnd: LocalDateTime
)

data class WebhookAttemptResult(
    val success: Boolean,
    val httpStatus: Int? = null,
    val message: String,
    val retryable: Boolean = false
)

internal fun LocalDate.startOfWeek(): LocalDate {
    var current = this
    while (current.dayOfWeek != DayOfWeek.MONDAY) {
        current = current.minusDays(1)
    }
    return current
}

internal fun BigDecimal.asPlainStringSafe(): String = stripTrailingZeros().toPlainString()
