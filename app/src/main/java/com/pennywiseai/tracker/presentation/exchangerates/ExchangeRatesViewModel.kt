package com.pennywiseai.tracker.presentation.exchangerates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.ExchangeRateEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.inject.Inject

data class ExchangeRatesUiState(
    val rates: List<ExchangeRateEntity> = emptyList(),
    val userCurrencies: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val lastUpdated: LocalDateTime? = null,
    val error: String? = null
)

@HiltViewModel
class ExchangeRatesViewModel @Inject constructor(
    private val currencyConversionService: CurrencyConversionService,
    private val transactionRepository: TransactionRepository
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
                val currencies = transactionRepository.getAllCurrencies().first()
                val rates = currencyConversionService.getActiveRates(currencies)
                val lastUpdated = rates.maxOfOrNull { it.updatedAt }

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
                    it.copy(isLoading = false, error = "Failed to load rates")
                }
            }
        }
    }

    fun refreshRates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val currencies = _uiState.value.userCurrencies.ifEmpty {
                    transactionRepository.getAllCurrencies().first()
                }
                currencyConversionService.refreshExchangeRates(currencies)
                val rates = currencyConversionService.getActiveRates(currencies)
                val lastUpdated = rates.maxOfOrNull { it.updatedAt }

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
                    it.copy(isRefreshing = false, error = "Failed to refresh rates")
                }
            }
        }
    }

    fun setCustomRate(fromCurrency: String, toCurrency: String, rate: BigDecimal) {
        viewModelScope.launch {
            try {
                currencyConversionService.setCustomRate(fromCurrency, toCurrency, rate)
                loadRates()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to set custom rate") }
            }
        }
    }

    fun clearCustomRate(fromCurrency: String, toCurrency: String) {
        viewModelScope.launch {
            try {
                currencyConversionService.clearCustomRate(fromCurrency, toCurrency)
                loadRates()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to clear custom rate") }
            }
        }
    }

    fun clearAllCustomRates() {
        viewModelScope.launch {
            try {
                currencyConversionService.clearAllCustomRates()
                loadRates()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to clear custom rates") }
            }
        }
    }
}
