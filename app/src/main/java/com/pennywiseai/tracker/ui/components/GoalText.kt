package com.pennywiseai.tracker.ui.components

import android.content.Context
import android.content.res.Resources
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalStatus
import com.pennywiseai.tracker.domain.goals.GoalPill
import com.pennywiseai.tracker.domain.goals.pill
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.util.Locale

/** How a status line writes money: the app's usual; the widgets pass their own. */
private val appMoney: (BigDecimal, String) -> String = { amount, currency -> CurrencyFormatter.formatCurrency(amount, currency) }

/**
 * A month count. [latinDigits] keeps "12" Latin where Arabic resources would
 * write "١٢": the widgets do, beside their Latin amounts.
 */
private fun Resources.months(id: Int, count: Int, latinDigits: Boolean): String =
    if (latinDigits) String.format(Locale.US, getQuantityString(id, count), count)
    else getQuantityString(id, count, count)

/**
 * One-line status for compact rows (Home card, goals widget):
 * "₹1,920/mo · 5 months left", "Behind ₹2,000", "Reached" or "Missed by ₹X".
 * Takes a [Context] so the widget worker builds the same text the Home card shows.
 */
fun GoalProgress.statusLine(context: Context, money: (BigDecimal, String) -> String = appMoney, latinDigits: Boolean = false): String {
    val res = context.resources
    val currency = goal.currency
    return when (val pill = pill()) {
        GoalPill.Reached -> res.getString(R.string.goals_reached)
        is GoalPill.MissedBy -> res.getString(R.string.goals_missed_by, money(pill.amount, currency))
        is GoalPill.Behind -> res.getString(R.string.goals_behind, money(pill.amount, currency))
        is GoalPill.PerMonth -> res.getString(
            R.string.goals_line_join,
            res.getString(R.string.goals_per_month, money(pill.amount, currency)),
            res.months(R.plurals.goals_months_left, monthsLeft, latinDigits),
        )
        GoalPill.Archived -> res.getString(R.string.goals_archived)
    }
}

/** The 2×2 widget's line: "₹9,600 left · 5 mo" while in progress, else the same as [statusLine]. */
fun GoalProgress.shortLine(context: Context, money: (BigDecimal, String) -> String = appMoney, latinDigits: Boolean = false): String {
    if (status != GoalStatus.IN_PROGRESS) return statusLine(context, money, latinDigits)
    val res = context.resources
    return res.getString(
        R.string.goals_line_join,
        res.getString(R.string.goals_left, money(left, goal.currency)),
        res.months(R.plurals.goals_months_short, monthsLeft, latinDigits),
    )
}
