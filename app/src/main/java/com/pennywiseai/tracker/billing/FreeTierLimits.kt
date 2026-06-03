package com.pennywiseai.tracker.billing

/**
 * Free-tier quotas for Pro-gated features. Constants only — feature modules
 * read these and combine with [EntitlementGate.isProEntitled] to decide
 * whether to render the upgrade prompt.
 *
 * Keeping these in a single file makes it cheap to audit "what does the
 * free tier actually offer" and easy to tune limits without hunting
 * through feature code.
 */
object FreeTierLimits {

    /** Max rules a free user can create. Pro = unlimited. */
    const val MAX_RULES = 3

    /** Max PDF statement imports per calendar month for free users. Pro = unlimited. */
    const val MAX_STATEMENT_IMPORTS_PER_MONTH = 1

    /** Max rows a free user can export to CSV per calendar month. Pro = unlimited. */
    const val MAX_CSV_EXPORT_ROWS_PER_MONTH = 100
}
