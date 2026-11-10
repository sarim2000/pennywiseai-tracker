package com.pennywiseai.shared.domain.money

import kotlin.math.abs
import kotlin.math.roundToLong

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

    /** "450.00" INR → 45000, "1.235" JOD → 1235. Digits past the currency's precision are cut, never rounded up. */
    fun fromPlain(plainAmount: String, currency: String): Long {
        val digits = digits(currency)
        val negative = plainAmount.startsWith("-")
        val (whole, fraction) = plainAmount.removePrefix("-").split('.').let { it[0] to it.getOrElse(1) { "" } }
        val minor = whole.ifEmpty { "0" }.toLong() * factor(currency) + fraction.padEnd(digits, '0').take(digits).toLong()
        return if (negative) -minor else minor
    }

    /** A typed or spoken amount (12.5) in [currency]'s minor units, rounded half away from zero. */
    fun fromDouble(amount: Double, currency: String): Long = (amount * factor(currency)).roundToLong()

    /**
     * [amount] in [currency]'s minor units only when it fits that precision exactly:
     * 1.235 is 1235 in JOD but null in USD (123.5 cents), so a match never rounds.
     */
    fun exactMinor(amount: Double, currency: String): Long? {
        val scaled = amount * factor(currency)
        if (!scaled.isFinite() || abs(scaled) >= 9.0e15) return null // past 2^53 a Double has no exact cents
        val minor = scaled.roundToLong()
        return minor.takeIf { abs(scaled - minor) < 1e-6 }
    }

    /** 1235 JOD → "1.235", 45000 INR → "450.00". */
    fun toPlain(amountMinor: Long, currency: String): String {
        val factor = factor(currency)
        val sign = if (amountMinor < 0) "-" else ""
        val a = abs(amountMinor)
        return "$sign${a / factor}.${(a % factor).toString().padStart(digits(currency), '0')}"
    }
}
