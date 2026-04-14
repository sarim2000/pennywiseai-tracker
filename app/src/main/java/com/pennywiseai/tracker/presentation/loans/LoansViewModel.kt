package com.pennywiseai.tracker.presentation.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class LoansUiState(
    val activeLoans: List<LoanEntity> = emptyList(),
    val settledLoans: List<LoanEntity> = emptyList(),
    val totalLentRemaining: BigDecimal = BigDecimal.ZERO,
    val totalBorrowedRemaining: BigDecimal = BigDecimal.ZERO,
    val summaryCurrency: String = "INR",
    val isLoading: Boolean = true,
    val showSettledLoans: Boolean = false
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoansUiState())
    val uiState: StateFlow<LoansUiState> = _uiState.asStateFlow()

    init {
        loadLoans()
    }

    private fun loadLoans() {
        viewModelScope.launch {
            combine(
                loanRepository.getActiveLoans(),
                loanRepository.getAllLoans(),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                userPreferencesRepository.baseCurrency
            ) { active, all, isUnified, displayCurrency, baseCurrency ->
                LoanInputs(
                    activeLoans = active,
                    allLoans = all,
                    isUnified = isUnified,
                    displayCurrency = displayCurrency,
                    baseCurrency = baseCurrency
                )
            }.collect { inputs ->
                val summaryCurrency = if (inputs.isUnified) inputs.displayCurrency else inputs.baseCurrency

                val loanCurrencies = inputs.activeLoans.map { it.currency }.distinct()
                if (inputs.isUnified && loanCurrencies.size > 1) {
                    currencyConversionService.refreshExchangeRatesForAccount((loanCurrencies + summaryCurrency).distinct())
                }

                val loansForTotals = if (inputs.isUnified) {
                    inputs.activeLoans
                } else {
                    inputs.activeLoans.filter { it.currency.equals(summaryCurrency, ignoreCase = true) }
                }

                val (lent, borrowed) = if (inputs.isUnified) {
                    computeUnifiedTotals(loansForTotals, summaryCurrency)
                } else {
                    computeNativeTotals(loansForTotals)
                }

                _uiState.value = LoansUiState(
                    activeLoans = inputs.activeLoans,
                    settledLoans = inputs.allLoans.filter { it.status == LoanStatus.SETTLED },
                    totalLentRemaining = lent,
                    totalBorrowedRemaining = borrowed,
                    summaryCurrency = summaryCurrency,
                    isLoading = false,
                    showSettledLoans = _uiState.value.showSettledLoans
                )
            }
        }
    }

    private data class LoanInputs(
        val activeLoans: List<LoanEntity>,
        val allLoans: List<LoanEntity>,
        val isUnified: Boolean,
        val displayCurrency: String,
        val baseCurrency: String
    )

    private suspend fun computeUnifiedTotals(
        loans: List<LoanEntity>,
        targetCurrency: String
    ): Pair<BigDecimal, BigDecimal> {
        var lent = BigDecimal.ZERO
        var borrowed = BigDecimal.ZERO
        for (loan in loans) {
            val converted = currencyConversionService.convertAmount(
                amount = loan.remainingAmount,
                fromCurrency = loan.currency,
                toCurrency = targetCurrency
            )
            when (loan.direction) {
                LoanDirection.LENT -> lent += converted
                LoanDirection.BORROWED -> borrowed += converted
            }
        }
        return lent to borrowed
    }

    private fun computeNativeTotals(loans: List<LoanEntity>): Pair<BigDecimal, BigDecimal> {
        val lent = loans
            .filter { it.direction == LoanDirection.LENT }
            .fold(BigDecimal.ZERO) { acc, l -> acc + l.remainingAmount }
        val borrowed = loans
            .filter { it.direction == LoanDirection.BORROWED }
            .fold(BigDecimal.ZERO) { acc, l -> acc + l.remainingAmount }
        return lent to borrowed
    }

    fun toggleShowSettled() {
        _uiState.value = _uiState.value.copy(showSettledLoans = !_uiState.value.showSettledLoans)
    }
}
