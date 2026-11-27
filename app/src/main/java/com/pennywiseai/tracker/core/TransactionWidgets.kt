package com.pennywiseai.tracker.core

/**
 * Redraws every home-screen widget that shows transaction-derived data. Call
 * it after anything that changes what they show: a saved, edited or deleted
 * transaction, the budget cycle, the selected profile, ignored accounts. A
 * change to what they mask only needs [redraw].
 *
 * `widget` implements it (`widget/WorkManagerTransactionWidgets.kt`, bound in
 * `di/TransactionWidgetsModule.kt`), so `data` and `receiver` can refresh the
 * widgets without importing `widget`. It sits in `core` rather than `data`
 * because its callers span data, receivers and screens, and it carries no data
 * types.
 */
fun interface TransactionWidgets {
    fun refresh()

    /**
     * Redraws every widget from the snapshot it already has, right away: for a
     * change to what they mask (the eye, App Lock), not to their data, so the
     * mask applies even if a data rebuild fails or waits to retry.
     */
    fun redraw() = refresh()
}
