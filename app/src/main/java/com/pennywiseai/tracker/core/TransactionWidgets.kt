package com.pennywiseai.tracker.core

/**
 * Redraws every home-screen widget that shows transaction-derived data. Call
 * it after anything that changes what they show: a saved, edited or deleted
 * transaction, the hide-amounts eye, the budget cycle, the selected profile,
 * ignored accounts.
 *
 * `widget` implements it (`widget/WorkManagerTransactionWidgets.kt`, bound in
 * `di/TransactionWidgetsModule.kt`), so `data` and `receiver` can refresh the
 * widgets without importing `widget`. It sits in `core` rather than `data`
 * because its callers span data, receivers and screens, and it carries no data
 * types.
 */
fun interface TransactionWidgets {
    fun refresh()
}
