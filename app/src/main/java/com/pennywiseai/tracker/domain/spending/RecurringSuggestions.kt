package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A merchant the user seems to pay on a schedule but doesn't track yet, found
 * in their transaction history. Only a suggestion: nothing counts toward
 * totals or reminders until the user confirms it.
 */
data class RecurringSuggestion(
    /** [RecurringSuggestions.keyFor] — what "Not recurring" remembers. */
    val key: String,
    val merchantName: String,
    val currency: String,
    /** A [com.pennywiseai.tracker.data.database.entity.SubscriptionEntity.billingCycle] value. */
    val billingCycle: String,
    /** The latest payment, oldest-first in [payments]. */
    val amount: BigDecimal,
    val minAmount: BigDecimal,
    val maxAmount: BigDecimal,
    /** A bill whose amount moves from cycle to cycle (telecom, utilities). */
    val isVariable: Boolean,
    val category: String,
    val bankName: String?,
    val accountLast4: String?,
    /** The payments that make up the pattern, oldest first. */
    val payments: List<Payment>,
    val nextDueDate: LocalDate,
) {
    data class Payment(val transactionId: Long, val date: LocalDate, val amount: BigDecimal)

    val lastPaidOn: LocalDate get() = payments.last().date
}

/**
 * Finds repeating payments in history. Pure, so the rules are testable without
 * a database.
 *
 * A pattern needs:
 *  - payments at one merchant in one currency, each a cycle after the last
 *    (same day of the month, give or take a few days), with nothing at that
 *    amount in between: a same-amount buy mid-cycle means a frequent purchase
 *    (a bus card top-up), not a bill;
 *  - three monthly or quarterly payments, four weekly, two for longer cycles;
 *  - amounts within 5% of each other, or within 25% for a bill whose amount
 *    varies ([variableCategories]: phone and utilities only);
 *  - a last payment recent enough that the next one isn't long overdue;
 *  - a category that isn't everyday spending ([excludedCategories]).
 */
object RecurringSuggestions {

    /** Built-in categories whose spending repeats without being a bill. */
    val EXCLUDED_CATEGORIES = setOf(
        "Food & Dining", "Groceries", "Transportation", "Shopping", "Travel",
        "Personal Care", "Bank Charges", "Credit Card Payment", "Tax"
    )

    /** Built-in categories whose bills legitimately change amount every cycle. */
    val VARIABLE_CATEGORIES = setOf("Bills & Utilities", "Mobile")

    /** How far back the history needs to go to see two annual payments. */
    const val LOOKBACK_DAYS = 400L

    fun keyFor(merchantName: String, currency: String): String =
        "${currency.uppercase()}|${RecurringCharge.merchantKey(merchantName)}"

    private val VARIABLE_TOLERANCE = BigDecimal("0.25")

    private data class Cadence(val cycle: String, val minPayments: Int, val toleranceDays: Long, val graceDays: Long)

    // Monthly first: it's by far the most common, and a monthly series can't
    // also pass as weekly (the gaps differ).
    private val CADENCES = listOf(
        Cadence("Monthly", 3, 3, 10),
        Cadence("Weekly", 4, 1, 3),
        Cadence("Quarterly", 3, 5, 15),
        Cadence("Semi-Annual", 2, 7, 20),
        Cadence("Annual", 2, 10, 30),
    )

    /**
     * @param transactions history to search; anything that isn't a live spend
     *   is ignored here.
     * @param builtinKeyOf the built-in category a transaction's category name
     *   stands for (null for a user category), so a renamed or translated
     *   "Groceries" is still everyday spending.
     * @param isCovered whether a subscription (in any state) already covers
     *   the merchant in that currency.
     * @param dismissed keys the user marked "Not recurring".
     */
    fun detect(
        transactions: List<TransactionEntity>,
        today: LocalDate,
        builtinKeyOf: (String) -> String?,
        isCovered: (merchantName: String, currency: String) -> Boolean,
        dismissed: Set<String>,
    ): List<RecurringSuggestion> = transactions
        .asSequence()
        .filter { !it.isDeleted && it.transactionType in RecurringCharge.SPEND_TYPES && it.loanId == null }
        .filter { RecurringCharge.merchantKey(it.merchantName).isNotEmpty() && it.amount.signum() > 0 }
        .groupBy { keyFor(it.merchantName, it.currency) }
        .asSequence()
        .filter { (key, _) -> key !in dismissed }
        .mapNotNull { (key, charges) ->
            val latest = charges.maxBy { it.dateTime }
            val builtin = builtinKeyOf(latest.category)
            if (builtin in EXCLUDED_CATEGORIES) return@mapNotNull null
            if (isCovered(latest.merchantName, latest.currency)) return@mapNotNull null
            detectSeries(key, charges, today, variable = builtin in VARIABLE_CATEGORIES)
        }
        .sortedBy { it.nextDueDate }
        .toList()

