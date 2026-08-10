package com.pennywiseai.tracker.presentation.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.repository.GroupSummary
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
            repository.observeGroupSummaries().collect { summaries ->
                _uiState.value = _uiState.value.copy(groups = summaries, isLoading = false)
            }
        }
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
