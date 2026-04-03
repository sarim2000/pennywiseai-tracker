package com.pennywiseai.tracker.presentation.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.LoanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class LoanDetailUiState(
    val loan: LoanEntity? = null,
    val linkedTransactions: List<TransactionEntity> = emptyList(),
    val recentUnlinkedTransactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val showSettleDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showRecordPaymentSheet: Boolean = false,
    val isDeleted: Boolean = false
)

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val loanId: Long = savedStateHandle.get<Long>("loanId") ?: -1L

    private val _uiState = MutableStateFlow(LoanDetailUiState())
    val uiState: StateFlow<LoanDetailUiState> = _uiState.asStateFlow()

    init {
        loadLoan()
    }

    private fun loadLoan() {
        viewModelScope.launch {
            val loan = loanRepository.getLoanById(loanId)
            _uiState.value = _uiState.value.copy(loan = loan, isLoading = false)
        }
        viewModelScope.launch {
            loanRepository.getTransactionsForLoan(loanId).collect { txns ->
                _uiState.value = _uiState.value.copy(linkedTransactions = txns)
            }
        }
    }

    fun showRecordPayment() {
        val loan = _uiState.value.loan ?: return
        viewModelScope.launch {
            loanRepository.getRecentUnlinkedRepayments(loan.direction).collect {
                _uiState.value = _uiState.value.copy(
                    recentUnlinkedTransactions = it,
                    showRecordPaymentSheet = true
                )
            }
        }
    }

    fun hideRecordPayment() {
        _uiState.value = _uiState.value.copy(showRecordPaymentSheet = false)
    }

    fun linkTransactionAsRepayment(transactionId: Long) {
        viewModelScope.launch {
            loanRepository.recordRepayment(loanId, transactionId)
            _uiState.value = _uiState.value.copy(showRecordPaymentSheet = false)
            refreshLoan()
        }
    }

    fun recordManualRepayment(amount: BigDecimal) {
        val loan = _uiState.value.loan ?: return
        viewModelScope.launch {
            loanRepository.recordManualRepayment(loanId, amount, loan.personName, loan.currency)
            _uiState.value = _uiState.value.copy(showRecordPaymentSheet = false)
            refreshLoan()
        }
    }

    fun showSettleDialog() { _uiState.value = _uiState.value.copy(showSettleDialog = true) }
    fun hideSettleDialog() { _uiState.value = _uiState.value.copy(showSettleDialog = false) }
    fun showDeleteDialog() { _uiState.value = _uiState.value.copy(showDeleteDialog = true) }
    fun hideDeleteDialog() { _uiState.value = _uiState.value.copy(showDeleteDialog = false) }

    fun settleLoan() {
        viewModelScope.launch {
            loanRepository.settleLoan(loanId)
            _uiState.value = _uiState.value.copy(showSettleDialog = false)
            refreshLoan()
        }
    }

    fun reopenLoan() {
        viewModelScope.launch {
            loanRepository.reopenLoan(loanId)
            refreshLoan()
        }
    }

    fun deleteLoan() {
        viewModelScope.launch {
            loanRepository.deleteLoan(loanId)
            _uiState.value = _uiState.value.copy(showDeleteDialog = false, isDeleted = true)
        }
    }

    private suspend fun refreshLoan() {
        val loan = loanRepository.getLoanById(loanId)
        _uiState.value = _uiState.value.copy(loan = loan)
    }
}
