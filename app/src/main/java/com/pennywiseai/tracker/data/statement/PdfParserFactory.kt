package com.pennywiseai.tracker.data.statement

object PdfParserFactory {

    private val parsers = listOf(
        GPayPdfParser(),
        PhonePePdfParser(),
        PaytmPdfParser(),
        SlicePdfParser(),
        IciciBankPdfParser()
    )

    fun getParser(extractedText: String): PdfStatementParser? {
        return parsers.firstOrNull { it.canHandle(extractedText) }
    }
}
