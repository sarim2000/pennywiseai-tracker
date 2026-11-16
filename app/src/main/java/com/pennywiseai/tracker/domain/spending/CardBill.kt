package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * One statement's bill on a credit card, as the user entered it, in the card's
 * [currency]. A bill is (card, [statementDate]); it falls due on [dueDate], the
 * card's due day after that statement.
 */
data class CardBill(
    val bankName: String,
    val accountLast4: String,
    val label: String,
    val currency: String,
    val amount: BigDecimal,
    /** Payments to the card since the statement, in [currency]. */
    val paid: BigDecimal,
    val statementDate: LocalDate,
    val dueDate: LocalDate,
) {
    /** What's still to pay; never below zero. */
    val remaining: BigDecimal get() = (amount - paid).max(BigDecimal.ZERO)

    /** Paid off: nothing more to set aside for it. */
    val isRetired: Boolean get() = (amount - paid).signum() <= 0

    fun isOverdue(today: LocalDate): Boolean = !isRetired && dueDate.isBefore(today)
}

/**
 * A credit card as Safe to spend sees it: its [bill] (null when none is known or
 * it's paid off), and whether the user still needs to tell us something
 * ([needsBill]) before its bill can be set aside.
 */
data class CardBillStatus(
    val card: AccountBalanceEntity,
    val bill: CardBill?,
    val needsBill: Boolean,
    /**
     * The latest statement's bill is entered, paid off or not: what's charged to
     * the card is counted through its bills, not one payment at a time.
     */
    val isKnown: Boolean = bill != null,
)

/** Pure rules for a card's bill: statement dates, due dates and payments. */
object CardBills {

    /**
     * The latest statement date on or before [date] for a card whose statement
     * closes on [statementDay] (clamped to short months).
     */
    fun statementDate(statementDay: Int, date: LocalDate): LocalDate {
        val thisMonth = YearMonth.from(date).dayClamped(statementDay)
        return if (!thisMonth.isAfter(date)) thisMonth else YearMonth.from(date).minusMonths(1).dayClamped(statementDay)
    }

    /**
     * The first [dueDay] strictly after [statementDate], clamped to short months: a
     * day-31 card closing on 31 January is due on the last day of February. A
     * statement day is never a due date.
     */
    fun dueDate(statementDate: LocalDate, dueDay: Int): LocalDate {
        val sameMonth = YearMonth.from(statementDate).dayClamped(dueDay)
        return if (sameMonth.isAfter(statementDate)) sameMonth else YearMonth.from(statementDate).plusMonths(1).dayClamped(dueDay)
    }

    /**
     * A payment toward the card: money in, in the card's currency. A transfer into
     * it, or an income row on the card that isn't a credit back for a purchase
     * ([isPurchaseCredit]); spending never is. Each transfer pair counts once,
     * through [paymentsTo].
     */
    fun isPayment(card: AccountBalanceEntity, t: TransactionEntity): Boolean {
        if (t.currency != card.currency) return false
        return when (t.transactionType) {
            TransactionType.INCOME ->
                t.bankName.equals(card.bankName, ignoreCase = true) && t.accountNumber == card.accountLast4 &&
                    (t.transferPairId != null || !isPurchaseCredit(t))
            TransactionType.TRANSFER ->
                t.toAccount == card.accountLast4 &&
                    (t.toBankName == null || t.toBankName.equals(card.bankName, ignoreCase = true))
            else -> false
        }
    }

    /**
     * A refund, reversal or cashback on the card: it lowers what's owed but isn't a
     * payment of the bill. Marked as a refund by the user, or worded as one by the
     * bank; SMS ingestion doesn't mark them.
     */
    private fun isPurchaseCredit(t: TransactionEntity): Boolean =
        t.budgetImpactType == BudgetImpactType.DEDUCT_SPENT ||
            listOfNotNull(t.merchantName, t.category, t.description, t.smsBody).any { PURCHASE_CREDIT.containsMatchIn(it) }

    private val PURCHASE_CREDIT = Regex("""refund|revers|cash\s?back|charge\s?back""", RegexOption.IGNORE_CASE)

    /** What was paid toward [card] after [statementDate], up to and including [today]. */
    fun paymentsTo(card: AccountBalanceEntity, transactions: List<TransactionEntity>, statementDate: LocalDate, today: LocalDate): BigDecimal =
        transactions.asSequence()
            .filter { !it.isDeleted && isPayment(card, it) }
            .filter { it.dateTime.toLocalDate().let { d -> d.isAfter(statementDate) && !d.isAfter(today) } }
            // Both legs of a marked transfer can name the card; the pair is one payment.
            .distinctBy { it.transferPairId ?: "id:${it.id}" }
            .fold(BigDecimal.ZERO) { sum, t -> sum + t.amount }

    /**
     * Where [card]'s bill stands on [today]. The bill the user entered belongs to the
     * statement closed by the time they entered it (or, without a statement day, to
     * that day). It needs a bill when the card owes something and its due day or
     * amount is missing, or a newer statement has closed since: an unknown bill sets
     * nothing aside, it's never guessed from the card's purchases.
     */
    fun status(card: AccountBalanceEntity, transactions: List<TransactionEntity>, today: LocalDate): CardBillStatus {
        val owes = card.balance.signum() > 0
        val dueDay = card.cardDueDay
        val amount = card.cardBillAmount
        val asOf = card.cardBillAsOf?.toLocalDate()
        if (dueDay == null || amount == null || asOf == null) return CardBillStatus(card, null, needsBill = owes, isKnown = false)

        val statement = card.statementDay?.let { statementDate(it, asOf) } ?: asOf
        val bill = CardBill(
            bankName = card.bankName,
            accountLast4 = card.accountLast4,
            label = card.displayLabel,
            currency = card.currency,
            amount = amount,
            paid = paymentsTo(card, transactions, statement, today),
            statementDate = statement,
            dueDate = dueDate(statement, dueDay),
        )
        val newerStatement = card.statementDay?.let { statementDate(it, today).isAfter(statement) } == true
        return CardBillStatus(
            card, bill.takeIf { !it.isRetired }, needsBill = owes && newerStatement, isKnown = !newerStatement,
        )
    }

    private fun YearMonth.dayClamped(day: Int): LocalDate = atDay(day.coerceIn(1, lengthOfMonth()))
}
