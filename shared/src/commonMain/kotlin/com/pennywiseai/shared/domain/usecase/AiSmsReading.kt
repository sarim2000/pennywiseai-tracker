package com.pennywiseai.shared.domain.usecase

/**
 * What the on-device model (iOS Foundation Models) read from a bank SMS that no
 * parser knows. Untrusted: [AiSmsValidator] decides whether it's saved.
 */
data class AiSmsReading(
    val isBankTransaction: Boolean,
    val amount: String,
    val currency: String,
    /** expense, income, credit (a credit-card spend) or transfer. */
    val type: String,
    val merchant: String?,
    val accountLast4: String?,
    val bankName: String?
)

/**
 * Guards the model's reading of an SMS before it becomes a transaction. A small
 * model can invent an amount or read an OTP as a spend, so the amount must be
 * printed in the SMS itself, and anything doubtful is dropped rather than saved.
 */
object AiSmsValidator {
    /** Marks every transaction the model read, so the user knows to check it. */
    const val AI_NOTE = "Read by AI – check it"

    /** [reading] as a transaction to save, or null when it isn't safe to save. */
    internal fun validate(body: String, reading: AiSmsReading, knownBank: String?): ParsedBankSms? {
        if (!reading.isBankTransaction || NOT_A_SPEND.containsMatchIn(body)) return null
        val currency = normalizeCurrency(reading.currency) ?: return null
        val amount = canonicalAmount(reading.amount)?.takeIf { it != "0" && it in amountsIn(body) } ?: return null
        val type = TYPES[reading.type.trim().lowercase()] ?: return null
        val (whole, fraction) = amount.split('.').let { it[0] to it.getOrElse(1) { "" } }
        return ParsedBankSms(
            amount = "$whole.${fraction.padEnd(2, '0')}",
            type = type,
            merchant = reading.merchant?.trim()?.takeIf { it.isNotEmpty() && it.length <= 60 },
            reference = null,
            // Only digits the SMS really shows, never a guess.
            accountLast4 = reading.accountLast4?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 && it in body },
            balance = null,
            bankName = knownBank ?: reading.bankName?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 } ?: "Unknown bank",
            transactionHash = aiTransactionHash(body),
            isFromCard = false,
            currency = currency,
            accountCurrency = null,
            isMobileWallet = false
        )
    }

    /** "₹", "Rs.", "INR" → INR; any other ISO code PennyWise formats; else null. */
    internal fun normalizeCurrency(raw: String): String? {
        val code = raw.trim().uppercase().removeSuffix(".")
        if (code == "₹" || code == "RS" || code == "INR") return "INR"
        return code.takeIf { it in SUPPORTED_CURRENCIES }
    }

    /**
     * "Rs. 1,500.00" → "1500", "318.50" → "318.5": digits only, no grouping, no
     * trailing fraction zeros, so the model's amount and the SMS's compare equal.
     * Null for anything that isn't one plain amount (more than 2 decimals, too big).
     */
    internal fun canonicalAmount(raw: String): String? {
        val digits = raw.filter { it.isDigit() || it == '.' }.trim('.')
        val match = AMOUNT.matchEntire(digits) ?: return null
        val whole = match.groupValues[1].trimStart('0').ifEmpty { "0" }
        val fraction = match.groupValues[2].trimEnd('0')
        if (whole.length > 16) return null // the cap parser amounts have (see BankParserBridge)
        return if (fraction.isEmpty()) whole else "$whole.$fraction"
    }

    /**
     * Every amount [body] prints next to a currency ("Rs.1,250.50", "₹318", "INR 318",
     * "75.25 AED"), canonical. A bare number (a date, a spaced "XX 4321") isn't money.
     */
    internal fun amountsIn(body: String): Set<String> =
        MONEY.findAll(body)
            .mapNotNull { m -> canonicalAmount((m.groups[1] ?: m.groups[2])!!.value.replace(",", "")) }
            .toSet()

    /** Same SMS, same hash: a second automation run is a duplicate, as for parsed SMS. */
    internal fun aiTransactionHash(body: String): String {
        var hash = 0xcbf29ce484222325uL // FNV-1a, 64-bit
        for (c in body.trim().lowercase()) {
            hash = (hash xor c.code.toULong()) * 0x100000001b3uL
        }
        return "ai_" + hash.toString(16)
    }

    private val AMOUNT = Regex("""(\d+)(?:\.(\d{1,2}))?""")
    // An OTP or a payment request quotes an amount that hasn't moved; the debit SMS follows.
    private val NOT_A_SPEND = Regex("""\b(OTP|one[- ]time password|verification code)\b""", RegexOption.IGNORE_CASE)
    private val TYPES = mapOf("expense" to "EXPENSE", "income" to "INCOME", "credit" to "CREDIT", "transfer" to "TRANSFER")

    // The codes the iOS app has a symbol for (CurrencyFormatter.swift).
    private val SUPPORTED_CURRENCIES = setOf(
        "INR", "USD", "EUR", "GBP", "JPY", "CNY", "KRW", "THB", "AED", "SAR", "PKR", "NPR", "BDT", "LKR",
        "SGD", "MYR", "IDR", "PHP", "VND", "TWD", "HKD", "CAD", "AUD", "NZD", "CHF", "SEK", "NOK", "DKK",
        "PLN", "CZK", "HUF", "RUB", "TRY", "BRL", "MXN", "ZAR", "KES", "NGN", "EGP", "KWD", "BHD", "OMR",
        "QAR", "ILS", "JOD", "ARS", "CLP", "UYU"
    )

    // A number with a currency right before it (group 1) or a code right after it (group 2).
    private val MONEY = SUPPORTED_CURRENCIES.joinToString("|").let { codes ->
        val number = """(\d[\d,]*(?:\.\d+)?)"""
        // Codes stay upper case: "try 2 times" must not read as TRY 2.
        Regex("""(?:\b(?:[Rr][Ss]|$codes)\.?|[₹$€£])\s*$number|$number\s*(?:$codes)\b""")
    }
}
