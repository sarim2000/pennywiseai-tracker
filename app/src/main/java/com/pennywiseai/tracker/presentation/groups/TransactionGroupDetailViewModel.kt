package com.pennywiseai.tracker.presentation.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class TransactionGroupDetailUiState(
    val group: TransactionGroupEntity? = null,
    val linkedTransactions: List<TransactionEntity> = emptyList(),
    val totalExpense: BigDecimal = BigDecimal.ZERO,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val showAddSheet: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val isDeleted: Boolean = false,
    val ungroupedTransactions: List<TransactionEntity> = emptyList(),
    val addSearchQuery: String = ""
)

@OptIn(FlowPreview::class)
@HiltViewModel
class TransactionGroupDetailViewModel @Inject constructor(
    private val repository: TransactionGroupRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: Long = savedStateHandle.get<Long>("groupId") ?: -1L

    private val _uiState = MutableStateFlow(TransactionGroupDetailUiState())
    val uiState: StateFlow<TransactionGroupDetailUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadGroup()
        observeSearch()
    }

    private fun loadGroup() {
        viewModelScope.launch {
            val group = repository.getGroupById(groupId)
            _uiState.value = _uiState.value.copy(group = group, isLoading = false)
        }
        viewModelScope.launch {
            repository.getTransactionsForGroup(groupId).collect { txns ->
                val expense = txns
                    .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
                    .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
                val income = txns
                    .filter { it.transactionType == TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, t -> acc + t.amount }
                _uiState.value = _uiState.value.copy(
                    linkedTransactions = txns,
                    totalExpense = expense,
                    totalIncome = income
                )
            }
        }
    }

    private fun observeSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        repository.getRecentUngroupedTransactions().collect { txns ->
                            _uiState.value = _uiState.value.copy(ungroupedTransactions = txns)
                        }
                    } else {
                        repository.searchUngroupedTransactions(query).collect { txns ->
                            _uiState.value = _uiState.value.copy(ungroupedTransactions = txns)
                        }
                    }
                }
        }
    }

    fun showAddSheet() {
        viewModelScope.launch {
            repository.getRecentUngroupedTransactions().collect { txns ->
                _uiState.value = _uiState.value.copy(ungroupedTransactions = txns, showAddSheet = true)
            }
        }
    }

    fun hideAddSheet() {
        _uiState.value = _uiState.value.copy(showAddSheet = false, addSearchQuery = "", ungroupedTransactions = emptyList())
        _searchQuery.value = ""
    }

    fun updateAddSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(addSearchQuery = query)
        _searchQuery.value = query
    }

    fun addTransaction(transactionId: Long) {
        viewModelScope.launch {
            repository.addTransactionToGroup(transactionId, groupId)
        }
    }

    fun removeTransaction(transactionId: Long) {
        viewModelScope.launch {
            repository.removeTransactionFromGroup(transactionId)
        }
    }

    fun showDeleteDialog() { _uiState.value = _uiState.value.copy(showDeleteDialog = true) }
    fun hideDeleteDialog() { _uiState.value = _uiState.value.copy(showDeleteDialog = false) }
    fun showEditDialog() { _uiState.value = _uiState.value.copy(showEditDialog = true) }
    fun hideEditDialog() { _uiState.value = _uiState.value.copy(showEditDialog = false) }

    fun updateGroupName(name: String, note: String?) {
        val group = _uiState.value.group ?: return
        viewModelScope.launch {
            repository.updateGroup(group.copy(name = name.trim(), note = note?.trim()?.takeIf { it.isNotEmpty() }))
            _uiState.value = _uiState.value.copy(group = repository.getGroupById(groupId), showEditDialog = false)
        }
    }

    fun deleteGroup() {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
            _uiState.value = _uiState.value.copy(isDeleted = true, showDeleteDialog = false)
        }
    }
}
