package com.pennywiseai.tracker.presentation.add

import java.math.BigDecimal

/**
 * Best guess at the amount in text shared into the app ("Share to PennyWise"):
 * a UPI receipt, a forwarded bank SMS, or a quick note like "450 swiggy".
 *
 * A currency-tagged figure ("₹450", "Rs. 1,200.50", "INR 99", "$12") wins. A
 * bare number is only trusted in a short note, because long messages are full
 * of dates, reference and account numbers. Returns null rather than guessing,
 * leaving the field for the user.
 */
object SharedTextAmount {
    private const val NUMBER = """(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)"""
    // \b keeps "rs" from matching inside words ("users 50").
    private val TAGGED = Regex("""(?:[₹$€£]|\b(?:rs\.?|inr|usd|aed|sar|eur))\s*$NUMBER""", RegexOption.IGNORE_CASE)
    private val BARE = Regex("""(?<![\d/.-])$NUMBER(?![\d/-])""")
    private const val SHORT_NOTE_CHARS = 40

    fun extract(text: String): BigDecimal? {
        val match = TAGGED.find(text)?.groupValues?.get(1)
            ?: text.takeIf { it.length <= SHORT_NOTE_CHARS }?.let { BARE.find(it)?.groupValues?.get(1) }
            ?: return null
        return match.replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    }
}
