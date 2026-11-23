package com.pennywiseai.tracker.presentation.reimbursements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.ReimbursementStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reimbursement on the transaction screen (#1389): mark an expense reimbursable,
 * then reimbursed by the incoming payment that paid it back. On that payment, the
 * expenses it paid back. Changes only the two reimbursement columns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionReimbursementViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val transaction = MutableStateFlow<TransactionEntity?>(null)

    fun setTransaction(txn: TransactionEntity?) { transaction.value = txn }

    /**
     * The payment that paid the open expense back, while it's linked and not deleted.
     * Observed, so an edit or delete of that payment shows here at once.
     */
    val payment: StateFlow<TransactionEntity?> = transaction
        .map { it?.reimbursedById }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else transactionRepository.observeReimbursementPayment(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The expenses the open transaction paid back, when it's money received. */
    val paidBack: StateFlow<List<TransactionEntity>> = transaction
        .map { txn -> txn?.takeIf { it.transactionType == TransactionType.INCOME }?.id }
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else transactionRepository.observeReimbursedBy(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The "What paid it back?" sheet's choices; null while loading. */
    private val _candidates = MutableStateFlow<List<TransactionEntity>?>(null)
    val candidates: StateFlow<List<TransactionEntity>?> = _candidates.asStateFlow()
    private var candidatesJob: Job? = null

    /** Fires after each change, so the screen reloads the transaction. */
    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    fun loadCandidates() {
        val txn = transaction.value ?: return
        candidatesJob?.cancel()
        _candidates.value = null
        candidatesJob = viewModelScope.launch {
            _candidates.value = runCatching { transactionRepository.getReimbursementCandidates(txn) }.getOrDefault(emptyList())
        }
    }

    fun markPending() = set(ReimbursementStatus.PENDING)

    /** Reimbursed by [paymentId], or with null by a payment PennyWise didn't record. */
    fun markReimbursed(paymentId: Long?) = set(ReimbursementStatus.REIMBURSED, paymentId)

    fun clear() = set(null)

    private fun set(status: ReimbursementStatus?, paymentId: Long? = null) {
        val id = transaction.value?.id ?: return
        viewModelScope.launch {
            transactionRepository.setReimbursement(id, status, paymentId)
            _changed.tryEmit(Unit)
        }
    }
}
