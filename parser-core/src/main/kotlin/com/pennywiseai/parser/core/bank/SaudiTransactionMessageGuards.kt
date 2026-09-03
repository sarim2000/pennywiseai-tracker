package com.pennywiseai.parser.core.bank

internal object SaudiTransactionMessageGuards {

    /**
     * Whether the message says a payment did not go through.
     *
     * A failure word anywhere means failure — including in a refund that merely
     * *refers* to an earlier failed payment, which therefore gets dropped.
     *
     * That is deliberate. "Refund of a payment that failed" and "refund that
     * failed" are the same words in a different order, and every structural rule
     * tried had a counterexample: "refund transaction declined earlier" (the
     * transaction is the refund's), "refund payment declined earlier" (the
     * payment is the refund), "reversal of the original transaction was declined"
     * (the reversal is what failed).
     *
     * So this picks a failure mode instead of pretending to resolve the
     * ambiguity: a dropped refund leaves a transaction missing, which a user can
     * add by hand; an admitted failed one books income that never arrived and
     * corrupts the balance.
     *
     * ponytail: whole-message check, no attempt at attaching the failure to a
     * noun. If dropped refunds turn out to matter, the upgrade is an allowlist of
     * real bank phrasings — not more grammar heuristics.
     */
    fun isDeclinedOrFailed(message: String): Boolean {
        if (FinancialMessageSafety.isSecurityCode(message)) return true

        val lower = message.lowercase()
        if (FinancialMessageSafety.hasExplicitFailure(message, ARABIC_FAILURES)) return true
        if (ENGLISH_FAILURES.any { lower.contains(it) }) return true

        // The phrase lists above are keyed to debit nouns (purchase, transfer,
        // شراء، حوالة…), so a credit that was itself refused — "refund declined",
        // "استرجاع … مرفوض" — matches none of them.
        if (EN_CREDIT_NOUNS.any { lower.contains(it) } &&
            EN_FAILURE_WORDS.any { lower.contains(it) }
        ) return true
        if (ARABIC_CREDIT_NOUNS.any { message.contains(it) } &&
            ARABIC_FAILURE_WORDS.any { message.contains(it) }
        ) return true

        return STATUS_BEFORE_TRANSACTION.containsMatchIn(lower) ||
            TRANSACTION_BEFORE_STATUS.containsMatchIn(lower) ||
            ARABIC_TRANSACTION_FAILURE.containsMatchIn(message)
    }

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

    // "cashback" is absent on purpose: "cashback offer" is promotional wording
    // handled by [isPromotionalOrOperationalNotice], and pairing it with a
    // failure word here would reject unrelated marketing messages.
    private val EN_CREDIT_NOUNS = listOf("refund", "reversal")
    private val EN_FAILURE_WORDS = listOf("declined", "failed", "rejected", "unsuccessful")
    private val ARABIC_CREDIT_NOUNS = listOf("استرجاع", "مرتجع", "إرجاع", "تصحيح")
    private val ARABIC_FAILURE_WORDS = listOf("مرفوضة", "مرفوض", "فاشلة")

    private val ARABIC_TRANSACTION_FAILURE = Regex("""(?:شراء|حوالة|سداد|خصم|سحب|إيداع)(?:\s+دولي)?\s+مرفوض(?:ة)?""")
    private val STATUS_BEFORE_TRANSACTION = Regex("""\b(?:declined|failed|rejected)\s+(?:card\s+)?(?:transaction|purchase|payment|transfer)\b""")
    private val TRANSACTION_BEFORE_STATUS = Regex("""\b(?:card\s+)?(?:transaction|purchase|payment|transfer)\s+(?:was\s+|has\s+been\s+)?(?:declined|failed|rejected)\b""")
}
