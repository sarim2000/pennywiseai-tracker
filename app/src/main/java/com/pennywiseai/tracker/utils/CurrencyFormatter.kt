package com.pennywiseai.tracker.utils

import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.tracker.data.preferences.NumberFormatStyle
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Utility object for formatting currency values with multi-currency support
 */
object CurrencyFormatter {

    private val INDIAN_LOCALE = Locale.Builder().setLanguage("en").setRegion("IN").build()

    /** Western/international digit grouping (1,000,000). */
    private val INTERNATIONAL_LOCALE = Locale.US

    /** Currencies that use Indian lakh/crore grouping under [NumberFormatStyle.AUTO]. */
    private val INDIAN_NOTATION_CURRENCIES = setOf("INR", "NPR", "PKR")

    /**
     * The user's chosen number-format style. A stateless object can't read DataStore,
     * so this @Volatile field is pushed from [com.pennywiseai.tracker.PennyWiseApplication]
     * which collects the preference Flow at startup. Defaults to AUTO (currency-driven).
     */
    @Volatile
    var numberFormatStyle: NumberFormatStyle = NumberFormatStyle.AUTO

    /**
     * The locale that drives digit grouping for [currencyCode], honouring the
     * user's [numberFormatStyle]. AUTO keeps each currency's native grouping
     * (Indian for INR/NPR/PKR, international elsewhere).
     */
    private fun groupingLocale(currencyCode: String?): Locale = when (numberFormatStyle) {
        NumberFormatStyle.INDIAN -> INDIAN_LOCALE
        NumberFormatStyle.INTERNATIONAL -> INTERNATIONAL_LOCALE
        NumberFormatStyle.AUTO ->
            if (currencyCode != null && currencyCode in INDIAN_NOTATION_CURRENCIES) {
                INDIAN_LOCALE
            } else {
                CURRENCY_LOCALES[currencyCode] ?: INTERNATIONAL_LOCALE
            }
    }

    /** Whether to abbreviate large values with Indian L/Cr (vs western K/M). */
    private fun useIndianAbbreviation(currencyCode: String): Boolean = when (numberFormatStyle) {
        NumberFormatStyle.INDIAN -> true
        NumberFormatStyle.INTERNATIONAL -> false
        NumberFormatStyle.AUTO -> currencyCode in INDIAN_NOTATION_CURRENCIES
    }

    /**
     * Currencies whose ISO 4217 minor unit is 3 (three decimal places),
     * e.g. Jordanian Dinar, Kuwaiti Dinar. Used so amounts aren't truncated
     * to 2 decimals on the symbol/fallback display paths.
     */
    private val THREE_DECIMAL_CURRENCIES = setOf("JOD", "KWD", "BHD", "OMR")

    /**
     * Currency symbol mapping for display
     */
    private val CURRENCY_SYMBOLS = mapOf(
        "INR" to "₹",
        "PKR" to "Rs",
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "AED" to "AED",
        "SGD" to "S$",
        "CAD" to "C$",
        "MXN" to "MX$",
        "AUD" to "A$",
        "JPY" to "¥",
        "CNY" to "¥",
        "IRR" to "﷼",
        "NPR" to "₨",
        "ETB" to "ብር",
        "THB" to "฿",
        "MYR" to "RM",
        "KWD" to "KD",
        "KRW" to "₩",
        "NGN" to "₦",
        "TZS" to "TSh",
        "BRL" to "R$",
        "EGP" to "E£",
        "JOD" to "JD",
        "BHD" to "BD",
        "OMR" to "RO"
    )

    /**
     * Locale mapping for different currencies
     */
    private val CURRENCY_LOCALES = mapOf(
        "INR" to INDIAN_LOCALE,
        "USD" to Locale.US,
        "EUR" to Locale.GERMANY,
        "GBP" to Locale.UK,
        "AED" to Locale.Builder().setLanguage("en").setRegion("AE").build(),
        "SGD" to Locale.Builder().setLanguage("en").setRegion("SG").build(),
        "CAD" to Locale.CANADA,
        "MXN" to Locale.Builder().setLanguage("es").setRegion("MX").build(),
        "AUD" to Locale.Builder().setLanguage("en").setRegion("AU").build(),
        "JPY" to Locale.JAPAN,
        "CNY" to Locale.CHINA,
        "PKR" to Locale.Builder().setLanguage("en").setRegion("PK").build(),
        "IRR" to Locale.Builder().setLanguage("fa").setRegion("IR").build(),
        "NPR" to Locale.Builder().setLanguage("ne").setRegion("NP").build(),
        "ETB" to Locale.Builder().setLanguage("am").setRegion("ET").build(),
        "THB" to Locale.Builder().setLanguage("th").setRegion("TH").build(),
        "MYR" to Locale.Builder().setLanguage("ms").setRegion("MY").build(),
        "KWD" to Locale.Builder().setLanguage("en").setRegion("KW").build(),
        "KRW" to Locale.KOREA,
        "NGN" to Locale.Builder().setLanguage("en").setRegion("NG").build(),
        "TZS" to Locale.Builder().setLanguage("en").setRegion("TZ").build(),
        "BRL" to Locale.Builder().setLanguage("pt").setRegion("BR").build(),
        "EGP" to Locale.Builder().setLanguage("en").setRegion("EG").build(),
        "JOD" to Locale.Builder().setLanguage("en").setRegion("JO").build(),
        "BHD" to Locale.Builder().setLanguage("en").setRegion("BH").build(),
        "OMR" to Locale.Builder().setLanguage("en").setRegion("OM").build()
    )

