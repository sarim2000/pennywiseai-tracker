package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.domain.goals.GoalAffordability
import com.pennywiseai.tracker.domain.insights.InsightFact.BillsDue
import com.pennywiseai.tracker.domain.insights.InsightFact.BudgetOver
import com.pennywiseai.tracker.domain.insights.InsightFact.BudgetPace
import com.pennywiseai.tracker.domain.insights.InsightFact.BudgetsOnTrack
import com.pennywiseai.tracker.domain.insights.InsightFact.CardUtilisation
import com.pennywiseai.tracker.domain.insights.InsightFact.CategorySpike
import com.pennywiseai.tracker.domain.insights.InsightFact.GoalsFit
import com.pennywiseai.tracker.domain.insights.InsightFact.LargeTransaction
import com.pennywiseai.tracker.domain.insights.InsightFact.MoneyLasts
import com.pennywiseai.tracker.domain.insights.InsightFact.PriceChange
import com.pennywiseai.tracker.domain.insights.InsightFact.SavingsRate
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.spending.Bill
import com.pennywiseai.tracker.domain.spending.RecurringCharge
import com.pennywiseai.tracker.domain.spending.Runway
import com.pennywiseai.tracker.domain.spending.SafeToSpend
import com.pennywiseai.tracker.domain.spending.SpendForecast
import com.pennywiseai.tracker.domain.spending.SubscriptionCost
import com.pennywiseai.tracker.domain.spending.budgetPace
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One LIMIT budget's running window, in the currency the Budgets screen shows it in. */
data class BudgetStatus(
    val id: Long,
    val name: String,
    val currency: String,
    val spent: BigDecimal,
    val limit: BigDecimal,
    /** Today included, as the Budgets screen counts it. */
    val daysElapsed: Int,
    val windowDays: Int,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    /** Some spending had no exchange rate, so [spent] reads low. */
    val incomplete: Boolean = false,
) {
    val isRunning: Boolean get() = limit.signum() > 0 && daysElapsed in 1..windowDays

    /** A weekly budget's week 2 is news again after week 1 was dismissed. */
    internal val windowKey: String get() = "$id@$windowStart"

    /** At or under an even pace, as the Budgets meter reads it. */
    val isOnTrack: Boolean
        get() = budgetPace(spent, limit, daysElapsed, windowDays)?.let { !it.isOverBudget && it.daysOver < 1 } == true
}

/**
 * Everything the rules read. [review] is the current cycle with its detail, already
 * scoped to the profile and without ignored accounts; so are [subscriptions] and [creditCards].
 */
data class InsightInputs(
    val today: LocalDate,
    val review: PeriodReview,
    val budgets: List<BudgetStatus> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val creditCards: List<AccountBalanceEntity> = emptyList(),
    /** The profile all of the above is scoped to; null for all profiles. */
    val profileId: Long? = null,
    /** Per currency, from the same scope as [review]. */
    val runways: List<Runway> = emptyList(),
    /** Per goal currency; empty while one profile is selected, as goals span them all. */
    val goals: List<GoalAffordability> = emptyList(),
)

/**
 * Pure rules from [InsightInputs] to what needs attention this cycle. Each rule works
 * within one currency and hides itself when the data is too thin to say anything.
 */
object InsightRules {

    /** Most severe first; within a severity, in the order of the rules below. */
    fun evaluate(inputs: InsightInputs): List<Insight> {
        // The cycle, then the profile it's seen in: dismissing Personal's leaves Business's.
        // insightCycleOf still reads the cycle first.
        val period = "${inputs.review.range.start}:${inputs.profileId?.let { "p$it" } ?: "all"}"
        val subscriptions = inputs.subscriptions.filter(::isActiveExpense)
        return (
            budgets(inputs.budgets, period) +
                cardUtilisation(inputs.creditCards, period) +
                billsDue(subscriptions, inputs.review, inputs.today, period) +
                priceChanges(inputs.review, subscriptions, period) +
                categorySpikes(inputs.review, period) +
                largeTransactions(inputs.review, subscriptions, period) +
                savingsRate(inputs.review, inputs.today, period) +
                runway(inputs.runways, period) +
                goalsFit(inputs.goals, period)
            ).sortedBy { it.severity }
    }

