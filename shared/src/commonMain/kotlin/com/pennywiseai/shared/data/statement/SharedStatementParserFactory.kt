package com.pennywiseai.shared.data.statement

object SharedStatementParserFactory {
    private val parsers: List<SharedStatementParser> = listOf(
        GPaySharedStatementParser(),
        PhonePeSharedStatementParser()
    )

    fun getParser(text: String): SharedStatementParser? = parsers.firstOrNull { it.canHandle(text) }
}
