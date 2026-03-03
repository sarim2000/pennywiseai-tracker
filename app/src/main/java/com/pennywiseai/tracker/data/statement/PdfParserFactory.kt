package com.pennywiseai.tracker.data.statement

object PdfParserFactory {

    private val parsers = listOf(
        GPayPdfParser(),
        PhonePePdfParser()
    )

    fun getParser(extractedText: String): PdfStatementParser? {
        return parsers.firstOrNull { it.canHandle(extractedText) }
    }
}
