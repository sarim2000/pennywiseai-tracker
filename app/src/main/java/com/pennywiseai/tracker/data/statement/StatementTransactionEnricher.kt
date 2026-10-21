package com.pennywiseai.tracker.data.statement

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.time.Duration
import java.time.LocalDateTime

object StatementTransactionEnricher {
    val MATCH_WINDOW: Duration = Duration.ofMinutes(5)

    private val genericValues = setOf(
        "",
        "unknown",
        "unknown merchant",
        "upi",
        "upi transaction",
        "upi payment",
        "upi credit",
        "payment",
        "google pay"
    )

    fun isStatementMatch(existing: TransactionEntity, statement: TransactionEntity): Boolean {
        val existingReference = existing.reference?.trim().orEmpty()
        val statementReference = statement.reference?.trim().orEmpty()
        if (existingReference.isBlank() || existingReference != statementReference) return false
        return hasSameTransactionDetails(existing, statement)
    }

    fun isFallbackStatementMatch(existing: TransactionEntity, statement: TransactionEntity): Boolean {
        if (!hasSameTransactionDetails(existing, statement)) return false
        return hasUsableStatementDetails(existing, statement)
    }

    fun isAmountDateFallbackEnrichmentCandidate(
        existing: TransactionEntity,
        statement: TransactionEntity
    ): Boolean {
        if (existing.amount.compareTo(statement.amount) != 0) return false
        if (existing.currency != statement.currency) return false
        if (existing.transactionType != statement.transactionType) return false
        if (!accountsMatch(existing.accountNumber, statement.accountNumber)) return false
        return hasUsableStatementDetails(existing, statement)
    }

    /**
     * Whether a same-amount transaction near the row's date can be this
     * statement row at all (#13): same currency, same direction, and not on a
     * different account when both name one. A row from another account is
     * someone else's ₹120 coffee; a ₹120 credit is the refund of the debit,
     * not the debit.
     */
    fun isPossibleAmountDateMatch(existing: TransactionEntity, statement: TransactionEntity): Boolean =
        existing.currency == statement.currency &&
            sameDirection(existing, statement) &&
            accountsMatch(existing.accountNumber, statement.accountNumber)

    /**
     * A stored transaction with the statement row's reference that is the same
     * payment: same amount and direction, compatible account, within [window]
     * (an SMS is stamped on arrival, a statement row on its value date).
     */
    fun isReferenceMatch(existing: TransactionEntity, statement: TransactionEntity, window: Duration): Boolean {
        val reference = statement.reference?.trim().orEmpty()
        if (reference.isEmpty() || existing.reference?.trim() != reference) return false
        if (existing.amount.compareTo(statement.amount) != 0) return false
        if (!isPossibleAmountDateMatch(existing, statement)) return false
        return Duration.between(existing.dateTime, statement.dateTime).abs() <= window
    }

    /**
     * Money in vs money out. Only INCOME is money in; a TRANSFER-stored debit
     * still matches a statement debit, so this isn't an exact type comparison.
     */
    private fun sameDirection(existing: TransactionEntity, statement: TransactionEntity): Boolean =
        (existing.transactionType == TransactionType.INCOME) == (statement.transactionType == TransactionType.INCOME)

    private fun hasUsableStatementDetails(
        existing: TransactionEntity,
        statement: TransactionEntity
    ): Boolean {
        return canUseStatementValue(existing.merchantName, statement.merchantName) ||
                canUseStatementValue(existing.description, statement.description) ||
                existing.fromAccount.isNullOrBlank() && !statement.fromAccount.isNullOrBlank() ||
                existing.toAccount.isNullOrBlank() && !statement.toAccount.isNullOrBlank()
    }

    fun enrich(existing: TransactionEntity, statement: TransactionEntity): TransactionEntity {
        var changed = false

        val merchantName = if (canUseStatementValue(existing.merchantName, statement.merchantName)) {
            changed = true
            statement.merchantName
        } else {
            existing.merchantName
        }

        val description = if (canUseStatementValue(existing.description, statement.description)) {
            changed = true
            statement.description
        } else {
            existing.description
        }

        val fromAccount = if (existing.fromAccount.isNullOrBlank() && !statement.fromAccount.isNullOrBlank()) {
            changed = true
            statement.fromAccount
        } else {
            existing.fromAccount
        }

        val toAccount = if (existing.toAccount.isNullOrBlank() && !statement.toAccount.isNullOrBlank()) {
            changed = true
            statement.toAccount
        } else {
            existing.toAccount
        }

        return if (changed) {
            existing.copy(
                merchantName = merchantName,
                description = description,
                fromAccount = fromAccount,
                toAccount = toAccount,
                updatedAt = LocalDateTime.now()
            )
        } else {
            existing
        }
    }

    fun hasEnrichment(existing: TransactionEntity, enriched: TransactionEntity): Boolean =
        existing.merchantName != enriched.merchantName ||
                existing.description != enriched.description ||
                existing.fromAccount != enriched.fromAccount ||
                existing.toAccount != enriched.toAccount

    private fun canUseStatementValue(existing: String?, statement: String?): Boolean {
        val incoming = statement?.trim().orEmpty()
        if (incoming.isBlank() || isGeneric(incoming)) return false

        val current = existing?.trim().orEmpty()
        return current.isBlank() || isGeneric(current)
    }

    private fun hasSameTransactionDetails(
        existing: TransactionEntity,
        statement: TransactionEntity
    ): Boolean {
        if (existing.amount.compareTo(statement.amount) != 0) return false
        if (existing.currency != statement.currency) return false
        if (existing.transactionType != statement.transactionType) return false
        if (!accountsMatch(existing.accountNumber, statement.accountNumber)) return false

        val gap = Duration.between(existing.dateTime, statement.dateTime).abs()
        return gap <= MATCH_WINDOW
    }

    private fun isGeneric(value: String): Boolean =
        value.trim().lowercase() in genericValues

    private fun accountsMatch(existingAccount: String?, statementAccount: String?): Boolean =
        existingAccount.isNullOrBlank() ||
                statementAccount.isNullOrBlank() ||
                existingAccount == statementAccount
}
