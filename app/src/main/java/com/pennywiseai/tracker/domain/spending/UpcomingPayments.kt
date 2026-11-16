package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import java.time.LocalDate

/**
 * Every payment already committed between today and a window's end, each in its own
 * currency: each occurrence of an active expense subscription and recurring
 * template, and the card bills still to pay.
 */
object UpcomingPayments {

    /**
     * @param cardBills the visible cards' bills. One with something left counts even
     *   when overdue: it's still owed.
     * @param cards every credit card, hidden ones included. A subscription or template
     *   charged to one whose latest bill is entered ([CardBillStatus.isKnown]) is
     *   already inside that bill, so it isn't counted again; on a card with no bill
     *   entered it still counts, or nothing would be set aside for it. Nothing
     *   charged to a hidden card counts.
     * @param posted transactions from just before today on; one that pays a
     *   subscription's occurrence settles it, each at most one.
     * @param isExcluded an account whose payments don't count (ignored, or in another
     *   profile); null accounts never are.
     */
    fun of(
        subscriptions: List<SubscriptionEntity>,
        recurring: List<RecurringTransactionEntity>,
        cardBills: List<CardBillStatus>,
        cards: List<AccountBalanceEntity>,
        posted: List<TransactionEntity>,
        today: LocalDate,
        end: LocalDate,
        isExcluded: (bankName: String?, accountLast4: String?) -> Boolean = { _, _ -> false },
    ): List<Bill> {
        // Cards whose charges are already counted, or never count.
        val covered = cards.filter { c ->
            isExcluded(c.bankName, c.accountLast4) ||
                cardBills.any { it.isKnown && it.card.accountLast4 == c.accountLast4 && it.card.bankName.equals(c.bankName, ignoreCase = true) }
        }
        fun counts(bank: String?, last4: String?) = !onCard(bank, last4, covered) && !isExcluded(bank, last4)
        val charges = posted.filter {
            !it.isDeleted && it.transactionType in RecurringCharge.SPEND_TYPES && !it.dateTime.toLocalDate().isAfter(today)
        }.sortedBy { it.dateTime }.toMutableList()
        val subs = activeExpense(subscriptions).filter { counts(it.bankName, it.accountLast4) }.flatMap { s ->
            occurrences(s, today, end).map { s to Bill(s.merchantName, Money(s.amount, s.currency), it) }
        }
        // Matched against every subscription occurrence, settled or not: a template for a
        // payment that's already posted is paid too.
        val templates = recurring(recurring.filter { counts(it.bankName, it.accountLast4) }, today, end)
            .filter { t -> subs.none { (_, bill) -> bill.sameOccurrence(t) } }
        val unpaid = subs.filter { (s, bill) -> !settled(s, bill.dueDate, charges) }.map { it.second }
        val bills = cardBills.mapNotNull { it.bill }.filter { !it.isRetired && !it.dueDate.isAfter(end) }
            .map { Bill(it.label, Money(it.remaining, it.currency), it.dueDate, isCardBill = true) }
        return (unpaid + templates + bills).sortedBy { it.dueDate }
    }

    /** Each occurrence of the active expense [subscriptions] due today..[end]. */
    fun subscriptions(subscriptions: List<SubscriptionEntity>, today: LocalDate, end: LocalDate): List<Bill> =
        activeExpense(subscriptions).flatMap { s ->
            occurrences(s, today, end).map { Bill(s.merchantName, Money(s.amount, s.currency), it) }
        }

    private fun activeExpense(subscriptions: List<SubscriptionEntity>) =
        subscriptions.filter { it.state == SubscriptionState.ACTIVE && it.direction == SubscriptionDirection.EXPENSE }

    private fun occurrences(s: SubscriptionEntity, today: LocalDate, end: LocalDate): List<LocalDate> =
        generateSequence(s.nextPaymentDate) { RecurringCharge.advance(it, s.billingCycle) }
            .take(MAX_OCCURRENCES)
            .takeWhile { !it.isAfter(end) }
            .filter { !it.isBefore(today) }
            .toList()

    /**
     * Each occurrence of the active expense templates due today..[end]. One already
     * due is the daily worker's to post (it then moves the schedule past today,
     * [RecurringTransactionEntity.nextDueDateAfter]), so the count starts there.
     */
    private fun recurring(templates: List<RecurringTransactionEntity>, today: LocalDate, end: LocalDate): List<Bill> =
        templates
            .filter { it.isActive && it.transactionType == TransactionType.EXPENSE }
            .flatMap { t ->
                val first = if (t.nextDueDate.isBefore(today)) t.nextDueDateAfter(today) else t.nextDueDate
                generateSequence(first) { t.nextDueAfter(it) }
                    .take(MAX_OCCURRENCES)
                    .takeWhile { !it.isAfter(end) }
                    .map { Bill(t.merchantName, Money(t.amount, t.currency), it) }
                    .toList()
            }

    /**
     * Whether a posted charge already paid [s]'s payment due [due]: a spend at the
     * same merchant, currency and amount within [RecurringCharge.settles] of it,
     * after the last payment the subscription recorded. A charge that settles one
     * is taken out of [charges], so it settles no other.
     */
    private fun settled(s: SubscriptionEntity, due: LocalDate, charges: MutableList<TransactionEntity>): Boolean {
        val charge = charges.firstOrNull { t ->
            val on = t.dateTime.toLocalDate()
            t.currency == s.currency &&
                RecurringCharge.sameMerchant(t.merchantName, s.merchantName) &&
                RecurringCharge.sameAmount(t.amount, s.amount) &&
                RecurringCharge.settles(due, on) &&
                (s.lastPaidAt == null || on.isAfter(s.lastPaidAt))
        } ?: return false
        charges.remove(charge)
        return true
    }

    /** A subscription and a template for the same payment: merchant, currency, amount and date all agree. */
    private fun Bill.sameOccurrence(other: Bill): Boolean =
        dueDate == other.dueDate &&
            amount.currency == other.amount.currency &&
            RecurringCharge.sameMerchant(name, other.name) &&
            RecurringCharge.sameAmount(amount.amount, other.amount.amount)

    private fun onCard(bank: String?, last4: String?, cards: List<AccountBalanceEntity>): Boolean =
        last4 != null && cards.any { it.isCreditCard && it.accountLast4 == last4 && (bank == null || it.bankName.equals(bank, ignoreCase = true)) }

    /** A guard against a corrupt schedule; a weekly bill over a long cycle is a handful. */
    private const val MAX_OCCURRENCES = 400
}
