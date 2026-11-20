package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.utils.Money
import java.math.BigDecimal
import java.time.LocalDate

/** Declared in display order: what needs attention first. */
enum class InsightSeverity { ALERT, WARNING, GOOD }

/**
 * Where an insight's one button leads. Domain code names the target; the screen
 * decides which destination shows it (domain never imports navigation).
 */
sealed interface InsightAction {
    data object OpenBudgets : InsightAction
    data class OpenCategory(val category: String, val currency: String, val start: LocalDate, val end: LocalDate) :
        InsightAction
    data class OpenTransaction(val transactionId: Long) : InsightAction
    data class OpenAccount(val bankName: String, val accountLast4: String) : InsightAction
    data object OpenSubscriptions : InsightAction
    data object OpenAccounts : InsightAction
}

/**
 * What a rule found, with its evidence as values, never as text: the screen words
 * and formats it, so amounts follow the privacy toggle and the user's locale.
 */
sealed interface InsightFact {
    data class BudgetOver(val name: String, val spent: Money, val limit: Money, val incomplete: Boolean) : InsightFact

    /** On pace to pass [limit] by the window's end: [perDayLeft] a day keeps it inside. */
    data class BudgetPace(
        val name: String,
        val spent: Money,
        val limit: Money,
        val projected: Money,
        val perDayLeft: Money,
        val windowEnd: LocalDate,
        val incomplete: Boolean,
    ) : InsightFact

    data class BudgetsOnTrack(val count: Int) : InsightFact

    /** [spent] so far this cycle against the [usual] of the cycles before it. */
    data class CategorySpike(val category: String, val spent: Money, val usual: Money, val percentAbove: Int) :
        InsightFact

    /** [percent] of [income] kept this cycle; negative when more went out than came in. */
    data class SavingsRate(val income: Money, val saved: Money, val percent: Int) : InsightFact

    /** [amount] is [times] the [usual] (median) spend in [category]. */
    data class LargeTransaction(
        val merchant: String,
        val category: String,
        val amount: Money,
        val usual: Money,
        val times: Int,
        val date: LocalDate,
    ) : InsightFact

    /** A subscription tracked at [tracked] was charged [charged]. */
    data class PriceChange(val name: String, val tracked: Money, val charged: Money, val date: LocalDate) : InsightFact

    data class CardUtilisation(val label: String, val used: Money, val limit: Money, val percent: Int) : InsightFact

    /**
     * What's held ([balance]) would last [months] at [monthlySpend] a usual month.
     * [lowConfidence]: accounts whose balance isn't known yet are left out of it.
     */
    data class MoneyLasts(
        val months: BigDecimal,
        val balance: Money,
        val monthlySpend: Money,
        val lowConfidence: Boolean,
    ) : InsightFact

    /** [names] are the subscriptions due, soonest first. */
    data class BillsDue(
        val count: Int,
        val total: Money,
        val firstDue: LocalDate,
        val withinDays: Int,
        val names: List<String>,
    ) : InsightFact
}

/**
 * One finding in one currency. [id] is stable for the period
 * and profile (`rule:v2:period:profile:currency:subject`, profile `p<id>` or `all`),
 * which is what a dismissal remembers.
 */
data class Insight(
    val id: String,
    val severity: InsightSeverity,
    val currency: String,
    val fact: InsightFact,
    val action: InsightAction?,
)

/** The cycle an [Insight.id] belongs to (its third part), or null for an id in another form. */
fun insightCycleOf(id: String): String? = id.split(':').getOrNull(2)
