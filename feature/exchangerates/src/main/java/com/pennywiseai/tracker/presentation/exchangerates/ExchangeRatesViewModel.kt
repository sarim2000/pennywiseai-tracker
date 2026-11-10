package com.pennywiseai.tracker.presentation.exchangerates

import com.pennywiseai.feature.exchangerates.R
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

data class ExchangeRatesUiState(
    val rates: List<ExchangeRateSnapshot> = emptyList(),
    val userCurrencies: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val lastUpdated: LocalDateTime? = null,
    @StringRes val error: Int? = null
)

@HiltViewModel
class ExchangeRatesViewModel @Inject constructor(
    private val exchangeRatesData: ExchangeRatesData
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExchangeRatesUiState())
    val uiState: StateFlow<ExchangeRatesUiState> = _uiState.asStateFlow()

    init {
        loadRates()
    }

    fun loadRates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val currencies = exchangeRatesData.getUserCurrencies()
                val rates = exchangeRatesData.getActiveRates(currencies)
                val lastUpdated = rates.map { it.updatedAt }.maxOrNull()

                _uiState.update {
                    it.copy(
                        rates = rates,
                        userCurrencies = currencies,
                        isLoading = false,
                        lastUpdated = lastUpdated
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = R.string.exchange_rates_error_load)
                }
            }
        }
    }

    fun refreshRates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val currencies = _uiState.value.userCurrencies.ifEmpty {
                    exchangeRatesData.getUserCurrencies()
                }
                exchangeRatesData.refreshExchangeRates(currencies)
                val rates = exchangeRatesData.getActiveRates(currencies)
                val lastUpdated = rates.map { it.updatedAt }.maxOrNull()

                _uiState.update {
                    it.copy(
                        rates = rates,
                        userCurrencies = currencies,
                        isRefreshing = false,
                        lastUpdated = lastUpdated
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRefreshing = false, error = R.string.exchange_rates_error_refresh)
                }
            }
        }
    }

    fun setCustomRate(fromCurrency: String, toCurrency: String, rate: BigDecimal) {
        viewModelScope.launch {
            try {
                exchangeRatesData.setCustomRate(fromCurrency, toCurrency, rate)
                loadRates()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = R.string.exchange_rates_error_set_custom) }
            }
        }
    }

    fun clearCustomRate(fromCurrency: String, toCurrency: String) {
        viewModelScope.launch {
            try {
                exchangeRatesData.clearCustomRate(fromCurrency, toCurrency)
                loadRates()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = R.string.exchange_rates_error_clear_custom) }
            }
        }
    }

    fun clearAllCustomRates() {
        viewModelScope.launch {
            try {
                exchangeRatesData.clearAllCustomRates()
                loadRates()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = R.string.exchange_rates_error_clear_all_custom) }
            }
        }
    }
}
