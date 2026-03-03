package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction

interface PdfStatementParser {
    fun canHandle(text: String): Boolean
    fun parse(text: String): List<ParsedTransaction>
}