    /**
     * Over the limit (or at it with days to go), or on pace to pass it once a quarter
     * of the window has gone (day-one rent isn't a trend). A currency whose budgets are
     * all on track gets one "on track" line, unless a figure is incomplete.
     */
    internal fun budgets(budgets: List<BudgetStatus>, period: String): List<Insight> {
        val running = budgets.filter { it.isRunning }
        val flagged = running.mapNotNull { b ->
            val pace = budgetPace(b.spent, b.limit, b.daysElapsed, b.windowDays) ?: return@mapNotNull null
            if (pace.daysOver < 1 && !pace.isOverBudget) return@mapNotNull null
            val spent = Money(b.spent, b.currency)
            val limit = Money(b.limit, b.currency)
            val daysLeft = b.windowDays - b.daysElapsed + 1
            // Whole units, rounded as Home's budget card and forecast round them, so the two agree.
            val perDayLeft = (b.limit - b.spent).divide(BigDecimal(daysLeft), 0, RoundingMode.HALF_UP)
            when {
                // Nothing left a day with days to go reads as used up, not as "0 a day".
                pace.isOverBudget || perDayLeft.signum() <= 0 -> Insight(
                    id("budget_over", period, b.currency, b.windowKey), InsightSeverity.ALERT, b.currency,
                    BudgetOver(b.name, spent, limit, b.incomplete), InsightAction.OpenBudgets,
                )
                b.daysElapsed * 4 >= b.windowDays -> Insight(
                    id("budget_pace", period, b.currency, b.windowKey), InsightSeverity.WARNING, b.currency,
                    BudgetPace(
                        name = b.name,
                        spent = spent,
                        limit = limit,
                        projected = Money(
                            b.spent.multiply(BigDecimal(b.windowDays))
                                .divide(BigDecimal(b.daysElapsed), 0, RoundingMode.HALF_UP),
                            b.currency,
                        ),
                        perDayLeft = Money(perDayLeft, b.currency),
                        windowEnd = b.windowEnd,
                        incomplete = b.incomplete,
                    ),
                    InsightAction.OpenBudgets,
                )
                else -> null
            }
        }.sortedByDescending { it.usedShare() }

        // Only when every budget is on track, early fast starts included.
        val onTrack = running.groupBy { it.currency }
            .filter { (_, list) -> list.all { it.isOnTrack && !it.incomplete } }
            .map { (currency, list) ->
                Insight(
                    id("budgets_on_track", period, currency, "all"), InsightSeverity.GOOD, currency,
                    BudgetsOnTrack(list.size), null,
                )
            }
        return flagged + onTrack
    }

    /** How much of a card's limit is used: from half, and close to all of it. */
    internal fun cardUtilisation(cards: List<AccountBalanceEntity>, period: String): List<Insight> =
        cards.mapNotNull { card ->
            val limit = card.creditLimit?.takeIf { it.signum() > 0 } ?: return@mapNotNull null
            if (!card.isCreditCard || card.balance.signum() <= 0) return@mapNotNull null
            val percent = card.balance.multiply(HUNDRED).divide(limit, 0, RoundingMode.HALF_UP).toInt()
            val severity = when {
                percent >= CARD_ALERT_PERCENT -> InsightSeverity.ALERT
                percent >= CARD_WARNING_PERCENT -> InsightSeverity.WARNING
                else -> return@mapNotNull null
            }
            // The limit is the card's own, in the card's currency.
            Insight(
                id("card_use", period, card.currency, "${card.bankName}_${card.accountLast4}"), severity, card.currency,
                CardUtilisation(card.displayLabel, Money(card.balance, card.currency), Money(limit, card.currency), percent),
                InsightAction.OpenAccount(card.bankName, card.accountLast4),
            )
        }.sortedByDescending { (it.fact as CardUtilisation).percent }

