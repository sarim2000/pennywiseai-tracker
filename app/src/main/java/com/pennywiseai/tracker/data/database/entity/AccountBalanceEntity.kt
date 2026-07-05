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
    tableName = "account_balances",
    indices = [
        Index(value = ["bank_name", "account_last4", "timestamp"], unique = true),
        Index(value = ["bank_name", "account_last4"]),
        Index(value = ["timestamp"])
    ]
)
@Serializable
data class AccountBalanceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "bank_name")
    val bankName: String,
    
    @ColumnInfo(name = "account_last4")
    val accountLast4: String,
    
    @ColumnInfo(name = "balance")
    @Contextual
    val balance: BigDecimal,

    @ColumnInfo(name = "timestamp")
    @Contextual
    val timestamp: LocalDateTime,
    
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,
    
    @ColumnInfo(name = "credit_limit")
    @Contextual
    val creditLimit: BigDecimal? = null,

    @ColumnInfo(name = "is_credit_card", defaultValue = "0")
    val isCreditCard: Boolean = false,
    
    @ColumnInfo(name = "sms_source")
    val smsSource: String? = null,
    
    @ColumnInfo(name = "source_type")
    val sourceType: String? = null,  // TRANSACTION, SMS_BALANCE, MANUAL, CARD_LINK

    @ColumnInfo(name = "account_type")
    val accountType: String? = null,  // SAVINGS, CURRENT, CREDIT, CASH

    @ColumnInfo(name = "created_at")
    @Contextual
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "statement_day", defaultValue = "NULL")
    val statementDay: Int? = null,

    @ColumnInfo(name = "profile_id", defaultValue = "1")
    val profileId: Long = ProfileEntity.PERSONAL_ID,

    @ColumnInfo(name = "alias")
    val alias: String? = null,

    // Per-account low-balance alert threshold (#509). null = no alert set (off).
    // Account-level (same value on every balance row for a bank_name/account_last4),
    // like alias/profileId. @Contextual so the backup serializer emits the
    // BigDecimal as a plain string and old backups without the key default to null.
    @ColumnInfo(name = "lowBalanceThreshold")
    @Contextual
    val lowBalanceThreshold: BigDecimal? = null
) {
    companion object {
        // Sentinel account_last4 for mobile-money wallets (eMola, M-Pesa
        // Mozambique) whose SMS has a running balance but no per-account number —
        // the whole wallet is one account, keyed on bank_name. The UI renders
        // this as the plain service name (no "••1234" suffix).
        const val WALLET_ACCOUNT_MARKER = "WALLET"
    }
}