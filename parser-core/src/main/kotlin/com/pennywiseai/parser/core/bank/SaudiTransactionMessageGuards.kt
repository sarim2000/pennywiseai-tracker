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

        // A credit that is still described as rejected once the historical
        // wording is out of the way. The Arabic failure vocabulary above is
        // phrase-based and keyed to debit nouns (شراء، حوالة، سداد…), so a bare
        // "استرجاع … مرفوض" — a refund that was itself rejected — slipped past it.
        if (ARABIC_CREDIT_NOUNS.any { scrubbed.contains(it) } &&
            ARABIC_FAILURE_WORDS.any { scrubbed.contains(it) }
        ) return true
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
    private val ARABIC_CREDIT_NOUNS = listOf("استرجاع", "مرتجع", "إرجاع", "تصحيح")
    private val ARABIC_FAILURE_WORDS = listOf("مرفوضة", "مرفوض", "فاشلة")
    private val ARABIC_TRANSACTION_FAILURE = Regex("""(?:شراء|حوالة|سداد|خصم|سحب|إيداع)(?:\s+دولي)?\s+مرفوض(?:ة)?""")
    private val STATUS_BEFORE_TRANSACTION = Regex("""\b(?:declined|failed|rejected)\s+(?:card\s+)?(?:transaction|purchase|payment|transfer)\b""")
    private val TRANSACTION_BEFORE_STATUS = Regex("""\b(?:card\s+)?(?:transaction|purchase|payment|transfer)\s+(?:was\s+|has\s+been\s+)?(?:declined|failed|rejected)\b""")

    /**
     * A failure word sitting directly against a historical marker, in either
     * order: "previously declined", "original failed", "declined earlier" —
     * and only when it qualifies a *named kind of payment* — the thing the
     * failure actually describes. Which side that noun sits on depends on the
     * word order: it trails "previously declined purchase" and leads "purchase
     * declined earlier".
     *
     * The noun must come from a closed list of debit words. Accepting any
     * non-credit word instead let generic ones through — "refund transaction
     * declined earlier" is the refund's own transaction — so an unrecognised
     * phrasing now fails closed and the credit is rejected rather than booked.
     *
     * That trailing noun is what makes the phrase historical. "Refund for your
     * previously declined purchase" qualifies the purchase, so the refund is
     * real. "Your previously declined refund" names a credit noun, and "Refund
     * was previously declined" is followed only by a line break — in both the
     * failure is this message's own, and the credit never landed. The qualifier
     * must sit on the same line, or the next field's label ("Amount: ...") would
     * be mistaken for it.
     *
     * Adjacency is the whole point. In "refund for your previously declined
     * purchase" the failure describes the earlier purchase; in "refund for your
     * previous purchase failed" a noun separates them and "failed" is this
     * message's own verb. Allowing words in between would swallow the second
     * case and let a failed refund through as income.
     */
    // The kinds of payment a refund can legitimately be *about*. Deliberately a
    // closed list of specific debit nouns: a generic one like "transaction" or
    // "العملية" doesn't say whose transaction it was, and "refund transaction
    // declined earlier" is the refund's own. Anything not named here is treated
    // as the credit itself, so an unrecognised phrasing fails closed.
    private const val EN_DEBIT_NOUNS = "purchase|payment|transfer|withdrawal|charge|debit|pos"
    private const val AR_DEBIT_NOUNS = "شراء|حوالة|تحويل|سداد|خصم|سحب|إيداع|مشتريات"

    private val HISTORICAL_FAILURE_MENTION = Regex(
        """(?i)(?:\b(?:previous(?:ly)?|earlier|original|prior|former)[ \t]+(?:declined|failed|rejected)[ \t]+(?:$EN_DEBIT_NOUNS)\b""" +
            """|\b(?:$EN_DEBIT_NOUNS)[ \t]+(?:declined|failed|rejected)[ \t]+(?:previous(?:ly)?|earlier|original|prior|former)\b""" +
            """|(?:$AR_DEBIT_NOUNS)[ \t]+(?:سابقة|السابقة|الأصلية|سابق)[ \t]+(?:مرفوضة|مرفوض|فاشلة)""" +
            """|(?:$AR_DEBIT_NOUNS)[ \t]+(?:مرفوضة|مرفوض|فاشلة)[ \t]+(?:سابقة|السابقة|الأصلية|سابق))"""
    )

}
