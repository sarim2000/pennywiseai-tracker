package com.pennywiseai.tracker.data.statement

sealed class StatementImportResult {
    data class Success(
        val imported: Int,
        val skippedDuplicates: Int,
        val skippedByReference: Int,
        val skippedByAmountDate: Int,
        val skippedByHash: Int,
        val totalParsed: Int
    ) : StatementImportResult()

    data class Error(val message: String) : StatementImportResult()
}