    /**
     * Subscription payments due in the next [BILLS_DAYS] days, one line per currency,
     * on the dates the Subscriptions screen shows. One already charged is paid, even when
     * the charge wasn't linked to it (a new price, or the subscription added after it),
     * and whether or not card spending counts as expense: settled from the cycle's
     * payment rows, not its spending rows.
     * A charge pays at most one bill, and only one it [RecurringCharge.couldBePaidFrom];
     * charges at a bill's exact amount are placed before new prices, and one already
     * recorded against a subscription stays that one's ([withoutRecorded]).
     * Keyed on the first due date, so a dismissal lasts until that payment has gone.
     */
    internal fun billsDue(
        subscriptions: List<SubscriptionEntity>,
        review: PeriodReview,
        today: LocalDate,
        period: String,
    ): List<Insight> {
        val dues = subscriptions.mapNotNull { sub ->
            sub.nextPaymentDate?.let { sub to RecurringCharge.nextPaymentShown(it, sub.billingCycle, today) }
        }.toMap()
        fun inWindow(sub: SubscriptionEntity, charge: TransactionEntity): Boolean {
            val date = charge.dateTime.toLocalDate()
            return !date.isBefore(dues.getValue(sub).minusDays(RecurringCharge.DUE_WINDOW_DAYS)) && !date.isAfter(today)
        }
        val closestToDue = { sub: SubscriptionEntity ->
            compareBy<TransactionEntity> { Math.abs(ChronoUnit.DAYS.between(dues.getValue(sub), it.dateTime.toLocalDate())) }
        }
        // Exact amounts first, across every bill, and only then new prices.
        val unclaimed = withoutRecorded(subscriptions, review.detail?.paymentRows.orEmpty())
        val paid = claim(dues.keys, unclaimed, closestToDue) { sub, charge ->
            canPay(sub, charge) && inWindow(sub, charge) && RecurringCharge.sameAmount(sub.amount, charge.amount)
        }.keys
        val repriced = claim(dues.keys.filter { it.id !in paid }, unclaimed, closestToDue) { sub, charge ->
            canPay(sub, charge) && inWindow(sub, charge) &&
                RecurringCharge.isPriceChange(sub.amount, dues.getValue(sub), charge.amount, charge.dateTime.toLocalDate())
        }.keys
        val bills = dues.filterKeys { it.id !in paid && it.id !in repriced }
            .map { (sub, due) -> Bill(sub.merchantName, Money(sub.amount, sub.currency), due) }
        return SafeToSpend.billsInWindow(bills, today, today.plusDays(BILLS_DAYS))
            .groupBy { it.amount.currency }
            .map { (currency, due) ->
                val firstDue = due.minOf { it.dueDate }
                Insight(
                    id("bills_due", period, currency, "next$BILLS_DAYS@$firstDue"), InsightSeverity.WARNING, currency,
                    BillsDue(
                        count = due.size,
                        total = due.sumByCurrency({ it.amount.currency }, { it.amount.amount }).getValue(currency),
                        firstDue = firstDue,
                        withinDays = BILLS_DAYS.toInt(),
                        names = due.sortedBy { it.dueDate }.map { it.name },
                    ),
                    InsightAction.OpenSubscriptions,
                )
            }
    }

    /**
     * A charge this cycle that looks like a subscription at a new price, by the same
     * test the SMS matcher uses: until the user updates the subscription, it stays news.
     * As with bills due, a charge counts for one subscription that it [RecurringCharge.couldBePaidFrom],
     * and one at another subscription's exact amount is that one's, not a new price.
     */
    internal fun priceChanges(
        review: PeriodReview,
        subscriptions: List<SubscriptionEntity>,
        period: String,
    ): List<Insight> {
        val charges = review.detail?.paymentRows.orEmpty()
            .filter { it.dateTime.toLocalDate() in review.range.start..review.range.end }
        // A charge at the exact amount of a subscription due around then is that one's
        // payment, not another's new price. One due months away (an annual plan) can't claim it.
        val unclaimed = withoutRecorded(subscriptions, charges).filterNot { charge ->
            subscriptions.any {
                canPay(it, charge) && RecurringCharge.sameAmount(it.amount, charge.amount) &&
                    dueNear(it, charge.dateTime.toLocalDate())
            }
        }.toMutableList()
        val latestFirst = { _: SubscriptionEntity -> compareByDescending<TransactionEntity> { it.dateTime } }
        val repriced = claim(subscriptions, unclaimed, latestFirst) { sub, charge ->
            canPay(sub, charge) &&
                RecurringCharge.isPriceChange(sub.amount, sub.nextPaymentDate, charge.amount, charge.dateTime.toLocalDate())
        }
        return subscriptions.mapNotNull { sub ->
            val charge = repriced[sub.id] ?: return@mapNotNull null
            Insight(
                id("price_change", period, sub.currency, sub.id), InsightSeverity.WARNING, sub.currency,
                PriceChange(
                    sub.merchantName, Money(sub.amount, sub.currency), Money(charge.amount, sub.currency),
                    charge.dateTime.toLocalDate(),
                ),
                InsightAction.OpenSubscriptions,
            )
        }
    }

