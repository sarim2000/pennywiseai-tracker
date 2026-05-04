package com.pennywiseai.tracker.domain.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates regex patterns from a sample SMS plus token-level field tags
 * produced by the add/edit screen. Each "tag" is the index of a whitespace-
 * delimited token in the sample plus the field it identifies; the builder
 * picks neighboring tokens as anchors and synthesizes a regex that locates
 * the same field in future SMS with the same surrounding shape.
 */
@Singleton
class CustomParserRuleBuilder @Inject constructor() {

    enum class TokenTag {
        AMOUNT,
        MERCHANT,
        ACCOUNT,
        EXPENSE_KEYWORD,
        INCOME_KEYWORD
    }

    data class TaggedToken(val tokenIndex: Int, val tag: TokenTag)

    data class BuiltPatterns(
        val amountRegex: String?,
        val merchantRegex: String?,
        val accountRegex: String?,
        val expenseKeywords: List<String>,
        val incomeKeywords: List<String>
    )

    fun tokenize(sms: String): List<String> =
        sms.split(WHITESPACE).filter { it.isNotEmpty() }

    fun build(sample: String, tags: List<TaggedToken>): BuiltPatterns {
        val tokens = tokenize(sample)
        if (tokens.isEmpty()) {
            return BuiltPatterns(null, null, null, emptyList(), emptyList())
        }

        val amountTag = tags.firstOrNull { it.tag == TokenTag.AMOUNT }
        val merchantTag = tags.firstOrNull { it.tag == TokenTag.MERCHANT }
        val accountTag = tags.firstOrNull { it.tag == TokenTag.ACCOUNT }

        val expenseKeywords = tags
            .filter { it.tag == TokenTag.EXPENSE_KEYWORD }
            .mapNotNull { tokens.getOrNull(it.tokenIndex)?.let(::stripTrailingPunct) }
            .filter { it.isNotEmpty() }
            .distinct()

        val incomeKeywords = tags
            .filter { it.tag == TokenTag.INCOME_KEYWORD }
            .mapNotNull { tokens.getOrNull(it.tokenIndex)?.let(::stripTrailingPunct) }
            .filter { it.isNotEmpty() }
            .distinct()

        return BuiltPatterns(
            amountRegex = amountTag?.let { buildAnchoredPattern(tokens, it.tokenIndex, AMOUNT_VALUE) },
            merchantRegex = merchantTag?.let { buildAnchoredPattern(tokens, it.tokenIndex, MERCHANT_VALUE) },
            accountRegex = accountTag?.let { buildAnchoredPattern(tokens, it.tokenIndex, ACCOUNT_VALUE) },
            expenseKeywords = expenseKeywords,
            incomeKeywords = incomeKeywords
        )
    }

    /**
     * Produces a sender pattern from the SMS sender. We keep alphabetic prefixes
     * verbatim and treat numeric runs (likely DLT codes / phone numbers) as
     * literal — this matches Cashew's behavior of locking sender exactly.
     */
    fun buildSenderPattern(sender: String): String = Regex.escape(sender.trim())

    private fun buildAnchoredPattern(
        tokens: List<String>,
        targetIndex: Int,
        valuePattern: String
    ): String {
        val anchorBefore = tokens.getOrNull(targetIndex - 1)?.let(::escapeAnchor)
        val anchorAfter = tokens.getOrNull(targetIndex + 1)?.let(::escapeAnchor)

        return buildString {
            if (anchorBefore != null) {
                append(anchorBefore)
                append("""\s+""")
            }
            append("(")
            append(valuePattern)
            append(")")
            if (anchorAfter != null) {
                append("""\s+""")
                append(anchorAfter)
            }
        }
    }

    /**
     * Escape a token for use as a regex anchor. We strip surrounding
     * punctuation that's likely incidental (".", ",", ":") so that minor
     * SMS variations don't break the anchor.
     */
    private fun escapeAnchor(token: String): String =
        Regex.escape(stripTrailingPunct(token))

    private fun stripTrailingPunct(token: String): String =
        token.trim().trim { ch -> ch in ",.;:!?" }

    companion object {
        private val WHITESPACE = Regex("""\s+""")

        // Numeric value with optional thousand separators and decimals.
        private const val AMOUNT_VALUE = """[\d,]+(?:\.\d{1,2})?"""

        // Merchant: greedy across letters/digits/space and a few separators,
        // bounded by the surrounding anchors in the synthesized pattern.
        private const val MERCHANT_VALUE = """[A-Za-z0-9 ._&'/-]+?"""

        // Account / card last 4: at least 4 digits, possibly preceded by Xs.
        private const val ACCOUNT_VALUE = """[X\d]{4,20}"""
    }
}
