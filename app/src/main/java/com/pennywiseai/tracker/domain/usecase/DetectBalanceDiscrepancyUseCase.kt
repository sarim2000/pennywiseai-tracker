package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.utils.BalanceDiscrepancy
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Does the balance this SMS transaction reported match what the ledger
 * predicted from the previous snapshot plus every transaction since (#734, #135)?
 * A non-null result means something is missing or double-counted in between.
 */
class DetectBalanceDiscrepancyUseCase @Inject constructor(
    private val accountBalanceRepository: AccountBalanceRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend fun execute(tx: TransactionEntity): BalanceDiscrepancy? {
        val reported = tx.balanceAfter ?: return null
        val bank = tx.bankName ?: return null
        val last4 = tx.accountNumber ?: return null
        // Transfers/credit-card legs don't move a debit balance in a way the
        // calculator can predict; skip rather than cry wolf.
        if (tx.transactionType == TransactionType.TRANSFER || tx.transactionType == TransactionType.CREDIT) return null

        val history = accountBalanceRepository.getBalanceHistoryForAccount(bank, last4) // newest first
        val own = history.firstOrNull { it.transactionId == tx.id }
        if (own?.isCreditCard == true) return null
        val at = own?.timestamp ?: tx.dateTime
        val previous = history.firstOrNull { it.id != own?.id && it.timestamp < at } ?: return null
        if (previous.isCreditCard || previous.currency != tx.currency) return null

        val between = transactionRepository.getTransactionsBetweenDates(previous.timestamp, at).first()
            .filter { it.bankName == bank && it.accountNumber == last4 && it.dateTime > previous.timestamp && it.dateTime <= at }
            .map { it.transactionType to it.amount }
        return BalanceDiscrepancy.compute(previous.balance, between, reported, tx.currency)
    }
}
