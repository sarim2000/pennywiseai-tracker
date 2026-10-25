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
        val rate = BigDecimal("83.50")
        val now = LocalDateTime.now()
        val entity = ExchangeRateEntity(
            fromCurrency = "USD", toCurrency = "INR", rate = rate,
            provider = "test", updatedAt = now,
            expiresAt = now.plusDays(1)
        )
        coEvery { dao.getExchangeRate("USD", "INR", any()) } returns entity
        coEvery { dao.getExchangeRateIgnoringExpiry(any(), any()) } returns null
        val provider = mockk<ExchangeRateProvider>()
        coEvery { provider.fetchAllExchangeRatesWithMetadata(any()) } returns null // no network needed
        val service = CurrencyConversionService(dao, provider, mockk<UserPreferencesRepository>(relaxed = true))

        val results = withContext(Dispatchers.Default) {
            (1..100).map { async { service.getExchangeRate("USD", "INR") } }.awaitAll()
        }

        assertEquals(100, results.size)
        assertTrue(results.all { r -> r == rate })
    }
}
