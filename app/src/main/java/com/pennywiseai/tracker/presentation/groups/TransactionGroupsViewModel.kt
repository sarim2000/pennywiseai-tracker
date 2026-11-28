package com.pennywiseai.tracker.presentation.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.repository.GroupSummary
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.data.trip.TripOverlapException
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
    val tripOverlap: String? = null,
    val isSaving: Boolean = false,
    // Existing rows the just-created trip could take; shown once, then dropped.
    val backfill: TripBackfillOffer? = null
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
    fun clearTripOverlap() { _uiState.value = _uiState.value.copy(tripOverlap = null) }

    fun hideCreateDialog() { _uiState.value = _uiState.value.copy(showCreateDialog = false, tripOverlap = null) }

    fun createGroup(name: String, note: String?, trip: TripDraft = TripDraft()) {
        if (name.isBlank()) return
        // Starting a trip is Pro; a stale dialog can't get past the switch's own gate.
        if (trip.enabled && !isProEntitled.value) return
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            val (start, end) = trip.window
            try {
                val id = repository.createGroup(name, note, start, end)
                val rows = if (start != null) repository.tripBackfillCandidates(id) else emptyList()
                _uiState.value = _uiState.value.copy(
                    showCreateDialog = false,
                    tripOverlap = null,
                    backfill = rows.takeIf { it.isNotEmpty() }?.let { TripBackfillOffer(id, it) }
                )
            } catch (e: TripOverlapException) {
                _uiState.value = _uiState.value.copy(tripOverlap = e.other)
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun skipBackfill() { _uiState.value = _uiState.value.copy(backfill = null) }

    /** One database transaction for the whole batch; skipping records nothing. */
    fun addBackfill(ids: List<Long>) {
        val offer = _uiState.value.backfill ?: return
        _uiState.value = _uiState.value.copy(backfill = null)
        viewModelScope.launch { repository.addTransactionsToGroup(ids, offer.groupId) }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
        }
    }
}
