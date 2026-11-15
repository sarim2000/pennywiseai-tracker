package com.pennywiseai.tracker.data.model

import java.math.BigDecimal

/** Figures the AI chat reads (AiContextRepository). */
data class MonthSummary(
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
    val transactionCount: Int,
    val daysInMonth: Int,
    val currentDay: Int
)

data class CategorySpending(
    val category: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int
)

/**
 * What a chat reply answered, for the chat screen's mascot: the tool behind it (a
 * PennyWiseTools name; null for the model's own words) and the figure its shape shows.
 * The screen's only: never saved, never told to the model. An amount here is one the
 * reply's text states too.
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
    /** The month's spending, when it is one figure in one currency. */
    val monthTotal: com.pennywiseai.tracker.utils.Money? = null,
    /** How many currencies the month's spending is in, when it is more than one: no total then. */
    val currencies: Int = 0,
)
