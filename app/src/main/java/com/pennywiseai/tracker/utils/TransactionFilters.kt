package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionEntity

/**
 * Single source of truth for "does this transaction count toward a total?"
 * (Home, Analytics, Budgets, widgets, AI summaries). A loan-linked transaction
 * isn't discretionary spend/income, and one flagged `excludedFromAnalytics` is
 * a deliberate opt-out (#451) — both stay out of every aggregate. Screen-specific
 * conditions (expense-only, date range, category, ...) are layered on by the
 * caller on top of this; this only covers the two universal exclusions (#800).
 */
fun TransactionEntity.countsInTotals(): Boolean = loanId == null && !excludedFromAnalytics
