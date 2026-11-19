package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import java.time.LocalDate

/**
 * The payments the calendar shows as still due, each in its own currency and on its
 * own day: each occurrence of an active expense subscription and recurring template,
 * and the card bills still to pay. Nothing here is added up.
 *
 * A paid subscription has already moved its schedule on (ingestion records the
 * charge, [com.pennywiseai.tracker.data.repository.SubscriptionRepository.recordCharge]),
 * and the daily worker posts a template's due run and moves it on, so each schedule
 * is read from its next date.
 */
object UpcomingPayments {

    /**
     * Payments due [today] (or [from], if later: the calendar's month, which can be far
     * ahead) to [end].
     *
     * @param cardBills the visible cards' bills. One with something left counts even
     *   when overdue, and isn't cut by [from]: it's still owed.
     * @param cards every credit card, hidden ones included: nothing charged to a hidden
     *   card counts, even when the charge names only its last digits.
     * @param isExcluded an account whose payments don't count (ignored, or in another
     *   profile); null accounts never are.
     */
    fun of(
        subscriptions: List<SubscriptionEntity>,
        recurring: List<RecurringTransactionEntity>,
        cardBills: List<CardBillStatus>,
        cards: List<AccountBalanceEntity>,
        today: LocalDate,
        end: LocalDate,
        isExcluded: (bankName: String?, accountLast4: String?) -> Boolean = { _, _ -> false },
        from: LocalDate = today,
    ): List<Bill> {
        val start = maxOf(today, from)
        val hidden = cards.filter { isExcluded(it.bankName, it.accountLast4) }
        fun counts(bank: String?, last4: String?) = !isExcluded(bank, last4) && !onCard(bank, last4, hidden)
        val subs = subscriptions(subscriptions.filter { counts(it.bankName, it.accountLast4) }, start, end)
        // A template for the same payment as a subscription is shown once.
        val templates = recurring(recurring.filter { counts(it.bankName, it.accountLast4) }, today, start, end)
            .filter { t -> subs.none { it.sameOccurrence(t) } }
        val bills = cardBills.mapNotNull { it.bill }.filter { !it.isRetired && !it.dueDate.isAfter(end) }
            .map {
                Bill(it.label, Money(it.remaining, it.currency), it.dueDate, source = BillSource.Card(it.bankName, it.accountLast4))
            }
        return (subs + templates + bills).sortedBy { it.dueDate }
    }

    /**
     * Each occurrence of the active expense [subscriptions] due [start]..[end], counted
     * from the scheduled date as the Subscriptions screen counts it, so a bill due on the
     * 31st comes back to the 31st after February. The guard counts from [start], so a
     * far window isn't cut short.
     */
    private fun subscriptions(subscriptions: List<SubscriptionEntity>, start: LocalDate, end: LocalDate): List<Bill> =
        subscriptions
            .filter { it.state == SubscriptionState.ACTIVE && it.direction == SubscriptionDirection.EXPENSE }
            .flatMap { s ->
                val scheduled = s.nextPaymentDate ?: return@flatMap emptyList()
                generateSequence(0L) { it + 1 }.map { RecurringCharge.advance(scheduled, s.billingCycle, it) }
                    .dropWhile { it.isBefore(start) }
                    .take(MAX_OCCURRENCES)
                    .takeWhile { !it.isAfter(end) }
                    .map { Bill(s.merchantName, Money(s.amount, s.currency), it, source = BillSource.Subscription) }
                    .toList()
            }

    /**
     * Each occurrence of the active expense templates due [start]..[end]. One already
     * due is the daily worker's to post (it then moves the schedule past [today]), so
     * the count starts after today.
     */
    private fun recurring(templates: List<RecurringTransactionEntity>, today: LocalDate, start: LocalDate, end: LocalDate): List<Bill> =
        templates
            .filter { it.isActive && it.transactionType == TransactionType.EXPENSE }
            .flatMap { t ->
                val first = if (t.nextDueDate.isBefore(today)) {
                    generateSequence(t.nextDueAfter(t.nextDueDate)) { t.nextDueAfter(it) }.first { it.isAfter(today) }
                } else {
                    t.nextDueDate
                }
                generateSequence(first) { t.nextDueAfter(it) }
                    .dropWhile { it.isBefore(start) }
                    .take(MAX_OCCURRENCES)
                    .takeWhile { !it.isAfter(end) }
                    .map { Bill(t.merchantName, Money(t.amount, t.currency), it, source = BillSource.Recurring) }
                    .toList()
            }

    /** A subscription and a template for the same payment: merchant, currency, amount and date all agree. */
    private fun Bill.sameOccurrence(other: Bill): Boolean =
        dueDate == other.dueDate &&
            amount.currency == other.amount.currency &&
            RecurringCharge.sameMerchant(name, other.name) &&
            RecurringCharge.sameAmount(amount.amount, other.amount.amount)

    private fun onCard(bank: String?, last4: String?, cards: List<AccountBalanceEntity>): Boolean =
        last4 != null && cards.any { it.isCreditCard && it.accountLast4 == last4 && (bank == null || it.bankName.equals(bank, ignoreCase = true)) }

    /** A guard against a corrupt schedule; a month holds 31 daily bills at most. */
    private const val MAX_OCCURRENCES = 400
}
