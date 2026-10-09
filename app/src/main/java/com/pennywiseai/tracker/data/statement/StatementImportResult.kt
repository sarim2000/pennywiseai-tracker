package com.pennywiseai.tracker.data.statement

sealed class StatementImportResult {
    data class Success(
        val imported: Int,
        val skippedDuplicates: Int,
        val skippedByReference: Int,
        val skippedByAmountDate: Int,
        val skippedByHash: Int,
        val totalParsed: Int,
        val enriched: Int = 0
    ) : StatementImportResult()

    data class Error(val message: String) : StatementImportResult()
    /** The statement PDF is locked; ask for its password ([wrongPassword]: the last one failed). */
    data class PasswordRequired(val wrongPassword: Boolean) : StatementImportResult()
}
