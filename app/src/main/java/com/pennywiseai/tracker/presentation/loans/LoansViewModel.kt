package com.pennywiseai.tracker.presentation.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
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
    val isLoading: Boolean = true,
    val showSettledLoans: Boolean = false
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val loanRepository: LoanRepository
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
                loanRepository.getTotalLentRemaining(),
                loanRepository.getTotalBorrowedRemaining()
            ) { active, all, lent, borrowed ->
                LoansUiState(
                    activeLoans = active,
                    settledLoans = all.filter { it.status == LoanStatus.SETTLED },
                    totalLentRemaining = lent,
                    totalBorrowedRemaining = borrowed,
                    isLoading = false,
                    showSettledLoans = _uiState.value.showSettledLoans
                )
            }.collect { _uiState.value = it }
        }
    }

    fun toggleShowSettled() {
        _uiState.value = _uiState.value.copy(showSettledLoans = !_uiState.value.showSettledLoans)
    }
}
