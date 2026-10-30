package com.pennywiseai.tracker.data.currency

import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #881: with the network down or the app's internet blocked, a failed lazy rate
 * fetch must not be retried on every lookup — widgets refresh after every SMS.
 */
class CurrencyConversionServiceOfflineTest {

    @Test
    fun `a failed lazy fetch is not retried during the cooldown`() = runBlocking {
        val dao = mockk<ExchangeRateDao>(relaxed = true)
        coEvery { dao.getExchangeRate(any(), any(), any()) } returns null
        coEvery { dao.getExchangeRateIgnoringExpiry(any(), any()) } returns null
        val provider = mockk<ExchangeRateProvider>()
        coEvery { provider.fetchAllExchangeRatesWithMetadata(any()) } returns null // offline
        val service = CurrencyConversionService(dao, provider, mockk<UserPreferencesRepository>(relaxed = true))

        assertNull(service.getExchangeRate("USD", "INR"))
        assertNull(service.getExchangeRate("EUR", "INR"))
        assertNull(service.getExchangeRate("USD", "INR"))

        coVerify(exactly = 1) { provider.fetchAllExchangeRatesWithMetadata(any()) }
    }

    /**
     * AUD-03: the rate cache used to be a plain mutableMapOf written/read by
     * concurrent coroutines without synchronization — risking
     * ConcurrentModificationException / lost writes. With ConcurrentHashMap +
     * @Volatile timestamp, 100 concurrent converts must all return the same rate
     * with no exception.
     */
    @Test
    fun `concurrent convert calls do not corrupt the rate cache`() = runBlocking {
        val dao = mockk<ExchangeRateDao>(relaxed = true)
        val now = LocalDateTime.now()
        val usdInr = ExchangeRateEntity(
            fromCurrency = "USD", toCurrency = "INR", rate = BigDecimal("83.50"),
            provider = "test", updatedAt = now, expiresAt = now.plusDays(1)
        )
        val eurInr = ExchangeRateEntity(
            fromCurrency = "EUR", toCurrency = "INR", rate = BigDecimal("90.00"),
            provider = "test", updatedAt = now, expiresAt = now.plusDays(1)
        )
        val gbpInr = ExchangeRateEntity(
            fromCurrency = "GBP", toCurrency = "INR", rate = BigDecimal("105.00"),
            provider = "test", updatedAt = now, expiresAt = now.plusDays(1)
        )
        val pairs = mapOf("USD" to usdInr, "EUR" to eurInr, "GBP" to gbpInr)
        val latch = CountDownLatch(1)
        coEvery { dao.getExchangeRate(any(), any(), any()) } answers { latch.await(); pairs[firstArg<String>()]!! }
        coEvery { dao.getExchangeRateIgnoringExpiry(any(), any()) } returns null
        val provider = mockk<ExchangeRateProvider>()
        coEvery { provider.fetchAllExchangeRatesWithMetadata(any()) } returns null // no network needed
        val service = CurrencyConversionService(dao, provider, mockk<UserPreferencesRepository>(relaxed = true))

        val jobs = listOf("USD", "EUR", "GBP").flatMap { pair ->
            (1..33).map { async(Dispatchers.Default) { service.getExchangeRate(pair, "INR") } }
        }
        latch.countDown() // release all callers at once so they write to the cache concurrently
        val results = jobs.awaitAll()

        assertEquals(99, results.size)
        results.forEachIndexed { i, r ->
            val expectedPair = pairs.keys.elementAt(i / 33)
            assertEquals(pairs[expectedPair]?.rate, r)
        }

        // Every concurrent write must have left a usable cache entry: with the DB
        // now failing, all three pairs still resolve from the cache alone.
        coEvery { dao.getExchangeRate(any(), any(), any()) } throws IllegalStateException("cache miss")
        pairs.forEach { (from, entity) -> assertEquals(entity.rate, service.getExchangeRate(from, "INR")) }
    }
}