    /**
     * A category already [SPIKE_RATIO] times its usual this cycle, against the average of
     * up to [BASELINE_CYCLES] earlier cycles that had any activity. Needs [MIN_BASELINE]
     * of them, and a rise worth at least [SPIKE_FLOOR] of a usual cycle's spending, so a
     * coffee going from 3 to 9 isn't news. A category new this cycle has no usual, and
     * uncategorised spending isn't one place to spend less, so it's never flagged.
     */
    internal fun categorySpikes(review: PeriodReview, period: String): List<Insight> {
        val uncategorised = setOf(UNCATEGORISED, BuiltinCategoryNames.current(UNCATEGORISED))
        return review.detail?.cycles.orEmpty().flatMap { (currency, cycles) ->
            val now = cycles.lastOrNull() ?: return@flatMap emptyList()
            val before = cycles.dropLast(1).takeLast(BASELINE_CYCLES).filter { it.hasActivity }
            if (before.size < MIN_BASELINE) return@flatMap emptyList()
            val count = BigDecimal(before.size)
            val floor = before.fold(BigDecimal.ZERO) { sum, c -> sum + c.spent }
                .divide(count, 2, RoundingMode.HALF_UP).multiply(SPIKE_FLOOR)
            now.byCategory.filterKeys { it !in uncategorised }.mapNotNull { (category, spent) ->
                val usual = before.fold(BigDecimal.ZERO) { sum, c -> sum + (c.byCategory[category] ?: BigDecimal.ZERO) }
                    .divide(count, 2, RoundingMode.HALF_UP)
                if (usual.signum() <= 0 || spent < usual.multiply(SPIKE_RATIO) || spent - usual < floor) {
                    return@mapNotNull null
                }
                Insight(
                    id("category_spike", period, currency, category), InsightSeverity.WARNING, currency,
                    CategorySpike(
                        category, Money(spent, currency), Money(usual, currency),
                        (spent - usual).multiply(HUNDRED).divide(usual, 0, RoundingMode.HALF_UP).toInt(),
                    ),
                    InsightAction.OpenCategory(category, currency, review.range.start, review.range.end),
                )
            }.sortedByDescending { (it.fact as CategorySpike).let { f -> f.spent.amount - f.usual.amount } }
                .take(MAX_PER_RULE)
        }
    }

    /**
     * A spend this cycle over [LARGE_TIMES] times its category's median across the loaded
     * cycles, once the category has [MIN_SAMPLES] rows. Known subscriptions and rows
     * marked recurring are expected, and split rows span categories, so none counts.
     */
    internal fun largeTransactions(
        review: PeriodReview,
        subscriptions: List<SubscriptionEntity>,
        period: String,
    ): List<Insight> {
        val rows = review.detail?.spendRows.orEmpty().filter { !it.hasSplits }.map { it.transaction }
        return rows.groupBy { it.currency to it.category.ifEmpty { UNCATEGORISED } }
            .flatMap { (key, group) ->
                val (currency, category) = key
                if (group.size < MIN_SAMPLES) return@flatMap emptyList()
                val median = median(group.map { it.amount })
                if (median.signum() <= 0) return@flatMap emptyList()
                group.filter { tx ->
                    tx.dateTime.toLocalDate() in review.range.start..review.range.end &&
                        tx.amount > median.multiply(BigDecimal(LARGE_TIMES)) &&
                        !tx.isRecurring &&
                        subscriptions.none { it.currency == currency && RecurringCharge.sameMerchant(it.merchantName, tx.merchantName) }
                }.map { tx ->
                    Insight(
                        id("large_spend", period, currency, tx.id), InsightSeverity.WARNING, currency,
                        LargeTransaction(
                            tx.merchantName, category, Money(tx.amount, currency), Money(median, currency),
                            tx.amount.divide(median, 0, RoundingMode.DOWN).toInt(), tx.dateTime.toLocalDate(),
                        ),
                        InsightAction.OpenTransaction(tx.id),
                    )
                }
            }
            .groupBy { it.currency }
            .flatMap { (_, list) ->
                list.sortedByDescending { (it.fact as LargeTransaction).amount.amount }.take(MAX_PER_RULE)
            }
    }

