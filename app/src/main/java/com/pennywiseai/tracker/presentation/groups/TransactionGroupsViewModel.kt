package com.pennywiseai.tracker.presentation.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class GroupSummary(
    val group: TransactionGroupEntity,
    val transactionCount: Int,
    // Totals are kept per-currency because a group can mix currencies and summing
    // across them is meaningless. Keyed by currency code.
    val expenseByCurrency: Map<String, BigDecimal>,
    val incomeByCurrency: Map<String, BigDecimal>
) {
    val hasExpense: Boolean get() = expenseByCurrency.values.any { it.signum() > 0 }
    val hasIncome: Boolean get() = incomeByCurrency.values.any { it.signum() > 0 }
}

data class TransactionGroupsUiState(
    val groups: List<GroupSummary> = emptyList(),
    val isLoading: Boolean = true,
    val showCreateDialog: Boolean = false
)

@HiltViewModel
class TransactionGroupsViewModel @Inject constructor(
    private val repository: TransactionGroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionGroupsUiState())
    val uiState: StateFlow<TransactionGroupsUiState> = _uiState.asStateFlow()

    init {
        loadGroups()
    }

    private fun loadGroups() {
        viewModelScope.launch {
            repository.getAllGroups()
                .flatMapLatest { groups ->
                    if (groups.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            groups.map { group ->
                                repository.getTransactionsForGroup(group.id)
                                    .map { txns -> buildSummary(group, txns) }
                            }
                        ) { it.toList() }
                    }
                }
                .collect { summaries ->
                    _uiState.value = _uiState.value.copy(groups = summaries, isLoading = false)
                }
        }
    }

    private fun buildSummary(group: TransactionGroupEntity, transactions: List<TransactionEntity>): GroupSummary {
        val expense = transactions
            .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
            .sumByCurrency()
        val income = transactions
            .filter { it.transactionType == TransactionType.INCOME }
            .sumByCurrency()
        return GroupSummary(group, transactions.size, expense, income)
    }

    private fun List<TransactionEntity>.sumByCurrency(): Map<String, BigDecimal> =
        groupBy { it.currency }
            .mapValues { (_, txns) -> txns.fold(BigDecimal.ZERO) { acc, t -> acc + t.amount } }

    fun showCreateDialog() { _uiState.value = _uiState.value.copy(showCreateDialog = true) }
    fun hideCreateDialog() { _uiState.value = _uiState.value.copy(showCreateDialog = false) }

    fun createGroup(name: String, note: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createGroup(name, note)
            _uiState.value = _uiState.value.copy(showCreateDialog = false)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
        }
    }
}
