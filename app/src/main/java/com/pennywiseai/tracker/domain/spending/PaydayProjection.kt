package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/** Money expected in on [date], in [currency]: an income subscription or recurring income. */
data class ExpectedIncome(val name: String, val currency: String, val date: LocalDate)

/**
 * Whether the money held in one currency covers the bills due before the next income:
 * today's balance less each bill on its due day, up to (not including) [until]. Day-to-day
 * spending isn't projected, only the payments PennyWise knows are coming.
 */
data class PaydayProjection(
    val currency: String,
    /** What savings, current and cash accounts with a known balance hold today. */
    val balance: BigDecimal,
    /** The next [income]'s day, or the day after the cycle ends when none is expected before then. */
    val until: LocalDate,
    /** The income the projection runs up to; null when it runs to the cycle's end. */
    val income: ExpectedIncome?,
    /** The bills counted, in [currency] and by due day. An overdue card bill is due today: it's still owed. */
    val bills: List<Bill>,
    /** The lowest the balance gets before [until], and the day it gets there. */
    val lowest: BigDecimal,
    val lowestOn: LocalDate,
    /** The first day the balance is below zero; null when it never is. */
    val belowZeroFrom: LocalDate?,
    /** Some accounts were left out because their balance is only an estimate. */
    val partial: Boolean = false,
) {
    companion object {
        /** An income further out than this isn't "payday": the projection runs to the cycle's end. */
        const val MAX_DAYS = 45L

        /**
         * One projection per currency held in a liquid account with a known balance
         * ([Runway.isLiquid]); [accounts] are already scoped. [unanchored] keys are left
         * out as [Runway] leaves them, and a currency held only in such accounts has none.
         */
        fun calculate(
            accounts: List<AccountBalanceEntity>,
            unanchored: Set<Pair<String, String>>,
            bills: List<Bill>,
            incomes: List<ExpectedIncome>,
            today: LocalDate,
            cycleEnd: LocalDate,
        ): List<PaydayProjection> =
            accounts.filter(Runway::isLiquid).groupBy { it.currency }.mapNotNull { (currency, held) ->
                val known = held.filter { (it.bankName to it.accountLast4) !in unanchored }
                if (known.isEmpty()) return@mapNotNull null
                val balance = known.fold(BigDecimal.ZERO) { sum, a -> sum + a.balance }
                of(currency, balance, bills, incomes, today, cycleEnd, partial = known.size < held.size)
            }

        /**
         * The projection in [currency] alone: bills and incomes in any other currency are
         * left out, never converted or added. It runs to the first income due today..
         * [MAX_DAYS] ahead, else to the day after [cycleEnd]. A bill due on payday isn't
         * counted: the income pays it.
         */
        fun of(
            currency: String,
            balance: BigDecimal,
            bills: List<Bill>,
            incomes: List<ExpectedIncome>,
            today: LocalDate,
            cycleEnd: LocalDate,
            partial: Boolean = false,
        ): PaydayProjection {
            val income = incomes
                .filter { it.currency == currency && !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(MAX_DAYS)) }
                .minByOrNull { it.date }
            val until = income?.date ?: cycleEnd.plusDays(1)
            val due = bills
                .filter { it.amount.currency == currency && it.dueDate.isBefore(until) && (it.isCardBill || !it.dueDate.isBefore(today)) }
                .map { if (it.dueDate.isBefore(today)) it.copy(dueDate = today) else it }
                .sortedBy { it.dueDate }
            var running = balance
            var lowest = balance
            var lowestOn = today
            var belowZeroFrom = today.takeIf { balance.signum() < 0 }
            for (bill in due) {
                running -= bill.amount.amount
                if (running < lowest) {
                    lowest = running
                    lowestOn = bill.dueDate
                }
                if (belowZeroFrom == null && running.signum() < 0) belowZeroFrom = bill.dueDate
            }
            return PaydayProjection(currency, balance, until, income, due, lowest, lowestOn, belowZeroFrom, partial)
        }

        /**
         * The next payment of each active income subscription and income template, each
         * in its own currency. A template already due is the daily worker's to post, so
         * one overdue counts from its next run after today, as [UpcomingPayments] counts bills.
         */
        fun incomesFrom(
            subscriptions: List<SubscriptionEntity>,
            recurring: List<RecurringTransactionEntity>,
            today: LocalDate,
        ): List<ExpectedIncome> {
            val fromSubscriptions = subscriptions
                .filter { it.state == SubscriptionState.ACTIVE && it.direction == SubscriptionDirection.INCOME }
                .mapNotNull { s ->
                    s.nextPaymentDate?.let {
                        ExpectedIncome(s.merchantName, s.currency, RecurringCharge.nextPaymentShown(it, s.billingCycle, today))
                    }
                }
            val fromTemplates = recurring
                .filter { it.isActive && it.transactionType == TransactionType.INCOME }
                .map { t ->
                    val next = if (t.nextDueDate.isBefore(today)) {
                        generateSequence(t.nextDueAfter(t.nextDueDate)) { t.nextDueAfter(it) }.first { it.isAfter(today) }
                    } else {
                        t.nextDueDate
                    }
                    ExpectedIncome(t.merchantName, t.currency, next)
                }
            return fromSubscriptions + fromTemplates
        }
    }
}
