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
     *   subscription's occurrence settles it, each at most one, as Insights settles
     *   it ([subscriptions]). Only a charge in scope can.
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
        fun subCounts(s: SubscriptionEntity, on: LocalDate) = counts(s.bankName, s.accountLast4, on)
        // Matched against every subscription occurrence, settled or not: a template for a
        // payment that's already posted is paid too.
        val subs = occurrences(subscriptions, today, end, ::subCounts)
        val templates = recurring(recurring, today, end) { t, on -> counts(t.bankName, t.accountLast4, on) }
            .filter { t -> subs.none { (_, bill) -> bill.sameOccurrence(t) } }
        // A charge on an ignored account, or another profile's, pays nothing in this scope.
        val unpaid = subscriptions(subscriptions, posted.filter { !isExcluded(it.bankName, it.accountNumber) }, today, end, ::subCounts)
        val bills = cardBills.mapNotNull { it.bill }.filter { !it.isRetired && !it.dueDate.isAfter(end) }
            .map {
                Bill(it.label, Money(it.remaining, it.currency), it.dueDate, source = BillSource.Card(it.bankName, it.accountLast4))
            }
        return (unpaid + templates + bills).sortedBy { it.dueDate }
    }

    /**
     * Each occurrence of the active expense [subscriptions] due today..[end] that
     * [counts] and that none of the [posted] transactions has paid yet. Insights' bills
     * due and Safe to spend both read this, so the two always agree.
     *
     * A charge pays one occurrence at most: one of its merchant and currency, from an
     * account the subscription [RecurringCharge.couldBePaidFrom], within
     * [RecurringCharge.settles] of the due date and after the last payment the
     * subscription recorded. One already recorded against a subscription stays that
     * one's ([withoutRecorded]). Charges at a bill's exact amount are placed across every
     * bill first, and only then new prices ([RecurringCharge.isPriceChange]), each
     * occurrence taking the charge nearest its due date ([claim]).
     *
     * Charges are placed over every occurrence one could pay, before the window and
     * [counts] narrow what's returned: which bill a charge paid mustn't depend on how
     * far ahead the caller looks, or Safe to spend and Insights would disagree.
     */
    fun subscriptions(
        subscriptions: List<SubscriptionEntity>,
        posted: List<TransactionEntity>,
        today: LocalDate,
        end: LocalDate,
        counts: (SubscriptionEntity, LocalDate) -> Boolean = { _, _ -> true },
    ): List<Bill> {
        val active = subscriptions.filter(::isActiveExpense)
        // A charge up to today pays nothing due more than DUE_WINDOW_DAYS after it.
        val due = occurrences(active, today, maxOf(end, today.plusDays(RecurringCharge.DUE_WINDOW_DAYS))) { _, _ -> true }
        val charges = posted.filter {
            !it.isDeleted && it.transactionType in RecurringCharge.SPEND_TYPES && !it.dateTime.toLocalDate().isAfter(today)
        }.sortedBy { it.dateTime }
        val unclaimed = withoutRecorded(active, charges)
        fun payable(o: Pair<SubscriptionEntity, Bill>, t: TransactionEntity): Boolean {
            val (s, bill) = o
            val on = t.dateTime.toLocalDate()
            return canPay(s, t) && RecurringCharge.settles(bill.dueDate, on) && (s.lastPaidAt == null || on.isAfter(s.lastPaidAt))
        }
        val nearestDue = { o: Pair<SubscriptionEntity, Bill> ->
            compareBy<TransactionEntity> { Math.abs(ChronoUnit.DAYS.between(o.second.dueDate, it.dateTime.toLocalDate())) }
        }
        // Exact amounts first, across every bill, and only then new prices.
        val paid = claim(due, unclaimed, { it.first }, nearestDue) { o, t ->
            payable(o, t) && RecurringCharge.sameAmount(o.first.amount, t.amount)
        }.keys
        val repriced = claim(due.filter { it !in paid }, unclaimed, { it.first }, nearestDue) { o, t ->
            payable(o, t) && RecurringCharge.isPriceChange(o.first.amount, o.second.dueDate, t.amount, t.dateTime.toLocalDate())
        }.keys
        return due.filter { (s, bill) -> !bill.dueDate.isAfter(end) && counts(s, bill.dueDate) }
            .filter { it !in paid && it !in repriced }.map { it.second }
    }

    /** Same merchant and currency, from an account [sub] can be paid from. */
    fun canPay(sub: SubscriptionEntity, charge: TransactionEntity): Boolean =
        charge.currency == sub.currency &&
            RecurringCharge.sameMerchant(charge.merchantName, sub.merchantName) &&
            RecurringCharge.couldBePaidFrom(sub, charge)

    /**
     * [charges] less those already recorded against one of [subscriptions]. Ingestion
     * (and marking a bill paid) keeps no link to the row: it stamps the charge's date as
     * the bill's lastPaidAt and moves the bill on a cycle, so that's the trace matched
     * here. Such a charge is that bill's, so it can't settle or reprice a second one
     * after the first has moved on. One per subscription, a row marked recurring first.
     */
    fun withoutRecorded(
        subscriptions: Collection<SubscriptionEntity>,
        charges: List<TransactionEntity>,
    ): MutableList<TransactionEntity> {
        val unclaimed = charges.toMutableList()
        val recurringFirst = { _: SubscriptionEntity -> compareByDescending<TransactionEntity> { it.isRecurring } }
        // By the matcher's own test, which doesn't look at the account.
        claim(subscriptions.filter { it.lastPaidAt != null }, unclaimed, { it }, recurringFirst) { sub, charge ->
            charge.currency == sub.currency &&
                RecurringCharge.sameMerchant(charge.merchantName, sub.merchantName) &&
                charge.dateTime.toLocalDate() == sub.lastPaidAt &&
                RecurringCharge.sameAmount(sub.amount, charge.amount)
        }
        return unclaimed
    }

    /**
     * Gives each of [items] at most one of [unclaimed] that [matches] it, removing it so
     * no other item gets it. Items whose subscription names its account choose first, so
     * a charge goes to the bill on its own account before one that could be paid from
     * any; each takes the charge [preferred] puts first.
     */
    fun <T> claim(
        items: Collection<T>,
        unclaimed: MutableList<TransactionEntity>,
        subscriptionOf: (T) -> SubscriptionEntity,
        preferred: (T) -> Comparator<TransactionEntity>,
        matches: (T, TransactionEntity) -> Boolean,
    ): Map<T, TransactionEntity> {
        val claimed = linkedMapOf<T, TransactionEntity>()
        items.sortedByDescending { subscriptionOf(it).knownAccountParts() }
            .forEach { item ->
                val charge = unclaimed.filter { matches(item, it) }.minWithOrNull(preferred(item)) ?: return@forEach
                unclaimed.remove(charge)
                claimed[item] = charge
            }
        return claimed
    }

    private fun isActiveExpense(s: SubscriptionEntity) =
        s.state == SubscriptionState.ACTIVE && s.direction == SubscriptionDirection.EXPENSE

    /** Each occurrence of the active expense [subscriptions] due today..[end] that [counts]. */
    private fun occurrences(
        subscriptions: List<SubscriptionEntity>,
        today: LocalDate,
        end: LocalDate,
        counts: (SubscriptionEntity, LocalDate) -> Boolean,
    ): List<Pair<SubscriptionEntity, Bill>> =
        subscriptions
            .filter(::isActiveExpense)
            .flatMap { s ->
                val scheduled = s.nextPaymentDate ?: return@flatMap emptyList()
                // Each counted from the scheduled date, as RecurringCharge.nextPaymentShown
                // counts, so a bill due on the 31st comes back to the 31st after February.
                generateSequence(0L) { it + 1 }.map { RecurringCharge.advance(scheduled, s.billingCycle, it) }
                    .take(MAX_OCCURRENCES)
                    .takeWhile { !it.isAfter(end) }
                    .filter { !it.isBefore(today) && counts(s, it) }
                    .map { s to Bill(s.merchantName, Money(s.amount, s.currency), it, source = BillSource.Subscription) }
                    .toList()
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
                    .map { Bill(t.merchantName, Money(t.amount, t.currency), it, source = BillSource.Recurring) }
                    .toList()
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