    /**
     * The share of income kept, once a week of the cycle has gone and only with income
     * recorded. Worth saying when it's [GOOD_SAVINGS_PERCENT] or more, or below zero.
     */
    internal fun savingsRate(review: PeriodReview, today: LocalDate, period: String): List<Insight> {
        if (ChronoUnit.DAYS.between(review.range.start, today) + 1 < SpendForecast.MIN_DAYS) return emptyList()
        return review.currencies.mapNotNull { c ->
            if (c.income.signum() <= 0) return@mapNotNull null
            val saved = c.income - c.spent
            val percent = saved.multiply(HUNDRED).divide(c.income, 0, RoundingMode.HALF_UP).toInt()
            val severity = when {
                percent >= GOOD_SAVINGS_PERCENT -> InsightSeverity.GOOD
                percent < 0 -> InsightSeverity.WARNING
                else -> return@mapNotNull null
            }
            Insight(
                id("savings_rate", period, c.currency, "cycle"), severity, c.currency,
                SavingsRate(Money(c.income, c.currency), Money(saved, c.currency), percent), null,
            )
        }
    }

    /**
     * How long the money held would last at a usual month's spending: worth a word
     * below [LOW_RUNWAY_MONTHS], and as good news from [Runway.TARGET_MONTHS]. In
     * between, the Insights summary still shows it. A low figure that leaves some
     * accounts out may only be low because of them, so it isn't a warning; it opens
     * Accounts, where their balances can be set.
     */
    internal fun runway(runways: List<Runway>, period: String): List<Insight> = runways.mapNotNull { r ->
        val severity = when {
            r.months < BigDecimal(LOW_RUNWAY_MONTHS) && !r.lowConfidence -> InsightSeverity.WARNING
            r.months >= BigDecimal(Runway.TARGET_MONTHS) -> InsightSeverity.GOOD
            else -> return@mapNotNull null
        }
        Insight(
            id("runway", period, r.currency, "held"), severity, r.currency,
            MoneyLasts(r.months, Money(r.balance, r.currency), Money(r.monthlySpend, r.currency), r.lowConfidence),
            InsightAction.OpenAccounts.takeIf { r.lowConfidence },
        )
    }

    /**
     * Whether the goals in progress fit what a usual month leaves over. A currency with
     * no income history says nothing: no data isn't the same as can't afford.
     */
    internal fun goalsFit(goals: List<GoalAffordability>, period: String): List<Insight> = goals.mapNotNull { g ->
        val surplus = g.surplus ?: return@mapNotNull null
        Insight(
            id("goals_fit", period, g.currency, "all"),
            if (g.fits == true) InsightSeverity.GOOD else InsightSeverity.WARNING,
            g.currency,
            GoalsFit(Money(g.needed, g.currency), Money(surplus, g.currency), g.shortBy?.let { Money(it, g.currency) }),
            InsightAction.OpenGoals,
        )
    }

    /**
     * What active subscriptions add up to a month, per currency: a running figure for
     * the summary card, not something that needs attention every cycle.
     */
    fun subscriptionsMonthly(subscriptions: List<SubscriptionEntity>): Map<String, Money> =
        subscriptions.filter(::isActiveExpense)
            .sumByCurrency({ it.currency }) { SubscriptionCost.monthlyEquivalent(it.amount, it.billingCycle) }

    /**
     * Whether a payment of [sub] falls within [RecurringCharge.DUE_WINDOW_DAYS] of [date]:
     * the scheduled one around it, or the one before. One with no schedule might.
     */
    private fun dueNear(sub: SubscriptionEntity, date: LocalDate): Boolean {
        val scheduled = sub.nextPaymentDate ?: return true
        val next = RecurringCharge.nextPaymentShown(scheduled, sub.billingCycle, date)
        return listOf(next, RecurringCharge.advance(next, sub.billingCycle, -1)).any {
            Math.abs(ChronoUnit.DAYS.between(it, date)) <= RecurringCharge.DUE_WINDOW_DAYS
        }
    }

    /** Same merchant and currency, from an account [sub] can be paid from. */
    private fun canPay(sub: SubscriptionEntity, charge: TransactionEntity) =
        charge.currency == sub.currency &&
            RecurringCharge.sameMerchant(charge.merchantName, sub.merchantName) &&
            RecurringCharge.couldBePaidFrom(sub, charge)

