package com.pennywiseai.tracker.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

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
    val sourceType: String? = null, // TRANSACTION, SMS_BALANCE, MANUAL, CARD_LINK

    @ColumnInfo(name = "account_type")
    val accountType: String? = null, // SAVINGS, CURRENT, CREDIT, CASH, LOAN

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
    val lowBalanceThreshold: BigDecimal? = null,

    // A credit card's bill, as the user entered it. Account-level like alias:
    // the same on every row of the account, null on legacy rows ("unknown").
    /** Day of the month the card's bill is due, 1–31. Never the statement day. */
    @ColumnInfo(name = "card_due_day", defaultValue = "NULL")
    val cardDueDay: Int? = null,

    /** The last statement's amount, in [currency]. */
    @ColumnInfo(name = "card_bill_amount", defaultValue = "NULL")
    @Contextual
    val cardBillAmount: BigDecimal? = null,

    /** When [cardBillAmount] was entered: it belongs to the statement closed by then. */
    @ColumnInfo(name = "card_bill_as_of", defaultValue = "NULL")
    @Contextual
    val cardBillAsOf: LocalDateTime? = null
) {
    /**
     * Label to show wherever the user picks or reads this account: the user-set
     * [alias] when one exists, otherwise [accountLabel]. An account renamed in
     * Manage Accounts must be recognizable under that name everywhere — pickers
     * that ignored the alias were the bug in #637.
     */
    val displayLabel: String
        get() = alias?.takeIf { it.isNotBlank() } ?: accountLabel(bankName, accountLast4)

    /** A bank loan account (home, car, personal loan) — not the person-to-person Loans feature. (#792) */
    val isLoan: Boolean
        get() = accountType.equals(LOAN_TYPE, ignoreCase = true)

    /**
     * The balance is an amount owed, not money held: credit cards and loan
     * accounts. Spending raises it, a credit/payment lowers it, and it stays out
     * of the spendable bank total. Drives every balance-sign and total decision,
     * so a loan follows the credit-card rules without its own copy of them. (#792)
     */
    val isLiability: Boolean
        get() = isCreditCard || isLoan

    /**
     * This row carrying the account-level fields the user set (statement and due day,
     * card bill) from [from], an earlier row of the same account.
     * Every path that builds a fresh row for an existing account goes through this,
     * so a new SMS or a manual edit can't drop them. A bill amount is in the old
     * row's currency, so a row in another currency drops it (the due day stays):
     * INR 4000 isn't USD 4000, and the card asks for the bill again.
     */
    fun withAccountFieldsOf(from: AccountBalanceEntity?): AccountBalanceEntity =
        if (from == null) {
            this
        } else {
            val sameCurrency = from.currency == currency
            copy(
                statementDay = from.statementDay,
                cardDueDay = from.cardDueDay,
                cardBillAmount = from.cardBillAmount.takeIf { sameCurrency },
                cardBillAsOf = from.cardBillAsOf.takeIf { sameCurrency },
            )
        }

    companion object {
        // Sentinel account_last4 for mobile-money wallets (eMola, M-Pesa
        // Mozambique) whose SMS has a running balance but no per-account number —
        // the whole wallet is one account, keyed on bank_name. The UI renders
        // this as the plain service name (no "••1234" suffix).
        const val WALLET_ACCOUNT_MARKER = "WALLET"

        /** Stored `account_type` of a bank loan account; see [isLoan]. */
        const val LOAN_TYPE = "LOAN"

        /**
         * Human-readable label for an account. Mobile-money wallets have no
         * account number (and a blank last4 is meaningless), so show just the
         * name; otherwise "Name ••1234". Use this everywhere an account is
         * labelled so the [WALLET_ACCOUNT_MARKER] never leaks into the UI.
         */
        fun accountLabel(name: String, accountLast4: String?): String =
            if (accountLast4.isNullOrBlank() || accountLast4 == WALLET_ACCOUNT_MARKER) {
                name
            } else {
                "$name ••$accountLast4"
            }
    }
}
