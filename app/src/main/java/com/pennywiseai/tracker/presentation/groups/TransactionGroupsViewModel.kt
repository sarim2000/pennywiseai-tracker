package com.pennywiseai.tracker.presentation.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
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
    val showCreateDialog: Boolean = false,
    // Name of the trip a new trip's dates overlap, shown in the dialog (#1463).
    val tripOverlap: String? = null
)

@HiltViewModel
class TransactionGroupsViewModel @Inject constructor(
    private val repository: TransactionGroupRepository,
    entitlementGate: EntitlementGate
) : ViewModel() {

    val isProEntitled: StateFlow<Boolean> = entitlementGate.isProEntitled

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
    fun hideCreateDialog() { _uiState.value = _uiState.value.copy(showCreateDialog = false, tripOverlap = null) }

    fun createGroup(name: String, note: String?, trip: TripDraft = TripDraft()) {
        if (name.isBlank()) return
        // Starting a trip is Pro; a stale dialog can't get past the switch's own gate.
        if (trip.enabled && !isProEntitled.value) return
        viewModelScope.launch {
            val (start, end) = trip.window
            try {
                repository.createGroup(name, note, start, end)
                _uiState.value = _uiState.value.copy(showCreateDialog = false, tripOverlap = null)
            } catch (e: TransactionGroupRepository.TripOverlapException) {
                _uiState.value = _uiState.value.copy(tripOverlap = e.other)
            }
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
        }
    }
}