    /**
     * Formats a BigDecimal amount as currency with the specified currency code
     */
    fun formatCurrency(amount: BigDecimal, currencyCode: String = "INR"): String {
        if (currencyCode == "PKR") {
            return "${CURRENCY_SYMBOLS["PKR"]}${formatAmount(amount, "PKR")}"
        }
        return try {
            val locale = groupingLocale(currencyCode)
            val formatter = NumberFormat.getCurrencyInstance(locale)

            // Set the currency if supported
            try {
                formatter.currency = Currency.getInstance(currencyCode)
            } catch (e: Exception) {
                // If currency not supported, use symbol mapping
                val symbol = CURRENCY_SYMBOLS[currencyCode] ?: currencyCode
                return "$symbol${formatAmount(amount, currencyCode)}"
            }

            // Show decimals only if they exist
            formatter.minimumFractionDigits = 0
            formatter.maximumFractionDigits = if (currencyCode in THREE_DECIMAL_CURRENCIES) 3 else 2
            formatter.format(amount)
        } catch (e: Exception) {
            // Fallback to symbol + amount
            val symbol = CURRENCY_SYMBOLS[currencyCode] ?: currencyCode
            "$symbol${formatAmount(amount, currencyCode)}"
        }
    }

    /**
     * Formats a Double amount as currency with the specified currency code
     */
    fun formatCurrency(amount: Double, currencyCode: String = "INR"): String {
        return formatCurrency(amount.toBigDecimal(), currencyCode)
    }

    /**
     * Legacy method for backward compatibility - defaults to INR
     */
    fun formatCurrency(amount: BigDecimal): String {
        return formatCurrency(amount, "INR")
    }

    /**
     * Legacy method for backward compatibility - defaults to INR
     */
    fun formatCurrency(amount: Double): String {
        return formatCurrency(amount.toBigDecimal(), "INR")
    }

    /**
     * Formats a set of per-currency totals. We never sum across currencies (that
     * would be meaningless), so a single-currency total renders as one figure
     * (e.g. "$600") while a mixed set renders each currency's total joined by
     * " · " (e.g. "$600 · ₹500"). Zero/empty totals fall back to a single zero
     * in [fallbackCurrency]. [signPrefix] (e.g. "-" or "+") is applied to each
     * figure.
     */
    fun formatByCurrency(
        totalsByCurrency: Map<String, BigDecimal>,
        signPrefix: String = "",
        fallbackCurrency: String = "INR"
    ): String {
        val nonZero = totalsByCurrency.filterValues { it.signum() != 0 }
        if (nonZero.isEmpty()) {
            return "$signPrefix${formatCurrency(BigDecimal.ZERO, fallbackCurrency)}"
        }
        return nonZero.entries
            .sortedByDescending { it.value.abs() }
            .joinToString(" · ") { (currency, total) ->
                "$signPrefix${formatCurrency(total, currency)}"
            }
    }

    /**
     * Formats just the numeric amount without currency symbol
     */
    private fun formatAmount(amount: BigDecimal, currencyCode: String? = null): String {
        val formatter = NumberFormat.getNumberInstance(groupingLocale(currencyCode))
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = if (currencyCode != null && currencyCode in THREE_DECIMAL_CURRENCIES) 3 else 2
        return formatter.format(amount)
    }

    /**
     * Gets the currency symbol for a given currency code
     */
    fun getCurrencySymbol(currencyCode: String): String {
        return CURRENCY_SYMBOLS[currencyCode] ?: currencyCode
    }

    /**
     * Returns all supported currency codes
     */
    fun getSupportedCurrencies(): List<String> = CURRENCY_SYMBOLS.keys.toList()

    /**
     * Formats large currency values in abbreviated form for chart axes.
     * Uses Indian notation (L/Cr) or Western notation (K/M) per the user's
     * [numberFormatStyle] (AUTO = L/Cr for INR/NPR/PKR, K/M otherwise).
     */
    fun formatAbbreviated(value: Double, currencyCode: String): String {
        val symbol = getCurrencySymbol(currencyCode)
        val absValue = kotlin.math.abs(value)
        val useIndianNotation = useIndianAbbreviation(currencyCode)
        return when {
            useIndianNotation && absValue >= 1_00_00_000 ->
                "${symbol}${String.format("%.1f", absValue / 1_00_00_000)}Cr"
            useIndianNotation && absValue >= 1_00_000 ->
                "${symbol}${String.format("%.1f", absValue / 1_00_000)}L"
            absValue >= 10_000_000 ->
                "${symbol}${String.format("%.1f", absValue / 1_000_000)}M"
            absValue >= 1_000 ->
                "${symbol}${String.format("%.1f", absValue / 1_000)}K"
            absValue > 0 -> "${symbol}${absValue.toInt()}"
            else -> "${symbol}0"
        }
    }

    /**
     * Resolves the effective currency for an account. Manual accounts store the
     * currency the user chose — trust it. SMS-tracked accounts fall back to the bank
     * parser's currency (their stored value may be the INR default even for a non-INR
     * bank). Shared by Account Detail, onboarding, and Settings so the rule stays
     * consistent everywhere.
     */
    fun resolveAccountCurrency(
        sourceType: String?,
        storedCurrency: String,
        bankName: String?
    ): String {
        return if (sourceType == "MANUAL") {
            storedCurrency
        } else {
            getBankBaseCurrency(bankName)
        }
    }

    /**
     * Gets the base currency for a bank using the BankParserFactory
     * Returns INR as default for unknown banks
     */
    fun getBankBaseCurrency(bankName: String?): String {
        if (bankName == null) return "INR"

        // Try to find a parser that can handle this bank name
        val parser = BankParserFactory.getParserByName(bankName)
        return parser?.getCurrency() ?: "INR"
    }
}