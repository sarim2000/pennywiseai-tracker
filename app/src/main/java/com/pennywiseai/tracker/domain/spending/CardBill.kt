package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
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
     * When the latest statement closed, if its bill is entered (paid off or not):
     * what was charged to the card by then is counted through that bill, not one
     * payment at a time. Null when no bill is entered or a newer statement has closed.
     */
    val enteredStatement: LocalDate? = bill?.statementDate,
) {
    /** The latest statement's bill is entered, paid off or not. */
    val isKnown: Boolean get() = enteredStatement != null

    /**
     * The bill the user entered is behind the card: paid off, or a newer statement
     * has closed since. Its amount is no longer the one to edit; the next amount
     * they give is the new statement's.
     */
    val awaitsNewStatement: Boolean
        get() = card.cardDueDay != null && card.cardBillAmount != null && card.cardBillAsOf != null && (bill == null || !isKnown)
}

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
     * that day, and the next statement is a month on). It needs a bill when the card
     * owes something and its due day or amount is missing, or a newer statement has
     * closed since: an unknown bill sets nothing aside, it's never guessed from the
     * card's purchases.
     */
    fun status(card: AccountBalanceEntity, transactions: List<TransactionEntity>, today: LocalDate): CardBillStatus {
        val owes = card.balance.signum() > 0
        val dueDay = card.cardDueDay
        val amount = card.cardBillAmount
        val asOf = card.cardBillAsOf?.toLocalDate()
        if (dueDay == null || amount == null || asOf == null) return CardBillStatus(card, null, needsBill = owes)

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
        val newerStatement = newerStatement(card, statement, today)
        return CardBillStatus(
            card, bill.takeIf { !it.isRetired }, needsBill = owes && newerStatement,
            enteredStatement = statement.takeIf { !newerStatement },
        )
    }

    /**
     * The amount to store when the user saves [dueDay] and [amount], and the time it's
     * dated from (which decides its statement). Left blank on a bill that
     * [CardBillStatus.awaitsNewStatement], the entered bill stays as it was and only
     * the due day changes; an unchanged current bill keeps its statement
     * ([keepsStatement]); any other amount is the latest statement's, dated [now].
     */
    fun toSave(status: CardBillStatus?, dueDay: Int?, amount: BigDecimal?, now: LocalDateTime): Pair<BigDecimal?, LocalDateTime?> {
        val card = status?.card
        return when {
            amount == null && status?.awaitsNewStatement == true -> card?.cardBillAmount to card?.cardBillAsOf
            status != null && keepsStatement(status, dueDay, amount, now.toLocalDate()) -> amount to card?.cardBillAsOf
            else -> amount to amount?.let { now }
        }
    }

    /**
     * Whether saving [amount] with [dueDay] keeps the entered bill on its statement:
     * only when the amount is unchanged and the bill is still the latest statement's
     * and unpaid, or only the due day changed. Once it's paid off or a newer
     * statement has closed, the same figure is the new statement's amount.
     */
    fun keepsStatement(status: CardBillStatus, dueDay: Int?, amount: BigDecimal?, today: LocalDate): Boolean {
        val card = status.card
        val asOf = card.cardBillAsOf?.toLocalDate() ?: return false
        if (amount == null || card.cardBillAmount?.compareTo(amount) != 0) return false
        val statement = card.statementDay?.let { statementDate(it, asOf) } ?: asOf
        if (newerStatement(card, statement, today)) return false
        val paidOff = status.bill == null && card.cardDueDay != null
        return !paidOff || dueDay != card.cardDueDay
    }

    /**
     * Whether a statement after [statement] has closed by [today]. Without a statement
     * day the bill's own date stands in for it, monthly: otherwise a paid bill would
     * stay the card's latest forever, and what's charged to the card since would
     * never be set aside.
     */
    private fun newerStatement(card: AccountBalanceEntity, statement: LocalDate, today: LocalDate): Boolean =
        statementDate(card.statementDay ?: statement.dayOfMonth, today).isAfter(statement)

    private fun YearMonth.dayClamped(day: Int): LocalDate = atDay(day.coerceIn(1, lengthOfMonth()))
}
