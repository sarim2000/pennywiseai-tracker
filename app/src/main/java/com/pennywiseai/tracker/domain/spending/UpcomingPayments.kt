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
import java.time.temporal.ChronoUnit

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
     *   payment charged to a card by the close of its entered statement
     *   ([CardBillStatus.enteredStatement]) is already inside that bill, so it isn't
     *   counted again; one charged after it, or to a card with no bill entered, still
     *   counts, or nothing would be set aside for it. Nothing charged to a hidden
     *   card counts.
     * @param posted transactions from just before today on; one that pays a
     *   subscription's occurrence settles it, each at most one. Only a charge in
     *   scope can, and only one that could come from the subscription's account
     *   ([RecurringCharge.couldBePaidFrom]).
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
        // Cards whose charges never count, and each card's entered statement.
        val hidden = cards.filter { isExcluded(it.bankName, it.accountLast4) }
        val entered = cardBills.mapNotNull { s -> s.enteredStatement?.let { s.card to it } }
        fun counts(bank: String?, last4: String?, on: LocalDate) =
            !isExcluded(bank, last4) && !onCard(bank, last4, hidden) &&
                entered.none { (card, closed) -> !on.isAfter(closed) && onCard(bank, last4, listOf(card)) }
        val subs = occurrences(subscriptions, today, end) { s, on -> counts(s.bankName, s.accountLast4, on) }
        // Matched against every subscription occurrence, settled or not: a template for a
        // payment that's already posted is paid too.
        val templates = recurring(recurring, today, end) { t, on -> counts(t.bankName, t.accountLast4, on) }
            .filter { t -> subs.none { (_, bill) -> bill.sameOccurrence(t) } }
        // A charge on an ignored account, or another profile's, pays nothing in this scope.
        val unpaid = unpaid(subs, posted.filter { !isExcluded(it.bankName, it.accountNumber) }, today)
        val bills = cardBills.mapNotNull { it.bill }.filter { !it.isRetired && !it.dueDate.isAfter(end) }
            .map { Bill(it.label, Money(it.remaining, it.currency), it.dueDate, isCardBill = true) }
        return (unpaid + templates + bills).sortedBy { it.dueDate }
    }

    /**
     * Each occurrence of the active expense [subscriptions] due today..[end] that
     * none of the [posted] transactions has paid yet. Insights' bills due reads this
     * and Safe to spend the same rule, so the two always agree.
     */
    fun subscriptions(
        subscriptions: List<SubscriptionEntity>,
        posted: List<TransactionEntity>,
        today: LocalDate,
        end: LocalDate,
    ): List<Bill> = unpaid(occurrences(subscriptions, today, end) { _, _ -> true }, posted, today)

    /** Each occurrence of the active expense [subscriptions] due today..[end] that [counts]. */
    private fun occurrences(
        subscriptions: List<SubscriptionEntity>,
        today: LocalDate,
        end: LocalDate,
        counts: (SubscriptionEntity, LocalDate) -> Boolean,
    ): List<Pair<SubscriptionEntity, Bill>> =
        subscriptions
            .filter { it.state == SubscriptionState.ACTIVE && it.direction == SubscriptionDirection.EXPENSE }
            .flatMap { s ->
                val scheduled = s.nextPaymentDate ?: return@flatMap emptyList()
                // Each counted from the scheduled date, as RecurringCharge.nextPaymentShown
                // counts, so a bill due on the 31st comes back to the 31st after February.
                generateSequence(0L) { it + 1 }.map { RecurringCharge.advance(scheduled, s.billingCycle, it) }
                    .take(MAX_OCCURRENCES)
                    .takeWhile { !it.isAfter(end) }
                    .filter { !it.isBefore(today) && counts(s, it) }
                    .map { s to Bill(s.merchantName, Money(s.amount, s.currency), it) }
                    .toList()
            }

    /**
     * The [occurrences] no spend in [posted] up to [today] has paid ([settled]).
     * Subscriptions on a known account claim a charge first.
     */
    private fun unpaid(occurrences: List<Pair<SubscriptionEntity, Bill>>, posted: List<TransactionEntity>, today: LocalDate): List<Bill> {
        val charges = posted.filter {
            !it.isDeleted && it.transactionType in RecurringCharge.SPEND_TYPES && !it.dateTime.toLocalDate().isAfter(today)
        }.sortedBy { it.dateTime }.toMutableList()
        val paid = occurrences.sortedByDescending { (s, _) -> s.knownAccountParts() }
            .filter { (s, bill) -> settled(s, bill.dueDate, charges) }
            .toSet()
        return occurrences.filter { it !in paid }.map { it.second }
    }

    /**
     * Each occurrence of the active expense templates due today..[end]. One already
     * due is the daily worker's to post (it then moves the schedule past today,
     * [RecurringTransactionEntity.nextDueDateAfter]), so the count starts there.
     */
    private fun recurring(
        templates: List<RecurringTransactionEntity>,
        today: LocalDate,
        end: LocalDate,
        counts: (RecurringTransactionEntity, LocalDate) -> Boolean,
    ): List<Bill> =
        templates
            .filter { it.isActive && it.transactionType == TransactionType.EXPENSE }
            .flatMap { t ->
                val first = if (t.nextDueDate.isBefore(today)) t.nextDueDateAfter(today) else t.nextDueDate
                generateSequence(first) { t.nextDueAfter(it) }
                    .take(MAX_OCCURRENCES)
                    .takeWhile { !it.isAfter(end) }
                    .filter { counts(t, it) }
                    .map { Bill(t.merchantName, Money(t.amount, t.currency), it) }
                    .toList()
            }

    /**
     * Whether a posted charge already paid [s]'s payment due [due]: a spend from its
     * account at the same merchant and currency within [RecurringCharge.settles] of
     * it, after the last payment the subscription recorded. The amount matches, or
     * is the bill at a new price ([RecurringCharge.isPriceChange]): the charge still
     * paid it, even before the subscription was linked or updated. The charge nearest
     * the due date settles it and is taken out of [charges], so it settles no other.
     */
    private fun settled(s: SubscriptionEntity, due: LocalDate, charges: MutableList<TransactionEntity>): Boolean {
        val charge = charges.filter { t ->
            val on = t.dateTime.toLocalDate()
            t.currency == s.currency &&
                RecurringCharge.couldBePaidFrom(s, t) &&
                RecurringCharge.sameMerchant(t.merchantName, s.merchantName) &&
                (RecurringCharge.sameAmount(t.amount, s.amount) || RecurringCharge.isPriceChange(s.amount, due, t.amount, on)) &&
                RecurringCharge.settles(due, on) &&
                (s.lastPaidAt == null || on.isAfter(s.lastPaidAt))
        }.minByOrNull { Math.abs(ChronoUnit.DAYS.between(due, it.dateTime.toLocalDate())) } ?: return false
        charges.remove(charge)
        return true
    }

    /** Subscriptions that name their account claim a charge first. */
    private fun SubscriptionEntity.knownAccountParts() = listOf(bankName, accountLast4).count { !it.isNullOrBlank() }

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
