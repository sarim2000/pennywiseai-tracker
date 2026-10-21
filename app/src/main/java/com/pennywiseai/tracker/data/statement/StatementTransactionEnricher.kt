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
     * payment: same amount, currency and direction, within [window] (an SMS is
     * stamped on arrival, a statement row on its value date). The reference
     * identifies the payment, so the account isn't compared: an SMS and a
     * statement often print different parts of it.
     */
    fun isReferenceMatch(existing: TransactionEntity, statement: TransactionEntity, window: Duration): Boolean {
        val reference = normalizedReference(statement.reference) ?: return false
        if (normalizedReference(existing.reference) != reference) return false
        if (existing.amount.compareTo(statement.amount) != 0) return false
        if (existing.currency != statement.currency || !sameDirection(existing, statement)) return false
        return Duration.between(existing.dateTime, statement.dateTime).abs() <= window
    }

    /**
     * A reference as compared: zero-padding dropped ("0000412345678901" is
     * "412345678901"); null when there's nothing left (blank or all zeros).
     */
    fun normalizedReference(reference: String?): String? =
        reference?.trim()?.trimStart('0')?.takeIf { it.isNotEmpty() }

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

    /**
     * Unknown on either side is compatible. Otherwise the digits must agree on
     * at least the last two, one ending the other: Paytm prints "17" for the
     * SMS's "4517", Google Pay "0123" for an SMS's "123".
     */
    internal fun accountsMatch(existingAccount: String?, statementAccount: String?): Boolean {
        if (existingAccount.isNullOrBlank() || statementAccount.isNullOrBlank()) return true
        val a = existingAccount.filter(Char::isDigit)
        val b = statementAccount.filter(Char::isDigit)
        if (a.isEmpty() || b.isEmpty()) return existingAccount.trim().equals(statementAccount.trim(), ignoreCase = true)
        if (a == b) return true
        return minOf(a.length, b.length) >= 2 && (a.endsWith(b) || b.endsWith(a))
    }
}
