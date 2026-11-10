package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.presentation.exchangerates.ExchangeRateSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class AppExchangeRatesDataTest {
    private val service = mockk<CurrencyConversionService>(relaxed = true)
    private val transactions = mockk<TransactionRepository>()
    private val data = AppExchangeRatesData(service, transactions)

    private val updated = LocalDateTime.of(2026, 3, 1, 10, 30)

    @Test
    fun `projection keeps every field exactly, including BigDecimal scale`() = runTest {
        val rate = BigDecimal("0.0120000000") // scale 10; equals() on BigDecimal is scale-sensitive
        coEvery { service.getActiveRates(listOf("INR", "USD")) } returns listOf(
            ExchangeRateEntity(
                id = 7, fromCurrency = "INR", toCurrency = "USD", rate = rate,
                provider = "api", updatedAt = updated, updatedAtUnix = 1,
                expiresAt = updated.plusDays(1), expiresAtUnix = 2, isCustomRate = true
            )
        )

        val result = data.getActiveRates(listOf("INR", "USD"))

        assertEquals(listOf(ExchangeRateSnapshot("INR", "USD", rate, updated, true)), result)
        assertEquals(10, result.single().rate.scale())
    }

    @Test
    fun `user currencies come from the transaction repository`() = runTest {
        every { transactions.getAllCurrencies() } returns flowOf(listOf("AED", "INR"))
        assertEquals(listOf("AED", "INR"), data.getUserCurrencies())
    }

    @Test
    fun `writes delegate to the conversion service`() = runTest {
        val rate = BigDecimal("83.250000")
        data.refreshExchangeRates(listOf("INR"))
        data.setCustomRate("USD", "INR", rate)
        data.clearCustomRate("USD", "INR")
        data.clearAllCustomRates()

        coVerify(exactly = 1) { service.refreshExchangeRates(listOf("INR")) }
        coVerify(exactly = 1) { service.setCustomRate("USD", "INR", rate) }
        coVerify(exactly = 1) { service.clearCustomRate("USD", "INR") }
        coVerify(exactly = 1) { service.clearAllCustomRates() }
    }

    @Test
    fun `exceptions propagate`() {
        coEvery { service.refreshExchangeRates(any()) } throws IllegalStateException("offline")
        coEvery { service.getActiveRates(any()) } throws IllegalStateException("db")
        assertThrows(IllegalStateException::class.java) { runTest { data.refreshExchangeRates(listOf("INR")) } }
        assertThrows(IllegalStateException::class.java) { runTest { data.getActiveRates(listOf("INR")) } }
    }
}
