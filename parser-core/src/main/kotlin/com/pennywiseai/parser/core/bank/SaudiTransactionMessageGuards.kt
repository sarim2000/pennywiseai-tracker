package com.pennywiseai.parser.core.bank

internal object SaudiTransactionMessageGuards {

    fun isDeclinedOrFailed(message: String): Boolean {
        val lower = message.lowercase()
        if (FinancialMessageSafety.isSecurityCode(message)) return true
        if (isHistoricalCreditCorrection(lower)) return false
        if (FinancialMessageSafety.hasExplicitFailure(message, ARABIC_FAILURES)) return true
        if (ENGLISH_FAILURES.any { lower.contains(it) }) return true
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
    private val ARABIC_TRANSACTION_FAILURE = Regex("""(?:شراء|حوالة|سداد|خصم|سحب|إيداع)(?:\s+دولي)?\s+مرفوض(?:ة)?""")
    private val STATUS_BEFORE_TRANSACTION = Regex("""\b(?:declined|failed|rejected)\s+(?:card\s+)?(?:transaction|purchase|payment|transfer)\b""")
    private val TRANSACTION_BEFORE_STATUS = Regex("""\b(?:card\s+)?(?:transaction|purchase|payment|transfer)\s+(?:was\s+|has\s+been\s+)?(?:declined|failed|rejected)\b""")

    private fun isHistoricalCreditCorrection(lower: String): Boolean {
        val credit = listOf(
            "refund", "reversal", "cashback", "correction",
            "استرجاع", "مرتجع", "إرجاع", "عكس العملية", "تصحيح"
        ).any { lower.contains(it) }
        val historical = listOf(
            "previous", "previously", "earlier", "original", "prior", "former",
            "سابق", "سابقة", "السابقة", "الأصلية"
        ).any { lower.contains(it) }
        return credit && historical
    }
}
