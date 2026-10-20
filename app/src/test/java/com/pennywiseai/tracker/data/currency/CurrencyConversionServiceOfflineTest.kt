package com.pennywiseai.tracker.data.currency

import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
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
}
