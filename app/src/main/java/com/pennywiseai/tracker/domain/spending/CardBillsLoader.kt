package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Loads [CardBillStatus] for credit cards: the payments made since each entered
 * bill's statement, read from every account (a payment is often filed under the
 * bank account it came from).
 */
class CardBillsLoader @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    fun observe(cards: List<AccountBalanceEntity>, today: LocalDate): Flow<List<CardBillStatus>> {
        val since = cards.mapNotNull { card ->
            val asOf = card.cardBillAsOf?.toLocalDate() ?: return@mapNotNull null
            card.statementDay?.let { CardBills.statementDate(it, asOf) } ?: asOf
        }.minOrNull() ?: return flowOf(cards.map { CardBills.status(it, emptyList(), today) })
        return transactionRepository.getTransactionsBetweenDates(since, today)
            .map { transactions -> cards.map { CardBills.status(it, transactions, today) } }
    }
}
