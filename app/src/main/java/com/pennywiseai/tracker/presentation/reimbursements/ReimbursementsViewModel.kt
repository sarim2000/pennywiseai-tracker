package com.pennywiseai.tracker.presentation.reimbursements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.ReimbursementStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.utils.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ReimbursementsUiState(
    val isLoading: Boolean = true,
    val pending: List<TransactionEntity> = emptyList(),
    val reimbursed: List<TransactionEntity> = emptyList(),
    /** What [pending] adds up to, per currency. */
    val pendingTotals: Map<String, Money> = emptyMap(),
)

/** Every reimbursable expense across all time (#1389): what's still owed back first. */
@HiltViewModel
class ReimbursementsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<ReimbursementsUiState> = transactionRepository.observeReimbursables()
        .map { rows ->
            val (pending, reimbursed) = rows.partition { it.reimbursementStatus == ReimbursementStatus.PENDING }
            ReimbursementsUiState(
                isLoading = false,
                pending = pending,
                reimbursed = reimbursed,
                pendingTotals = pendingByCurrency(pending),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReimbursementsUiState())
}
