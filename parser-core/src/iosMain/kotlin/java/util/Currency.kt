package java.util

import platform.Foundation.ISOCurrencyCodes
import platform.Foundation.NSLocale

/** iOS stand-in for `java.util.Currency`: validates ISO 4217 codes like the JDK does. */
class Currency private constructor(val currencyCode: String) {
    companion object {
        fun getInstance(currencyCode: String): Currency {
            require(currencyCode in NSLocale.ISOCurrencyCodes) { "Unknown currency code: $currencyCode" }
            return Currency(currencyCode)
        }
    }
}
