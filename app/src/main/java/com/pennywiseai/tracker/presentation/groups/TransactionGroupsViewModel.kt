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
    val totalExpense: BigDecimal,
    val totalIncome: BigDecimal
) {
    val net: BigDecimal get() = totalIncome - totalExpense
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
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
        val income = transactions
            .filter { it.transactionType == TransactionType.INCOME }
            .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
        return GroupSummary(group, transactions.size, expense, income)
    }

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
