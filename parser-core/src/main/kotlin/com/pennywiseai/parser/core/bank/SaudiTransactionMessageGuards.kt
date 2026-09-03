package com.pennywiseai.parser.core.bank

internal object SaudiTransactionMessageGuards {

    fun isDeclinedOrFailed(message: String): Boolean {
        if (FinancialMessageSafety.isSecurityCode(message)) return true

        // A refund/reversal legitimately *describes* an earlier failed payment
        // ("refund for your previously declined purchase"), and that mention must
        // not make the credit itself look failed. Only the words tied to that
        // earlier transaction are neutralised — the message is then judged
        // normally, so a refund that itself failed is still caught.
        val scrubbed = redactHistoricalFailureMentions(message)
        val lower = scrubbed.lowercase()

        if (FinancialMessageSafety.hasExplicitFailure(scrubbed, ARABIC_FAILURES)) return true
        if (ENGLISH_FAILURES.any { lower.contains(it) }) return true
        return STATUS_BEFORE_TRANSACTION.containsMatchIn(lower) ||
            TRANSACTION_BEFORE_STATUS.containsMatchIn(lower) ||
            ARABIC_TRANSACTION_FAILURE.containsMatchIn(scrubbed)
    }

    /**
     * Blanks out failure wording that belongs to a *referenced* earlier
     * transaction rather than to this message, e.g. "previously declined" or
     * "the original failed purchase".
     *
     * Everything else is left intact, so "Refund for your previous purchase
     * failed" keeps its own "failed" and is still recognised as a failure.
     */
    private fun redactHistoricalFailureMentions(message: String): String =
        HISTORICAL_FAILURE_MENTION.replace(message, " ")

    fun isPromotionalOrOperationalNotice(message: String): Boolean {
        val lower = message.lowercase()
        return FinancialMessageSafety.isOperationalOrPromotionalNotice(message) ||
            OPERATIONAL.any { lower.contains(it) }
    }

    private val OPERATIONAL = listOf(
        "successfully added to apple pay", "wallet limit", "your limit has gone up",
        "cashback offer", "exclusive offer", "special offer", "promotional message", "unsubscribe"
    )
    private val ARABIC_FAILURES = listOf(
        "رصيد غير كافي", "عملية مرفوضة", "تم رفض العملية", "فشل العملية",
        "عملية فاشلة", "تعذر إتمام العملية", "لم تتم العملية", "عملية غير ناجحة"
    )
    private val ENGLISH_FAILURES = listOf(
        "insufficient balance", "insufficient funds", "transaction declined",
        "transaction failed", "transaction rejected", "purchase declined",
        "purchase failed", "purchase rejected", "payment declined", "payment failed",
        "payment rejected", "transfer declined", "transfer failed", "transfer rejected"
    )
    private val ARABIC_TRANSACTION_FAILURE = Regex("""(?:شراء|حوالة|سداد|خصم|سحب|إيداع)(?:\s+دولي)?\s+مرفوض(?:ة)?""")
    private val STATUS_BEFORE_TRANSACTION = Regex("""\b(?:declined|failed|rejected)\s+(?:card\s+)?(?:transaction|purchase|payment|transfer)\b""")
    private val TRANSACTION_BEFORE_STATUS = Regex("""\b(?:card\s+)?(?:transaction|purchase|payment|transfer)\s+(?:was\s+|has\s+been\s+)?(?:declined|failed|rejected)\b""")

    /**
     * A failure word sitting directly against a historical marker, in either
     * order: "previously declined", "original failed", "declined earlier".
     *
     * Adjacency is the whole point. In "refund for your previously declined
     * purchase" the failure describes the earlier purchase; in "refund for your
     * previous purchase failed" a noun separates them and "failed" is this
     * message's own verb. Allowing words in between would swallow the second
     * case and let a failed refund through as income.
     */
    private val HISTORICAL_FAILURE_MENTION = Regex(
        """(?i)(?:\b(?:previous(?:ly)?|earlier|original|prior|former)\s+(?:declined|failed|rejected)\b""" +
            """|\b(?:declined|failed|rejected)\s+(?:previous(?:ly)?|earlier|original|prior|former)\b""" +
            """|(?:سابقة|السابقة|الأصلية|سابق)\s+(?:مرفوضة|مرفوض|فاشلة)""" +
            """|(?:مرفوضة|مرفوض|فاشلة)\s+(?:سابقة|السابقة|الأصلية|سابق))"""
    )
}
