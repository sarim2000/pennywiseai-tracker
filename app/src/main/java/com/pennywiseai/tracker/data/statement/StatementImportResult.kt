package com.pennywiseai.tracker.data.statement

sealed class StatementImportResult {
    data class Success(
        val imported: Int,
        val skippedDuplicates: Int,
        val skippedByReference: Int,
        val skippedByAmountDate: Int,
        val skippedByHash: Int,
        val totalParsed: Int,
        val enriched: Int = 0,
        /** New rows a block rule kept out. */
        val blockedByRules: Int = 0
    ) : StatementImportResult()
}
