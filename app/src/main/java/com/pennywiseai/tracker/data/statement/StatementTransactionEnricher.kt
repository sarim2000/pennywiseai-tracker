package com.pennywiseai.tracker.data.statement

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
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
