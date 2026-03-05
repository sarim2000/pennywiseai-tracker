package com.pennywiseai.shared.data.statement

sealed class SharedStatementImportResult {
    data class Success(
        val imported: Int,
        val skippedDuplicates: Int,
        val totalParsed: Int
    ) : SharedStatementImportResult()

    data class Error(val message: String) : SharedStatementImportResult()
}
