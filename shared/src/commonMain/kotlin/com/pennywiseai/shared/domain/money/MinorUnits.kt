package com.pennywiseai.shared.domain.money

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sign

/**
 * How the shared layer (and so iOS) stores money: amountMinor = value × 10^[digits].
 * Three-decimal dinars/rials keep their fils/baisa (JOD 1.235 = 1235); every other
 * currency is value × 100, as it always was, zero-decimal ones (JPY, KRW) included.
 * Android keeps BigDecimal and isn't affected.
 */
object MinorUnits {
    /**
     * Android's CurrencyFormatter.THREE_DECIMAL_CURRENCIES plus the other ISO 4217
     * three-decimal codes the parsers emit. IQD stays at 2: its fils aren't in use
     * (CLDR gives it no decimals). Adding a code here changes how its stored rows
     * read: it needs a new migration that rescales them, like SharedDatabase's MIGRATION_2_3.
     */
    val THREE_DECIMAL_CURRENCIES: Set<String> = setOf("BHD", "JOD", "KWD", "LYD", "OMR", "TND")

    fun digits(currency: String): Int = if (currency.uppercase() in THREE_DECIMAL_CURRENCIES) 3 else 2

    /** 10^[digits]: what one unit of [currency] is in amountMinor. */
    fun factor(currency: String): Long = if (digits(currency) == 3) 1000L else 100L

    /**
     * "450.00" INR → 45000, "1.235" JOD → 1235. Digits past the currency's precision are cut,
     * never rounded up. Null for anything but plain digits, or more than 18 - digits integer
     * digits (16 for INR, 15 for JOD), so value × 10^digits stays under 10^18 and never wraps.
     */
    fun fromPlain(plainAmount: String, currency: String): Long? {
        val digits = digits(currency)
        val negative = plainAmount.startsWith("-")
        val parts = plainAmount.removePrefix("-").split('.')
        if (parts.size > 2) return null
        val whole = parts[0].ifEmpty { "0" }
        val fraction = parts.getOrElse(1) { "" }.padEnd(digits, '0').take(digits)
        if (whole.length > 18 - digits || !whole.all(Char::isDigit) || !fraction.all(Char::isDigit)) return null
        val minor = whole.toLong() * factor(currency) + fraction.toLong()
        return if (negative) -minor else minor
    }

    /** A typed or spoken amount (12.5) in [currency]'s minor units, rounded half away from zero. */
    fun fromDouble(amount: Double, currency: String): Long = (amount * factor(currency)).roundToLong()

    /**
     * [amount] in [currency]'s minor units only when it fits that precision exactly:
     * 1.235 is 1235 in JOD but null in USD (it isn't whole cents), so a match never rounds.
     * Rounded (half away from zero) and then round-tripped: the minor units read back as the
     * same Double, at any size a Double holds exactly.
     */
    fun exactMinor(amount: Double, currency: String): Long? {
        val factor = factor(currency)
        val scaled = amount * factor
        if (!scaled.isFinite() || abs(scaled) >= 9.0e15) return null // past 2^53 a Double has no exact minor units
        // Not floor(x + 0.5): past 2^52 that addition itself rounds a whole x up to the next integer.
        // x - floor(x) is exact, so splitting off the fraction rounds half away from zero safely.
        val whole = floor(abs(scaled))
        val minor = (sign(scaled) * (if (abs(scaled) - whole >= 0.5) whole + 1 else whole)).toLong()
        return minor.takeIf { it.toDouble() / factor == amount }
    }

    /** 1235 JOD → "1.235", 45000 INR → "450.00". */
    fun toPlain(amountMinor: Long, currency: String): String {
        val factor = factor(currency)
        val sign = if (amountMinor < 0) "-" else ""
        val a = abs(amountMinor)
        return "$sign${a / factor}.${(a % factor).toString().padStart(digits(currency), '0')}"
    }
}
