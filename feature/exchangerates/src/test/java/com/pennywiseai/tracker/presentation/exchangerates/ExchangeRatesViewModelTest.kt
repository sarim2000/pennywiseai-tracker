package com.pennywiseai.tracker.presentation.exchangerates

import com.pennywiseai.feature.exchangerates.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeRatesViewModelTest {
    private val older = LocalDateTime.of(2026, 1, 1, 9, 0)
    private val newer = LocalDateTime.of(2026, 1, 2, 9, 0)

    private class FakeData : ExchangeRatesData {
        val calls = mutableListOf<String>()
        var currencies = listOf("INR", "USD")
        var rates = listOf<ExchangeRateSnapshot>()
        var fail: String? = null

        private fun call(name: String) {
            calls += name
            if (fail == name) throw IllegalStateException(name)
        }

        override suspend fun getUserCurrencies() = currencies.also { call("getUserCurrencies") }
        override suspend fun getActiveRates(currencies: List<String>) = rates.also { call("getActiveRates$currencies") }
        override suspend fun refreshExchangeRates(currencies: List<String>) = call("refresh$currencies")
        override suspend fun setCustomRate(fromCurrency: String, toCurrency: String, rate: BigDecimal) =
            call("set $fromCurrency $toCurrency $rate")
        override suspend fun clearCustomRate(fromCurrency: String, toCurrency: String) =
            call("clear $fromCurrency $toCurrency")
        override suspend fun clearAllCustomRates() = call("clearAll")
    }

    private val data = FakeData().apply {
        rates = listOf(
            ExchangeRateSnapshot("INR", "USD", BigDecimal("0.012"), older, false),
            ExchangeRateSnapshot("USD", "INR", BigDecimal("83.25"), newer, true)
        )
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `init loads currencies then rates, with the newest timestamp`() {
        val state = ExchangeRatesViewModel(data).uiState.value

        assertEquals(listOf("getUserCurrencies", "getActiveRates[INR, USD]"), data.calls)
        assertEquals(data.rates, state.rates)
        assertEquals(listOf("INR", "USD"), state.userCurrencies)
        assertEquals(newer, state.lastUpdated)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `load failure sets the load error`() {
        data.fail = "getUserCurrencies"
        val state = ExchangeRatesViewModel(data).uiState.value
        assertFalse(state.isLoading)
        assertEquals(R.string.exchange_rates_error_load, state.error)
    }

    @Test
    fun `refresh reuses loaded currencies and refetches rates`() {
        val vm = ExchangeRatesViewModel(data)
        data.calls.clear()
        vm.refreshRates()
        assertEquals(listOf("refresh[INR, USD]", "getActiveRates[INR, USD]"), data.calls)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh failure keeps old rates and sets the refresh error`() {
        val vm = ExchangeRatesViewModel(data)
        data.fail = "refresh[INR, USD]"
        vm.refreshRates()
        val state = vm.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals(R.string.exchange_rates_error_refresh, state.error)
        assertEquals(data.rates, state.rates)
    }

    @Test
    fun `set custom rate writes then reloads`() {
        val vm = ExchangeRatesViewModel(data)
        data.calls.clear()
        vm.setCustomRate("USD", "INR", BigDecimal("84.000000"))
        assertEquals(listOf("set USD INR 84.000000", "getUserCurrencies", "getActiveRates[INR, USD]"), data.calls)
    }

    @Test
    fun `set custom rate failure sets its error and skips reload`() {
        val vm = ExchangeRatesViewModel(data)
        data.calls.clear()
        data.fail = "set USD INR 84"
        vm.setCustomRate("USD", "INR", BigDecimal("84"))
        assertEquals(listOf("set USD INR 84"), data.calls)
        assertEquals(R.string.exchange_rates_error_set_custom, vm.uiState.value.error)
    }

    @Test
    fun `clear custom rate writes then reloads, failure sets its error`() {
        val vm = ExchangeRatesViewModel(data)
        data.calls.clear()
        vm.clearCustomRate("USD", "INR")
        assertEquals(listOf("clear USD INR", "getUserCurrencies", "getActiveRates[INR, USD]"), data.calls)

        data.fail = "clear USD INR"
        vm.clearCustomRate("USD", "INR")
        assertEquals(R.string.exchange_rates_error_clear_custom, vm.uiState.value.error)
    }

    @Test
    fun `clear all writes then reloads, failure sets its error`() {
        val vm = ExchangeRatesViewModel(data)
        data.calls.clear()
        vm.clearAllCustomRates()
        assertEquals(listOf("clearAll", "getUserCurrencies", "getActiveRates[INR, USD]"), data.calls)

        data.fail = "clearAll"
        vm.clearAllCustomRates()
        assertEquals(R.string.exchange_rates_error_clear_all_custom, vm.uiState.value.error)
    }
}