    private fun detectSeries(
        key: String,
        charges: List<TransactionEntity>,
        today: LocalDate,
        variable: Boolean,
    ): RecurringSuggestion? {
        // One payment per day: a duplicate SMS or a split payment shouldn't
        // break (or fake) a cadence.
        val newestFirst = charges
            .sortedByDescending { it.dateTime }
            .distinctBy { it.dateTime.toLocalDate() }
        // A fixed subscription is anchored on one of the recent amounts (the
        // newest charge may be a one-off at the same merchant); a variable bill
        // takes every payment.
        val anchors = newestFirst.take(MAX_ANCHORS)
        for (anchor in anchors) {
            val chain = newestFirst
                .dropWhile { it !== anchor }
                .filter { RecurringCharge.sameAmount(it.amount, anchor.amount) }
            series(chain, today)?.let { (cadence, payments) ->
                return suggestion(key, payments, cadence, isVariable = false)
            }
        }
        if (variable) {
            series(newestFirst, today)?.let { (cadence, payments) ->
                if (cadence.cycle != "Monthly") return null
                val median = payments.map { it.amount }.sorted()[payments.size / 2]
                val band = median.multiply(VARIABLE_TOLERANCE)
                if (payments.all { (it.amount - median).abs() <= band }) {
                    return suggestion(key, payments, cadence, isVariable = true)
                }
            }
        }
        return null
    }

    /** The cadence [newestFirst] ends on, with the payments in it, oldest first. */
    private fun series(newestFirst: List<TransactionEntity>, today: LocalDate): Pair<Cadence, List<TransactionEntity>>? {
        val latest = newestFirst.firstOrNull() ?: return null
        val latestDate = latest.dateTime.toLocalDate()
        for (cadence in CADENCES) {
            // Stale: the next payment is long overdue, so it was probably cancelled.
            if (today.isAfter(advance(latestDate, cadence.cycle).plusDays(cadence.graceDays))) continue
            val payments = mutableListOf(latest)
            var frequent = false
            for (charge in newestFirst.drop(1)) {
                val date = charge.dateTime.toLocalDate()
                val expected = advance(payments.last().dateTime.toLocalDate(), cadence.cycle, reverse = true)
                val off = ChronoUnit.DAYS.between(expected, date)
                when {
                    Math.abs(off) <= cadence.toleranceDays -> payments += charge
                    // Lands inside the cycle: paid more often than this cadence.
                    off > cadence.toleranceDays -> { frequent = true; break }
                    // A missed cycle ends the run.
                    else -> break
                }
            }
            if (frequent) continue
            if (payments.size >= cadence.minPayments) return cadence to payments.reversed()
        }
        return null
    }

    private fun suggestion(
        key: String,
        oldestFirst: List<TransactionEntity>,
        cadence: Cadence,
        isVariable: Boolean,
    ): RecurringSuggestion {
        val latest = oldestFirst.last()
        val amounts = oldestFirst.map { it.amount }
        return RecurringSuggestion(
            key = key,
            merchantName = latest.merchantName,
            currency = latest.currency,
            billingCycle = cadence.cycle,
            amount = latest.amount,
            minAmount = amounts.min(),
            maxAmount = amounts.max(),
            isVariable = isVariable,
            category = latest.category,
            bankName = latest.bankName,
            accountLast4 = latest.accountNumber,
            payments = oldestFirst.map {
                RecurringSuggestion.Payment(it.id, it.dateTime.toLocalDate(), it.amount)
            },
            nextDueDate = advance(latest.dateTime.toLocalDate(), cadence.cycle),
        )
    }

    /** Same calendar arithmetic as [com.pennywiseai.tracker.data.repository.SubscriptionRepository.advance]. */
    private fun advance(date: LocalDate, cycle: String, reverse: Boolean = false): LocalDate {
        val sign = if (reverse) -1L else 1L
        return when (cycle) {
            "Weekly" -> date.plusWeeks(sign)
            "Quarterly" -> date.plusMonths(3L * sign)
            "Semi-Annual" -> date.plusMonths(6L * sign)
            "Annual" -> date.plusYears(sign)
            else -> date.plusMonths(sign)
        }
    }

    private const val MAX_ANCHORS = 3
}
