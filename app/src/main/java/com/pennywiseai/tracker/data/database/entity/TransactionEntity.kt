package com.pennywiseai.tracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["transaction_hash"], unique = true)]
)
@Serializable
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "amount")
    @Contextual
    val amount: BigDecimal,
    
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,
    
    @ColumnInfo(name = "category")
    val category: String,
    
    @ColumnInfo(name = "transaction_type")
    val transactionType: TransactionType,
    
    @ColumnInfo(name = "date_time")
    @Contextual
    val dateTime: LocalDateTime,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "sms_body")
    val smsBody: String? = null,
    
    @ColumnInfo(name = "bank_name")
    val bankName: String? = null,
    
    @ColumnInfo(name = "sms_sender")
    val smsSender: String? = null,
    
    @ColumnInfo(name = "account_number")
    val accountNumber: String? = null,
    
    @ColumnInfo(name = "balance_after")
    @Contextual
    val balanceAfter: BigDecimal? = null,
    
    @ColumnInfo(name = "transaction_hash", defaultValue = "")
    val transactionHash: String,
    
    @ColumnInfo(name = "is_recurring")
    val isRecurring: Boolean = false,
    
    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false,

    // Excluded from spending analytics (trends, averages, category/merchant
    // breakdowns, budgets, AI summaries) when true, but still shown in history
    // and counted toward the account balance — it's real money, only the stats
    // ignore it. Set per-transaction from the detail screen (#451).
    @ColumnInfo(name = "excluded_from_analytics", defaultValue = "0")
    val excludedFromAnalytics: Boolean = false,

    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    @Contextual
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "from_account")
    val fromAccount: String? = null,

    @ColumnInfo(name = "to_account")
    val toAccount: String? = null,

    @ColumnInfo(name = "reference")
    val reference: String? = null,

    @ColumnInfo(name = "loan_id", defaultValue = "NULL")
    val loanId: Long? = null,

    // When this transaction is linked to a loan, the portion of `amount` that
    // is actually a loan. Null means "the full amount is the loan", matching
    // legacy behaviour. Set by the "Mark as loan" sheet when the user wants only
    // part of a payment to count toward the loan total.
    @ColumnInfo(name = "loan_contribution", defaultValue = "NULL")
    @Contextual
    val loanContribution: BigDecimal? = null,

    @ColumnInfo(name = "receipt_path", defaultValue = "NULL")
    val receiptPath: String? = null,

    @ColumnInfo(name = "budget_category", defaultValue = "NULL")
    val budgetCategory: String? = null,

    @ColumnInfo(name = "budget_impact_type", defaultValue = "NULL")
    val budgetImpactType: BudgetImpactType? = null,

    @ColumnInfo(name = "group_id", defaultValue = "NULL")
    val groupId: Long? = null,

    @ColumnInfo(name = "profile_id", defaultValue = "NULL")
    val profileId: Long? = null
)

@Serializable
enum class BudgetImpactType {
    DEDUCT_SPENT,
    ADD_TO_LIMIT
}

@Serializable
enum class TransactionType {
    INCOME,     // Money received
    EXPENSE,    // Money spent from accounts
    CREDIT,     // Credit card purchases
    TRANSFER,   // Between own accounts
    INVESTMENT  // Mutual funds, stocks, etc.
}