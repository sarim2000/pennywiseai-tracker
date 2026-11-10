package com.pennywiseai.tracker.presentation.exchangerates

import java.math.BigDecimal
import java.time.LocalDateTime

/** What the exchange-rates screen needs from the app's data layer. Implemented in `di/`. */
interface ExchangeRatesData {
    /** Every currency the user has transactions in. */
    suspend fun getUserCurrencies(): List<String>
    suspend fun getActiveRates(currencies: List<String>): List<ExchangeRateSnapshot>
    suspend fun refreshExchangeRates(currencies: List<String>)
    suspend fun setCustomRate(fromCurrency: String, toCurrency: String, rate: BigDecimal)
    suspend fun clearCustomRate(fromCurrency: String, toCurrency: String)
    suspend fun clearAllCustomRates()
}

/** One rate as the screen shows it: `1 fromCurrency = rate toCurrency`. */
data class ExchangeRateSnapshot(
    val fromCurrency: String,
    val toCurrency: String,
    val rate: BigDecimal,
    val updatedAt: LocalDateTime,
    val isCustomRate: Boolean
)
