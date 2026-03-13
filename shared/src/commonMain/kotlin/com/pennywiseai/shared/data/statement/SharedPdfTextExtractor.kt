package com.pennywiseai.shared.data.statement

expect object SharedPdfTextExtractor {
    fun extractText(filePath: String): String
}