    /**
     * [charges] less those already recorded against one of [subscriptions]. Ingestion
     * (and marking a bill paid) keeps no link to the row: it stamps the charge's date as
     * the bill's lastPaidAt and moves the bill on a cycle, so that's the trace matched
     * here. Such a charge is that bill's, so it can't settle or reprice a second one
     * after the first has moved on. One per subscription, by date rather than amount, so
     * editing the bill's price later doesn't free its old payment for another bill; the
     * closest amount first, then a row marked recurring.
     */
    private fun withoutRecorded(
        subscriptions: Collection<SubscriptionEntity>,
        charges: List<TransactionEntity>,
    ): MutableList<TransactionEntity> {
        val unclaimed = charges.toMutableList()
        val closestAmount = { sub: SubscriptionEntity ->
            compareBy<TransactionEntity> { (it.amount - sub.amount).abs() }.thenByDescending { it.isRecurring }
        }
        // By the matcher's own test, which doesn't look at the account.
        claim(subscriptions.filter { it.lastPaidAt != null }, unclaimed, closestAmount) { sub, charge ->
            charge.currency == sub.currency &&
                RecurringCharge.sameMerchant(charge.merchantName, sub.merchantName) &&
                charge.dateTime.toLocalDate() == sub.lastPaidAt
        }
        return unclaimed
    }

    /**
     * Gives each of [subscriptions] at most one of [unclaimed] that [matches] it,
     * removing it so no other subscription gets it. Subscriptions that name their
     * account choose first, so a charge goes to the bill on its own account before one
     * that could be paid from any; each takes a free charge in [preferred] order. When
     * none is free, it takes one from a subscription that can move to another, so as
     * many bills as possible are settled.
     */
    private fun claim(
        subscriptions: Collection<SubscriptionEntity>,
        unclaimed: MutableList<TransactionEntity>,
        preferred: (SubscriptionEntity) -> Comparator<TransactionEntity>,
        matches: (SubscriptionEntity, TransactionEntity) -> Boolean,
    ): Map<Long, TransactionEntity> {
        val options = subscriptions.associateWith { sub ->
            unclaimed.indices.filter { matches(sub, unclaimed[it]) }.sortedWith(compareBy(preferred(sub)) { unclaimed[it] })
        }
        val holder = mutableMapOf<Int, SubscriptionEntity>()
        fun place(sub: SubscriptionEntity, tried: MutableSet<Int>): Boolean {
            val choices = options.getValue(sub)
            choices.firstOrNull { it !in holder }?.let { holder[it] = sub; return true }
            return choices.any { charge ->
                tried.add(charge) && place(holder.getValue(charge), tried) && run { holder[charge] = sub; true }
            }
        }
        subscriptions.sortedByDescending { listOf(it.bankName, it.accountLast4).count { s -> !s.isNullOrBlank() } }
            .forEach { place(it, mutableSetOf()) }
        val claimed = holder.entries.associate { (charge, sub) -> sub.id to unclaimed[charge] }
        holder.keys.sortedDescending().forEach { unclaimed.removeAt(it) }
        return claimed
    }

    private fun isActiveExpense(sub: SubscriptionEntity) =
        sub.state == SubscriptionState.ACTIVE && sub.direction == SubscriptionDirection.EXPENSE

    private fun Insight.usedShare(): BigDecimal {
        val (spent, limit) = when (val f = fact) {
            is BudgetOver -> f.spent to f.limit
            is BudgetPace -> f.projected to f.limit
            else -> return BigDecimal.ZERO
        }
        return spent.amount.divide(limit.amount, 4, RoundingMode.HALF_UP)
    }

    private fun median(values: List<BigDecimal>): BigDecimal {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid]
        else (sorted[mid - 1] + sorted[mid]).divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
    }

    private fun id(rule: String, period: String, currency: String, subject: Any) = "$rule:v2:$period:$currency:$subject"

    /** The bucket a row without a category is totalled under ([com.pennywiseai.tracker.domain.review.buildPeriodDetail]). */
    private const val UNCATEGORISED = "Others"
    private val HUNDRED = BigDecimal(100)
    private val SPIKE_RATIO = BigDecimal("1.5")
    private val SPIKE_FLOOR = BigDecimal("0.05")
    private const val BASELINE_CYCLES = 3
    private const val MIN_BASELINE = 2
    private const val MIN_SAMPLES = 5
    private const val LARGE_TIMES = 3
    private const val MAX_PER_RULE = 3
    private const val GOOD_SAVINGS_PERCENT = 20
    private const val CARD_WARNING_PERCENT = 50
    private const val CARD_ALERT_PERCENT = 90
    private const val LOW_RUNWAY_MONTHS = 3
    internal const val BILLS_DAYS = 7L
}
