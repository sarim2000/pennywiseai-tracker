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

        // A transfer row only records the FROM bank, so an incoming leg can't be
        // tied to a bank. If another bank has an account with the same last four,
        // a TO-leg match would be a guess — leave those transfers out and, since
        // that could itself fake a gap, don't report at all for that account.
        val sameLast4Elsewhere = accountBalanceRepository.getAllLatestBalancesOnce()
            .any { it.accountLast4 == last4 && it.bankName != bank }
        // Same account, same currency (never sum across currencies), strictly after
        // the snapshot and up to the reporting transaction.
        val window = transactionRepository.getTransactionsBetweenDates(previous.timestamp, at).first()
            .filter { it.currency == tx.currency && it.dateTime > previous.timestamp && it.dateTime <= at }
        val transfersIn = window.filter { it.transactionType == TransactionType.TRANSFER && it.toAccount == last4 && it.fromAccount != last4 }
        if (sameLast4Elsewhere && transfersIn.isNotEmpty()) return null
        val effects = window
            .filter {
                (it.bankName == bank && it.accountNumber == last4) ||
                    (it.transactionType == TransactionType.TRANSFER && it.bankName == bank && it.fromAccount == last4) ||
                    it in transfersIn
            }
            .distinct()
            .map { BalanceDiscrepancy.effectOn(it, last4) }
        return BalanceDiscrepancy.compute(previous.balance, effects, reported, tx.currency, since = previous.timestamp)
    }
}
