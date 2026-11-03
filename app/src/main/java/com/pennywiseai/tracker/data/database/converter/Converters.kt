package com.pennywiseai.tracker.data.database.converter

import androidx.room3.ColumnTypeConverter
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.WebhookDataType
import com.pennywiseai.tracker.data.database.entity.WebhookLogStatus
import com.pennywiseai.tracker.data.database.entity.WebhookRangePreset
import com.pennywiseai.tracker.data.webhook.WebhookSyncReason
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    // Handle both ISO format (2025-08-01T18:17:16) and space format (2025-08-01 18:17:16)
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val dateTimeFormatterWithSpace = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    @ColumnTypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toString()
    }
    
    @ColumnTypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return value?.let { BigDecimal(it) }
    }
    
    @ColumnTypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        return value?.format(dateTimeFormatter)
    }
    
    @ColumnTypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { dateStr ->
            try {
                // Try ISO format first (with 'T')
                LocalDateTime.parse(dateStr, dateTimeFormatter)
            } catch (e: Exception) {
                // Fall back to space format
                LocalDateTime.parse(dateStr, dateTimeFormatterWithSpace)
            }
        }
    }
    
    @ColumnTypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.format(dateFormatter)
    }
    
    @ColumnTypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { dateString ->
            // Handle invalid dates
            if (dateString == "0000-00-00" || dateString.isBlank()) {
                return null
            }
            try {
                LocalDate.parse(dateString, dateFormatter)
            } catch (e: Exception) {
                android.util.Log.w("Converters", "Invalid date format: $dateString", e)
                null
            }
        }
    }
    
    @ColumnTypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }
    
    @ColumnTypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }
    
    @ColumnTypeConverter
    fun fromSubscriptionState(value: SubscriptionState): String {
        return value.name
    }

    @ColumnTypeConverter
    fun toSubscriptionState(value: String): SubscriptionState {
        return SubscriptionState.valueOf(value)
    }

    @ColumnTypeConverter
    fun fromSubscriptionDirection(value: SubscriptionDirection): String {
        return value.name
    }

    @ColumnTypeConverter
    fun toSubscriptionDirection(value: String): SubscriptionDirection {
        return SubscriptionDirection.valueOf(value)
    }

    @ColumnTypeConverter
    fun fromBudgetPeriodType(value: BudgetPeriodType): String {
        return value.name
    }

    @ColumnTypeConverter
    fun toBudgetPeriodType(value: String): BudgetPeriodType {
        return BudgetPeriodType.valueOf(value)
    }

    @ColumnTypeConverter
    fun fromBudgetGroupType(value: BudgetGroupType): String {
        return value.name
    }

    @ColumnTypeConverter
    fun toBudgetGroupType(value: String): BudgetGroupType {
        return BudgetGroupType.valueOf(value)
    }

    @ColumnTypeConverter
    fun fromBudgetImpactType(value: BudgetImpactType?): String? {
        return value?.name
    }

    @ColumnTypeConverter
    fun toBudgetImpactType(value: String?): BudgetImpactType? {
        return value?.let { BudgetImpactType.valueOf(it) }
    }

    @ColumnTypeConverter
    fun fromLoanDirection(value: LoanDirection): String {
        return value.name
    }

    @ColumnTypeConverter
    fun toLoanDirection(value: String): LoanDirection {
        return LoanDirection.valueOf(value)
    }

    @ColumnTypeConverter
    fun fromLoanStatus(value: LoanStatus): String {
        return value.name
    }

    @ColumnTypeConverter
    fun toLoanStatus(value: String): LoanStatus {
        return LoanStatus.valueOf(value)
    }

    @ColumnTypeConverter fun fromWebhookDataType(value: WebhookDataType): String = value.name
    @ColumnTypeConverter fun toWebhookDataType(value: String): WebhookDataType = WebhookDataType.valueOf(value)
    @ColumnTypeConverter fun fromWebhookLogStatus(value: WebhookLogStatus): String = value.name
    @ColumnTypeConverter fun toWebhookLogStatus(value: String): WebhookLogStatus = WebhookLogStatus.valueOf(value)
    @ColumnTypeConverter fun fromWebhookRangePreset(value: WebhookRangePreset): String = value.name
    @ColumnTypeConverter fun toWebhookRangePreset(value: String): WebhookRangePreset = WebhookRangePreset.valueOf(value)
    @ColumnTypeConverter fun fromWebhookSyncReason(value: WebhookSyncReason): String = value.name
    @ColumnTypeConverter fun toWebhookSyncReason(value: String): WebhookSyncReason = WebhookSyncReason.valueOf(value)
}
