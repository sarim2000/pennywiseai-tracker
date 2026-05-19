package com.pennywiseai.tracker.data.manager

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import java.time.Duration

object TransactionDeduplication {
    private val upiReferencePattern = Regex("""\d{12}""")
    val UPI_DUPLICATE_WINDOW: Duration = Duration.ofMinutes(3)

    fun hasUpiReference(transaction: TransactionEntity): Boolean =
        transaction.reference?.let { upiReferencePattern.matches(it) } == true

    fun isSameUpiTransaction(
        existing: TransactionEntity,
        incoming: TransactionEntity,
        window: Duration = UPI_DUPLICATE_WINDOW
    ): Boolean {
        if (!hasUpiReference(existing) || !hasUpiReference(incoming)) return false
        if (existing.reference != incoming.reference) return false
        if (existing.transactionType != incoming.transactionType) return false
        if (existing.currency != incoming.currency) return false
        if (existing.amount.compareTo(incoming.amount) != 0) return false
        if (!accountsMatch(existing.accountNumber, incoming.accountNumber)) return false

        val gap = Duration.between(existing.dateTime, incoming.dateTime).abs()
        return gap <= window
    }

    fun shouldReplaceWithIncoming(
        existing: TransactionEntity,
        incoming: TransactionEntity
    ): Boolean {
        if (!isSameUpiTransaction(existing, incoming)) return false

        val existingIsPartnerBank = existing.bankName.equals("State Bank of India", ignoreCase = true)
        val incomingIsPartnerBank = incoming.bankName.equals("State Bank of India", ignoreCase = true)
        if (existingIsPartnerBank && !incomingIsPartnerBank) return true
        if (!existingIsPartnerBank && incomingIsPartnerBank) return false

        return existing.balanceAfter == null && incoming.balanceAfter != null
    }

    fun duplicateIdsToDelete(transactions: List<TransactionEntity>): List<Long> {
        return transactions
            .filter { !it.isDeleted && hasUpiReference(it) }
            .groupBy {
                DuplicateKey(
                    reference = it.reference.orEmpty(),
                    amount = it.amount.stripTrailingZeros().toPlainString(),
                    accountNumber = it.accountNumber.orEmpty(),
                    transactionType = it.transactionType.name,
                    currency = it.currency
                )
            }
            .values
            .flatMap { group -> duplicateIdsFromGroup(group) }
    }

    private fun duplicateIdsFromGroup(group: List<TransactionEntity>): List<Long> {
        val duplicateClusters = mutableListOf<MutableList<TransactionEntity>>()

        group.sortedWith(compareBy<TransactionEntity> { it.dateTime }.thenBy { it.id })
            .forEach { transaction ->
                val matchingCluster = duplicateClusters.firstOrNull { cluster ->
                    cluster.any { previous -> isSameUpiTransaction(previous, transaction) }
                }

                if (matchingCluster == null) {
                    duplicateClusters += mutableListOf(transaction)
                } else {
                    matchingCluster += transaction
                }
            }

        return duplicateClusters.flatMap { cluster ->
            val keeper = cluster.minWith(transactionQualityComparator)
            cluster
                .filter { it.id != keeper.id }
                .sortedWith(compareBy<TransactionEntity> { it.dateTime }.thenBy { it.id })
                .map { it.id }
        }
    }

    private val transactionQualityComparator = compareBy<TransactionEntity>(
        { it.bankName.equals("State Bank of India", ignoreCase = true) },
        { it.balanceAfter == null },
        { it.dateTime },
        { it.id }
    )

    private fun accountsMatch(existingAccount: String?, incomingAccount: String?): Boolean {
        return existingAccount.isNullOrBlank() ||
                incomingAccount.isNullOrBlank() ||
                existingAccount == incomingAccount
    }

    private data class DuplicateKey(
        val reference: String,
        val amount: String,
        val accountNumber: String,
        val transactionType: String,
        val currency: String
    )
}
