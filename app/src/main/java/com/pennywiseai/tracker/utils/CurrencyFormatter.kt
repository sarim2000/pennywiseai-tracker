package com.pennywiseai.tracker.utils

import com.pennywiseai.parser.core.bank.BankParserFactory
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Utility object for formatting currency values with multi-currency support
 */
object CurrencyFormatter {

    private val INDIAN_LOCALE = Locale.Builder().setLanguage("en").setRegion("IN").build()

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
        "AUD" to "A$",
        "JPY" to "¥",
        "CNY" to "¥",
        "IRR" to "﷼",
        "NPR" to "₨",
        "ETB" to "ብር",
        "THB" to "฿",
        "MYR" to "RM",
        "KWD" to "KD",
        "KRW" to "₩"
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
        "KRW" to Locale.KOREA
    )

    /**
     * Formats a BigDecimal amount as currency with the specified currency code
     */
    fun formatCurrency(amount: BigDecimal, currencyCode: String = "INR"): String {
        if (currencyCode == "PKR") {
            return "${CURRENCY_SYMBOLS["PKR"]}${formatAmount(amount)}"
        }
        return try {
            val locale = CURRENCY_LOCALES[currencyCode] ?: INDIAN_LOCALE
            val formatter = NumberFormat.getCurrencyInstance(locale)

            // Set the currency if supported
            try {
                formatter.currency = Currency.getInstance(currencyCode)
            } catch (e: Exception) {
                // If currency not supported, use symbol mapping
                val symbol = CURRENCY_SYMBOLS[currencyCode] ?: currencyCode
                return "$symbol${formatAmount(amount)}"
            }

            // Show decimals only if they exist
            formatter.minimumFractionDigits = 0
            formatter.maximumFractionDigits = 2
            formatter.format(amount)
        } catch (e: Exception) {
            // Fallback to symbol + amount
            val symbol = CURRENCY_SYMBOLS[currencyCode] ?: currencyCode
            "$symbol${formatAmount(amount)}"
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
     * Formats just the numeric amount without currency symbol
     */
    private fun formatAmount(amount: BigDecimal): String {
        val formatter = NumberFormat.getNumberInstance(INDIAN_LOCALE)
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 2
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