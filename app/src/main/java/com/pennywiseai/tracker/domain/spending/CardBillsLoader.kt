package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** A card's [status], and what Safe to spend sets aside for its bill ([CardBills.reserve]). */
data class CardBillReserve(val status: CardBillStatus, val amount: BigDecimal)

/**
 * Loads [CardBillStatus] for credit cards: the payments made since each entered
 * bill's statement, read from every account (a payment is often filed under the
 * bank account it came from).
 */
class CardBillsLoader @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    fun observe(cards: List<AccountBalanceEntity>, today: LocalDate): Flow<List<CardBillStatus>> =
        transactions(cards, today, purchasesFrom = null).map { transactions ->
            cards.map { CardBills.status(it, transactions, today) }
        }

    /**
     * Each card's status and what Safe to spend sets aside for its bill in the period
     * starting [periodStart]; [purchasesCounted] when card spending counts as an expense.
     */
    fun observeReserves(
        cards: List<AccountBalanceEntity>,
        today: LocalDate,
        periodStart: LocalDate,
        purchasesCounted: Boolean,
    ): Flow<List<CardBillReserve>> =
        transactions(cards, today, purchasesFrom = periodStart.takeIf { purchasesCounted }).map { transactions ->
            cards.map { card ->
                val status = CardBills.status(card, transactions, today)
                val reserve = status.bill?.let { CardBills.reserve(card, it, transactions, periodStart, purchasesCounted) }
                CardBillReserve(status, reserve ?: BigDecimal.ZERO)
            }
        }

    /**
     * Every transaction from the earliest entered bill's statement on, and from
     * [purchasesFrom] when its purchases are needed too. None when no card has a bill.
     */
    private fun transactions(
        cards: List<AccountBalanceEntity>,
        today: LocalDate,
        purchasesFrom: LocalDate?,
    ): Flow<List<TransactionEntity>> {
        val statements = cards.mapNotNull { card ->
            val asOf = card.cardBillAsOf?.toLocalDate() ?: return@mapNotNull null
            card.statementDay?.let { CardBills.statementDate(it, asOf) } ?: asOf
        }
        val since = (statements + listOfNotNull(purchasesFrom.takeIf { statements.isNotEmpty() })).minOrNull()
            ?: return flowOf(emptyList())
        return transactionRepository.getTransactionsBetweenDates(since, today)
    }
}
