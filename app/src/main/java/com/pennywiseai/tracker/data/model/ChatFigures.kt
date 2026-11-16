package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.ledger.ConvertedEstimate
import java.math.BigDecimal

/**
 * What a chat figure covers: [window], in the profile Home shows ([profile], null for all).
 * Every answer prints it, so two answers over different days or profiles don't read as a
 * disagreement.
 */
data class ChatScope(val window: SpendingComparison.Window, val profile: ProfileEntity? = null)

/**
 * Spending in [scope] as Home works it out: per currency, never added across currencies.
 * [spent] is spending net of refunds (card purchases only when the user counts them);
 * [forgiven] is lent balances forgiven in the scope, which Home adds to "Spent" (#938).
 * [estimate] is one converted figure, only when Home shows one (unified currency mode).
 * [transactionCount] is every transaction in the scope that counts toward Home's totals.
 */
data class PeriodSpending(
    val scope: ChatScope,
    val spent: Map<String, BigDecimal>,
    val income: Map<String, BigDecimal>,
    val forgiven: Map<String, BigDecimal> = emptyMap(),
    val estimate: ConvertedEstimate? = null,
    val transactionCount: Int = 0,
) {
    /** Home's "Spent": [spent] plus [forgiven], per currency; currencies at zero left out. */
    val spentWithForgiven: Map<String, BigDecimal>
        get() = with(SpendingComparison) { spent.plusAmounts(forgiven) }.filterValues { it.signum() > 0 }
}

/**
 * One category's spending in [scope], per currency: its split shares, its subcategories,
 * and refunds given back to it. [share] is its part of all spending in the user's own
 * currency (or the first one), for the reply's mascot.
 */
data class CategorySpend(
    val scope: ChatScope,
    val category: String,
    val byCurrency: Map<String, BigDecimal>,
    val transactionCount: Int,
    val share: Float? = null,
)

/** Each currency's biggest categories in [scope]; [forgiven] loans are listed apart, never as a category. */
data class TopCategories(
    val scope: ChatScope,
    val ranking: Map<String, List<Pair<String, BigDecimal>>>,
    val forgiven: Map<String, BigDecimal> = emptyMap(),
    val share: Float? = null,
)

/**
 * What a chat reply answered, for the chat screen's mascot: the tool behind it (a
 * PennyWiseTools name; null for the model's own words) and the figure its shape shows.
 * The screen's only: never saved, never told to the model.
 */
data class ReplySubject(
    /** The saved reply this is about. */
    val messageId: String = "",
    val tool: String? = null,
    /** A confirmed Add card's rows were saved. */
    val saved: Boolean = false,
    /** The category's share of the month's spending, 0..1. */
    val share: Float? = null,
    /** Spending rose (1) or fell (-1) in every currency; 0 when it held, went both ways or can't tell. */
    val trend: Int = 0,
)
