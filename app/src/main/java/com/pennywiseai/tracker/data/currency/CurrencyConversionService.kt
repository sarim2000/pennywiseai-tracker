package com.pennywiseai.tracker.data.currency

import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyConversionService @Inject constructor(
    private val exchangeRateDao: ExchangeRateDao,
    private val exchangeRateProvider: ExchangeRateProvider,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    // Cache rates for performance
    private val rateCache = mutableMapOf<String, BigDecimal>()
    private var lastCacheUpdate: LocalDateTime = LocalDateTime.MIN

    /**
     * Convert amount from one currency to another
     */
    suspend fun convertAmount(
        amount: BigDecimal,
        fromCurrency: String,
        toCurrency: String,
        forceRefresh: Boolean = false
    ): BigDecimal {
        if (fromCurrency.equals(toCurrency, ignoreCase = true)) {
            return amount
        }

        val rate = getExchangeRate(fromCurrency, toCurrency, forceRefresh)
        return if (rate != null) {
            amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        } else {
            amount // Return original amount if conversion fails
        }
    }

    /**
     * Get exchange rate between two currencies
     */
    suspend fun getExchangeRate(
        fromCurrency: String,
        toCurrency: String,
        forceRefresh: Boolean = false
    ): BigDecimal? {
        val cacheKey = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"

        // Check cache first (unless forced refresh)
        if (!forceRefresh && isCacheValid()) {
            rateCache[cacheKey]?.let { return it }
        }

        // Check database for fresh rates
        val currentTime = LocalDateTime.now()
        val dbRate = exchangeRateDao.getExchangeRate(fromCurrency, toCurrency, currentTime)

        if (dbRate != null && !forceRefresh) {
            // Rate is still valid (expires_at > currentTime), use it
            updateCache(cacheKey, dbRate.rate)
            return dbRate.rate
        }

        // Check if we have any expired rate that we might be able to use if rates aren't stale overall
        if (!forceRefresh) {
            val expiredRate = exchangeRateDao.getExchangeRate(
                fromCurrency,
                toCurrency,
                currentTime.minusHours(24) // Look back up to 24 hours for expired rates
            )

            if (expiredRate != null && !areOverallRatesStale()) {
                // Use expired rate if overall rates aren't stale, but fetch fresh ones soon
                updateCache(cacheKey, expiredRate.rate)
                // Trigger background refresh for next time
                backgroundScope.launch {
                    refreshExchangeRates(listOf(fromCurrency, toCurrency, "USD"))
                }
                return expiredRate.rate
            }
        }

        // Fetch from API if not found, forced refresh, or rates are stale
        return fetchAndCacheRate(fromCurrency, toCurrency)
    }

    /**
     * Check if we have a valid rate for this currency pair
     */
    suspend fun hasValidRate(fromCurrency: String, toCurrency: String): Boolean {
        if (fromCurrency.equals(toCurrency, ignoreCase = true)) {
            return true
        }

        val cacheKey = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"
        if (isCacheValid() && rateCache.containsKey(cacheKey)) {
            return true
        }

        return exchangeRateDao.hasValidRate(fromCurrency, toCurrency) > 0
    }

    /**
     * Refresh exchange rates for an account's currencies
     */
    suspend fun refreshExchangeRatesForAccount(currencies: List<String>) {
        if (currencies.size < 2) return // No conversion needed for single currency

        // Get unique currencies and ensure USD is included for API compatibility
        val uniqueCurrencies = currencies.distinct().toMutableList()
        if (!uniqueCurrencies.contains("USD")) {
            uniqueCurrencies.add("USD")
        }

        refreshExchangeRates(uniqueCurrencies)
    }

    /**
     * Refresh exchange rates for specific currencies using USD as base
     */
    suspend fun refreshExchangeRates(currencies: List<String>) {
        // Use USD as the base currency for the API since it's most commonly supported
        val apiBaseCurrency = "USD"

        // Check if we need to refresh by looking at the newest rate in our database
        if (!shouldRefreshRates(apiBaseCurrency)) {
            println("Currency rates are fresh, skipping refresh")
            return // Rates are still fresh, no need to refresh
        }
        println("Currency rates are stale, refreshing from API")

        // Fetch all exchange rates for the base currency at once with metadata
        val response = exchangeRateProvider.fetchAllExchangeRatesWithMetadata(apiBaseCurrency)

        if (response != null) {
            val allRates = response.rates
            val nextUpdateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(response.nextUpdateTimeUnix),
                ZoneId.systemDefault()
            )
            val lastUpdateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(response.lastUpdateTimeUnix),
                ZoneId.systemDefault()
            )

            // Cache all relevant rates from the API response
            currencies.forEach { fromCurrency ->
                currencies.forEach { toCurrency ->
                    if (fromCurrency != toCurrency) {
                        val rate = if (fromCurrency == apiBaseCurrency) {
                            allRates[toCurrency]
                        } else if (toCurrency == apiBaseCurrency) {
                            allRates[fromCurrency]?.let { fromRate ->
                                BigDecimal.ONE.divide(fromRate, MathContext(10))
                            }
                        } else {
                            // Cross-currency conversion: fromCurrency -> USD -> toCurrency
                            val fromToUsd = allRates[fromCurrency]
                            val usdToTo = allRates[toCurrency]
                            if (fromToUsd != null && usdToTo != null) {
                                usdToTo.divide(fromToUsd, MathContext(10))
                            } else {
                                null
                            }
                        }

                        if (rate != null) {
                            // Skip overwriting custom rates set by user
                            val existing = exchangeRateDao.getExchangeRateIgnoringExpiry(fromCurrency, toCurrency)
                            if (existing?.isCustomRate == true) {
                                // Don't overwrite user's custom rate
                            } else {
                                val entity = ExchangeRateEntity(
                                    fromCurrency = fromCurrency,
                                    toCurrency = toCurrency,
                                    rate = rate,
                                    provider = response.provider,
                                    updatedAt = lastUpdateTime,
                                    updatedAtUnix = response.lastUpdateTimeUnix,
                                    expiresAt = nextUpdateTime,
                                    expiresAtUnix = response.nextUpdateTimeUnix
                                )
                                exchangeRateDao.insertExchangeRate(entity)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the base currency for the app
     */
    private suspend fun getBaseCurrency(): String {
        return userPreferencesRepository.baseCurrency.first() ?: "INR"
    }

    /**
     * Fetch exchange rate from API and cache it
     */
    private suspend fun fetchAndCacheRate(fromCurrency: String, toCurrency: String): BigDecimal? {
        try {
            // Use the metadata method to get proper expiry times even for individual rates
            // We'll use USD as base since that's what the API uses and then convert
            val baseCurrency = "USD"
            val response = exchangeRateProvider.fetchAllExchangeRatesWithMetadata(baseCurrency)

            if (response != null) {
                val allRates = response.rates
                val nextUpdateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(response.nextUpdateTimeUnix),
                    ZoneId.systemDefault()
                )
                val lastUpdateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(response.lastUpdateTimeUnix),
                    ZoneId.systemDefault()
                )

                // Calculate the rate we need
                val rate = if (fromCurrency == baseCurrency) {
                    allRates[toCurrency]
                } else if (toCurrency == baseCurrency) {
                    allRates[fromCurrency]?.let { fromRate ->
                        BigDecimal.ONE.divide(fromRate, MathContext(10))
                    }
                } else {
                    // Cross-currency: fromCurrency -> USD -> toCurrency
                    val fromToUsd = allRates[fromCurrency]
                    val usdToTo = allRates[toCurrency]
                    if (fromToUsd != null && usdToTo != null) {
                        usdToTo.divide(fromToUsd, MathContext(10))
                    } else {
                        null
                    }
                }

                if (rate != null) {
                    val entity = ExchangeRateEntity(
                        fromCurrency = fromCurrency,
                        toCurrency = toCurrency,
                        rate = rate,
                        provider = response.provider,
                        updatedAt = lastUpdateTime,
                        updatedAtUnix = response.lastUpdateTimeUnix,
                        expiresAt = nextUpdateTime, // Use the API's actual next update time
                        expiresAtUnix = response.nextUpdateTimeUnix
                    )

                    exchangeRateDao.insertExchangeRate(entity)
                    val cacheKey = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"
                    updateCache(cacheKey, rate)
                    return rate
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash
            println("Failed to fetch exchange rate for $fromCurrency to $toCurrency: ${e.message}")
        }

        return null
    }

    /**
     * Check if we should refresh rates for the given base currency
     * Returns true if rates are stale or we don't have any rates
     */
    private suspend fun shouldRefreshRates(baseCurrency: String): Boolean {
        val currentTimeUnix = System.currentTimeMillis() / 1000

        // Use the efficient Unix timestamp query to get the latest expiry time
        val maxExpiryTimeUnix = exchangeRateDao.getMaxExpiryTimeUnix(baseCurrency)

        // If we don't have any rates, or they're from old records (timestamp 0), or they've expired, refresh
        return maxExpiryTimeUnix == null || maxExpiryTimeUnix == 0L || maxExpiryTimeUnix < currentTimeUnix
    }

    /**
     * Check if overall rates are stale across all currencies
     */
    private suspend fun areOverallRatesStale(): Boolean {
        return shouldRefreshRates("USD") // USD is our main base currency, so check its rates
    }

    /**
     * Get information about rate freshness for debugging
     */
    suspend fun getRateFreshnessInfo(): RateFreshnessInfo {
        val currentTime = LocalDateTime.now()
        val usdRates = exchangeRateDao.getExchangeRatesForCurrency("USD", currentTime)
        val latestRate = exchangeRateDao.getLatestRate()

        return RateFreshnessInfo(
            hasValidUsdRates = usdRates.isNotEmpty(),
            validUsdRatesCount = usdRates.size,
            latestUpdateTime = latestRate?.updatedAt,
            latestExpiryTime = usdRates.maxOfOrNull { it.expiresAt },
            isStale = areOverallRatesStale(),
            currentTime = currentTime
        )
    }

    /**
     * Update cache with new rate
     */
    private fun updateCache(key: String, rate: BigDecimal) {
        rateCache[key] = rate
        lastCacheUpdate = LocalDateTime.now()
    }

    /**
     * Check if cache is still valid (less than 1 hour old)
     */
    private fun isCacheValid(): Boolean {
        return lastCacheUpdate.isAfter(LocalDateTime.now().minusHours(1))
    }

    /**
     * Clear expired rates from database
     */
    suspend fun cleanupExpiredRates() {
        val expiryTime = LocalDateTime.now().minusDays(7) // Keep rates for 7 days
        exchangeRateDao.deleteExpiredRates(expiryTime)
    }

    /**
     * Get all available currencies with exchange rates
     */
    suspend fun getAvailableCurrencies(): List<String> {
        return exchangeRateDao.getAvailableCurrencies()
    }

    /**
     * Convert multiple amounts to base currency
     */
    suspend fun convertToBaseCurrency(
        transactions: List<TransactionData>,
        baseCurrency: String
    ): Map<String, BigDecimal> {
        val convertedAmounts = mutableMapOf<String, BigDecimal>()

        transactions.forEach { transaction ->
            val convertedAmount = convertAmount(
                amount = transaction.amount,
                fromCurrency = transaction.currency,
                toCurrency = baseCurrency
            )
            convertedAmounts[transaction.id] = convertedAmount
        }

        return convertedAmounts
    }

    /**
     * Set a custom exchange rate for a currency pair.
     * Custom rates are preserved across API refreshes.
     */
    suspend fun setCustomRate(fromCurrency: String, toCurrency: String, rate: BigDecimal) {
        val farFutureExpiry = LocalDateTime.now().plusYears(100)
        val entity = ExchangeRateEntity(
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            rate = rate,
            provider = "Custom",
            updatedAt = LocalDateTime.now(),
            updatedAtUnix = System.currentTimeMillis() / 1000,
            expiresAt = farFutureExpiry,
            expiresAtUnix = farFutureExpiry.atZone(ZoneId.systemDefault()).toEpochSecond(),
            isCustomRate = true
        )

        // Check if existing row exists to preserve its ID for REPLACE
        val existing = exchangeRateDao.getExchangeRateIgnoringExpiry(fromCurrency, toCurrency)
        val entityToInsert = if (existing != null) entity.copy(id = existing.id) else entity
        exchangeRateDao.insertExchangeRate(entityToInsert)

        val cacheKey = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"
        updateCache(cacheKey, rate)
    }

    /**
     * Clear a custom rate, allowing API rates to take over again.
     */
    suspend fun clearCustomRate(fromCurrency: String, toCurrency: String) {
        exchangeRateDao.clearCustomRateFlag(fromCurrency, toCurrency)
        val cacheKey = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"
        rateCache.remove(cacheKey)
    }

    /**
     * Clear all custom rates, resetting everything to API rates.
     */
    suspend fun clearAllCustomRates() {
        exchangeRateDao.clearAllCustomRateFlags()
        rateCache.clear()
        lastCacheUpdate = LocalDateTime.MIN
    }

    /**
     * Get all active exchange rates relevant to the user's currencies.
     */
    suspend fun getActiveRates(userCurrencies: List<String>): List<ExchangeRateEntity> {
        return exchangeRateDao.getRatesForCurrencies(userCurrencies)
    }

    /**
     * Get a Flow of all exchange rates in the database.
     */
    fun getAllRatesFlow(): Flow<List<ExchangeRateEntity>> {
        return exchangeRateDao.getAllRatesFlow()
    }

    data class TransactionData(
        val id: String,
        val amount: BigDecimal,
        val currency: String
    )

    data class RateFreshnessInfo(
        val hasValidUsdRates: Boolean,
        val validUsdRatesCount: Int,
        val latestUpdateTime: LocalDateTime?,
        val latestExpiryTime: LocalDateTime?,
        val isStale: Boolean,
        val currentTime: LocalDateTime
    )
}