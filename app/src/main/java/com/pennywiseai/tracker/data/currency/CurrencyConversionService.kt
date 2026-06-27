package com.pennywiseai.tracker.data.currency

import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.core.TimeConstants
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Serializes network fetches so the cold-start thundering herd (many collectors
    // converting at once) collapses to one API call: the first caller fetches and
    // stores, the rest re-check under the lock and find the rate already present.
    private val fetchMutex = Mutex()

    // Upper-cased currency codes the provider's latest response did not include.
    // Their pairs can never be stored, so they're excluded from the freshness gate
    // to avoid re-triggering a full refresh forever. In-memory only — cleared on
    // process restart so a transient gap doesn't sideline a code permanently.
    // Thread-safe: read on the fast path outside fetchMutex, written under it.
    private val unsupportedCurrencies: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    // Cooldown to avoid hammering the API when it keeps failing
    private var lastFailedRefreshTime: Long = 0L
    private val failedRefreshCooldownMs = TimeConstants.MILLIS_PER_5_MINUTES

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
        // Pair-aware freshness: refresh when ANY pair we'd store for this currency
        // set is missing or expired — not just when USD globally has some fresh rate.
        // The old global check skipped the whole refresh whenever any USD rate was
        // fresh, so a newly-added currency (e.g. an MZN account when USD->INR was
        // already cached) never got its pairs bulk-fetched, forcing N per-pair
        // lazy fetches downstream.
        if (hasAllRequiredPairsFresh(currencies)) {
            println("All required currency pairs are fresh, skipping refresh")
            return
        }

        // Serialize the actual fetch so a cold-start herd collapses to one call.
        fetchMutex.withLock {
            // Re-check under the lock: a concurrent refresh may have filled these
            // pairs while we were queued, in which case there's nothing left to do.
            if (hasAllRequiredPairsFresh(currencies)) {
                return@withLock
            }
            fetchAndStoreRates(currencies)
        }
    }

    private suspend fun fetchAndStoreRates(currencies: List<String>) {
        // Use USD as the base currency for the API since it's most commonly supported
        val apiBaseCurrency = "USD"

        // Cooldown: if the last refresh failed recently, skip to avoid hammering the API
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastFailedRefreshTime < failedRefreshCooldownMs) {
            println("Currency rates refresh skipped due to failed-refresh cooldown (${(nowMs - lastFailedRefreshTime) / 1000}s ago)")
            return
        }

        println("Currency rates are stale, refreshing from API")

        // Fetch all exchange rates for the base currency at once with metadata
        val response = exchangeRateProvider.fetchAllExchangeRatesWithMetadata(apiBaseCurrency)

        if (response != null) {
            val allRates = response.rates
            // Reset failed cooldown on success
            lastFailedRefreshTime = 0L

            // Record any requested code the provider didn't return (USD is the base,
            // always supported). Keeps the freshness gate from looping on a code
            // whose pairs can never be stored; refreshing this set keeps a code that
            // comes back later from staying excluded.
            currencies.map { it.uppercase() }.distinct().forEach { code ->
                if (code != apiBaseCurrency && !allRates.containsKey(code)) {
                    unsupportedCurrencies.add(code)
                } else {
                    unsupportedCurrencies.remove(code)
                }
            }
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
        } else {
            println("Failed to refresh exchange rates from API (response was null), cooldown ${failedRefreshCooldownMs / 1000}s")
            lastFailedRefreshTime = System.currentTimeMillis()
        }
    }

    /**
     * Get the base currency for the app
     */
    private suspend fun getBaseCurrency(): String {
        return userPreferencesRepository.baseCurrency.first() ?: "INR"
    }

    /**
     * Non-fetching lookup: the rate from the in-memory cache (if valid) or a fresh
     * (non-expired) DB row. Returns null without touching the network.
     */
    private suspend fun lookupFreshRate(fromCurrency: String, toCurrency: String): BigDecimal? {
        val cacheKey = "${fromCurrency.uppercase()}_${toCurrency.uppercase()}"
        if (isCacheValid()) rateCache[cacheKey]?.let { return it }
        return exchangeRateDao.getExchangeRate(fromCurrency, toCurrency, LocalDateTime.now())?.rate
    }

    /**
     * Fetch exchange rate from API and cache it
     */
    private suspend fun fetchAndCacheRate(fromCurrency: String, toCurrency: String): BigDecimal? {
        // Coalesce concurrent fetchers: re-check under the lock first — a fetch that
        // ran while we were queued may have already stored this pair, so we skip the
        // network entirely instead of each caller re-fetching the same table.
        return fetchMutex.withLock {
            lookupFreshRate(fromCurrency, toCurrency)?.let {
                updateCache("${fromCurrency.uppercase()}_${toCurrency.uppercase()}", it)
                return@withLock it
            }
            fetchAndCacheRateLocked(fromCurrency, toCurrency)
        }
    }

    private suspend fun fetchAndCacheRateLocked(fromCurrency: String, toCurrency: String): BigDecimal? {
        println("Lazy per-pair rate fetch for $fromCurrency->$toCurrency (cache + DB miss)")
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
    /**
     * True only when every cross pair we'd store for [currencies] already has a
     * fresh (non-expired or cached) rate. Unlike [shouldRefreshRates]'s single
     * global "are USD rates fresh?" check, this detects a newly-added currency
     * whose pairs were never fetched, so adding e.g. an MZN account triggers one
     * bulk refresh that fills all its pairs instead of N lazy per-pair fetches.
     *
     * Codes are upper-cased (so "inr" matches a stored "INR" row instead of
     * looking missing) and currencies the provider doesn't return are excluded —
     * otherwise an unsupported/mis-cased code could never satisfy the gate and
     * would re-trigger a full refresh on every call without making progress.
     */
    private suspend fun hasAllRequiredPairsFresh(currencies: List<String>): Boolean {
        val distinct = currencies.map { it.uppercase() }.distinct()
            .filter { it !in unsupportedCurrencies }
        for (from in distinct) {
            for (to in distinct) {
                if (from != to && !hasValidRate(from, to)) {
                    return false
                }
            }
        }
        return true
    }

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
            latestExpiryTime = usdRates.map { it.expiresAt }.maxOrNull(),
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