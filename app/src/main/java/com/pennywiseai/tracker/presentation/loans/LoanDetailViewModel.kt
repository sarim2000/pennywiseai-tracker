package com.pennywiseai.tracker.presentation.loans

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.events.MascotMoments
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.domain.loan.LoanOverflow
import com.pennywiseai.tracker.domain.loan.LoanOverflowPrompt
import com.pennywiseai.tracker.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val showEditAmountDialog: Boolean = false,
    val showRecordPaymentSheet: Boolean = false,
    /** A payment beyond what's owed, waiting for the user to say what the extra is. */
    val overflowPrompt: LoanOverflowPrompt? = null,
    val isDeleted: Boolean = false
)

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    @ApplicationContext private val context: Context,
    private val mascotMoments: MascotMoments,
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
            // Opened straight from a transaction, Loans may not have run this yet.
            loanRepository.refreshActiveLoans()
            val loan = loanRepository.getLoanById(loanId)
            _uiState.value = _uiState.value.copy(loan = loan, isLoading = false)
        }
        viewModelScope.launch {
            loanRepository.getTransactionsForLoan(loanId).collect { txns ->
                // Re-read the loan too: unlinking a row elsewhere (e.g. undoing a turn-around
                // from the transaction screen) can change its direction and balance.
                val loan = loanRepository.getLoanById(loanId)
                _uiState.value = _uiState.value.copy(linkedTransactions = txns, loan = loan ?: _uiState.value.loan)
            }
        }
    }

    fun showRecordPayment() {
        viewModelScope.launch {
            // Read once per opening, for the loan as it is now: a payment that turned
            // the loan around changes which side repays it, and a live search left
            // running would reopen the sheet with the old side's candidates.
            val loan = loanRepository.getLoanById(loanId) ?: return@launch
            val candidates = loanRepository.getRecentUnlinkedRepayments(loan.direction, loan.currency).first()
            _uiState.value = _uiState.value.copy(
                loan = loan,
                recentUnlinkedTransactions = candidates,
                showRecordPaymentSheet = true
            )
        }
    }

    fun hideRecordPayment() {
        _uiState.value = _uiState.value.copy(showRecordPaymentSheet = false)
    }

    /** A payment held back while [LoanDetailUiState.overflowPrompt] asks about its extra. */
    private sealed interface PendingPayment {
        data class Linked(val transactionId: Long) : PendingPayment
        data class Manual(val amount: BigDecimal) : PendingPayment
    }

    private var pendingPayment: PendingPayment? = null

    fun linkTransactionAsRepayment(transactionId: Long) {
        viewModelScope.launch {
            askOrRecord(PendingPayment.Linked(transactionId), loanRepository.flippableOverflow(loanId, transactionId, null))
        }
    }

    fun recordManualRepayment(amount: BigDecimal) {
        viewModelScope.launch {
            askOrRecord(PendingPayment.Manual(amount), loanRepository.flippableOverflow(loanId, null, amount))
        }
    }

    /** Records the waiting payment with the user's answer for its extra. */
    fun resolveOverflow(overflow: LoanOverflow) {
        val payment = pendingPayment ?: return
        pendingPayment = null
        _uiState.value = _uiState.value.copy(overflowPrompt = null)
        viewModelScope.launch { record(payment, overflow) }
    }

    /** Drops the waiting payment: nothing is recorded. */
    fun dismissOverflow() {
        pendingPayment = null
        _uiState.value = _uiState.value.copy(overflowPrompt = null)
    }

    // Paying more than is owed first asks what the extra is, when the loan can turn around.
    private suspend fun askOrRecord(payment: PendingPayment, extra: BigDecimal?) {
        val loan = _uiState.value.loan
        _uiState.value = _uiState.value.copy(showRecordPaymentSheet = false)
        if (extra != null && loan != null) {
            pendingPayment = payment
            _uiState.value = _uiState.value.copy(
                overflowPrompt = LoanOverflowPrompt(loan.personName, loan.direction, extra, loan.currency)
            )
            return
        }
        record(payment, LoanOverflow.COUNT_AS_OWN)
    }

    private suspend fun record(payment: PendingPayment, overflow: LoanOverflow) {
        val loan = _uiState.value.loan ?: return
        // Whether it paid the loan off is decided under the repository's repayment lock.
        val paidOff = when (payment) {
            // Refused for a split bill (#346); the picker already hides those.
            is PendingPayment.Linked -> runCatching {
                loanRepository.recordRepayment(loanId, payment.transactionId, overflow = overflow)
            }.getOrDefault(false)
            is PendingPayment.Manual ->
                loanRepository.recordManualRepayment(loanId, payment.amount, loan.personName, loan.currency, overflow)?.paidOff == true
        }
        // Before the refresh: leaving the screen cancels it, and the payment is already saved.
        if (paidOff) mascotMoments.announce(MascotMoment.DebtPaidOff)
        refreshLoan()
    }

    fun showSettleDialog() { _uiState.value = _uiState.value.copy(showSettleDialog = true) }
    fun hideSettleDialog() { _uiState.value = _uiState.value.copy(showSettleDialog = false) }
    fun showDeleteDialog() { _uiState.value = _uiState.value.copy(showDeleteDialog = true) }
    fun hideDeleteDialog() { _uiState.value = _uiState.value.copy(showDeleteDialog = false) }
    fun showEditAmountDialog() { _uiState.value = _uiState.value.copy(showEditAmountDialog = true) }
    fun hideEditAmountDialog() { _uiState.value = _uiState.value.copy(showEditAmountDialog = false) }

    fun updateLoanAmount(newAmount: BigDecimal) {
        viewModelScope.launch {
            loanRepository.updateOriginalAmount(loanId, newAmount)
            _uiState.value = _uiState.value.copy(showEditAmountDialog = false)
            refreshLoan()
        }
    }

    // Settling forgives what's left without a payment (#938 counts it as spent), so the
    // Chat tab doesn't celebrate it: only a repayment that clears the debt is paid off.
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
            WidgetRefresher.refreshTransactionWidgets(context)
            _uiState.value = _uiState.value.copy(showDeleteDialog = false, isDeleted = true)
        }
    }

    /** After every loan change: a forgiven or repaid balance moves the widgets' "Spent" (#938). */
    private suspend fun refreshLoan() {
        WidgetRefresher.refreshTransactionWidgets(context)
        val loan = loanRepository.getLoanById(loanId)
        _uiState.value = _uiState.value.copy(loan = loan)
    }
}
