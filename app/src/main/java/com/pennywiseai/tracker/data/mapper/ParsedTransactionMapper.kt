package com.pennywiseai.tracker.data.mapper

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Maps ParsedTransaction from parser-core to TransactionEntity
 */
fun ParsedTransaction.toEntity(): TransactionEntity {
    val dateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(timestamp),
        ZoneId.systemDefault()
    )

    // Normalize merchant name to proper case
    val normalizedMerchant = merchant?.let { normalizeMerchantName(it) }

    // Map TransactionType from parser-core to database entity
    val entityType = when (type) {
        com.pennywiseai.parser.core.TransactionType.INCOME -> TransactionType.INCOME
        com.pennywiseai.parser.core.TransactionType.EXPENSE -> TransactionType.EXPENSE
        com.pennywiseai.parser.core.TransactionType.CREDIT -> TransactionType.CREDIT
        com.pennywiseai.parser.core.TransactionType.TRANSFER -> TransactionType.TRANSFER
        com.pennywiseai.parser.core.TransactionType.INVESTMENT -> TransactionType.INVESTMENT
        com.pennywiseai.parser.core.TransactionType.BALANCE_UPDATE -> TransactionType.EXPENSE
    }

    return TransactionEntity(
        id = 0, // Auto-generated
        amount = amount,
        merchantName = normalizedMerchant ?: "Unknown Merchant",
        category = determineCategory(merchant, entityType),
        transactionType = entityType,
        dateTime = dateTime,
        description = null,
        smsBody = smsBody,
        bankName = bankName,
        smsSender = sender,
        accountNumber = accountLast4,
        balanceAfter = balance,
        transactionHash = transactionHash?.takeIf { it.isNotBlank() } ?: generateTransactionId(),
        isRecurring = false, // Will be determined later
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        currency = currency,
        fromAccount = fromAccount,
        toAccount = toAccount,
        reference = reference
    )
}

/** Account-resolution facts stay separate from rule-selected financial values. */
internal data class DeferredBalanceInput(
    val account: ParsedTransaction,
    val type: com.pennywiseai.parser.core.TransactionType,
    val amount: java.math.BigDecimal
) {
    fun forSavedTransaction(resolvedAccount: ParsedTransaction, saved: TransactionEntity): ParsedTransaction? =
        resolvedAccount.forDeferredBalance(saved)?.copy(type = type, amount = amount)
}

/** Capture rule-selected identity before a balance update leaves the save lock. */
internal fun ParsedTransaction.atEnqueuedAccount(
    resolvedInput: ParsedTransaction,
    saved: TransactionEntity
): DeferredBalanceInput? {
    if (saved.currency != currency) return null
    val account = if (isFromCard) {
        // A transaction rule does not change the issuing bank or the card binding.
        this
    } else {
        val selectedBank = saved.bankName ?: return null
        val sameAccount = selectedBank == resolvedInput.bankName &&
            saved.accountNumber == resolvedInput.accountLast4
        copy(
            bankName = selectedBank,
            // Retain the original mask and type for later confirmed alias changes.
            accountLast4 = if (sameAccount) accountLast4 else saved.accountNumber,
            balance = if (sameAccount) balance else null
        )
    }
    val selectedType = if (saved.transactionType == type.toEntityType()) type
        else com.pennywiseai.parser.core.TransactionType.valueOf(saved.transactionType.name)
    return DeferredBalanceInput(account, selectedType, saved.amount)
}

/** Route a queued bank balance to the transaction's current account after edits or merges. */
internal fun ParsedTransaction.forDeferredBalance(saved: TransactionEntity): ParsedTransaction? {
    if (saved.isDeleted || saved.currency != currency) return null
    // A card suffix identifies the card itself; its account binding is resolved separately.
    if (isFromCard) return this
    // Absolute SMS balances belong to the source account. The caller resolves
    // confirmed aliases first; a move anywhere else must not overwrite its target.
    if (bankName != saved.bankName || (accountLast4 != null && accountLast4 != saved.accountNumber)) return null
    return this
}

/**
 * Normalizes merchant name to consistent format.
 * Converts all-caps to proper case, preserves already mixed case.
 */
private fun normalizeMerchantName(name: String): String {
    val trimmed = name.trim()

    // If it's all uppercase, convert to proper case
    return if (trimmed == trimmed.uppercase()) {
        trimmed.lowercase().split(" ").joinToString(" ") { word ->
            if (word.isEmpty()) word else word.substring(0, 1).uppercase() + word.substring(1)
        }
    } else {
        // Already has mixed case, keep as is
        trimmed
    }
}

/**
 * Determines the category based on merchant name and transaction type.
 * Delegates to SharedCategoryMapping (single source of truth).
 */
private fun determineCategory(merchant: String?, type: TransactionType): String {
    val merchantName = merchant ?: return "Others"
    return SharedCategoryMapping.determineCategory(merchantName, type.name)
}

/**
 * Extension to map parser-core TransactionType to database entity TransactionType
 */
fun com.pennywiseai.parser.core.TransactionType.toEntityType(): TransactionType {
    return when (this) {
        com.pennywiseai.parser.core.TransactionType.INCOME -> TransactionType.INCOME
        com.pennywiseai.parser.core.TransactionType.EXPENSE -> TransactionType.EXPENSE
        com.pennywiseai.parser.core.TransactionType.CREDIT -> TransactionType.CREDIT
        com.pennywiseai.parser.core.TransactionType.TRANSFER -> TransactionType.TRANSFER
        com.pennywiseai.parser.core.TransactionType.INVESTMENT -> TransactionType.INVESTMENT
        com.pennywiseai.parser.core.TransactionType.BALANCE_UPDATE -> TransactionType.EXPENSE
    }
}