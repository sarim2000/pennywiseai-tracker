package com.pennywiseai.tracker.presentation.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.canMarkReimbursable
import com.pennywiseai.tracker.data.repository.AccountShare
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.data.repository.accountSharesOf
import com.pennywiseai.tracker.data.repository.groupTotalsOf
import com.pennywiseai.tracker.data.trip.TripOverlapException
import com.pennywiseai.tracker.data.trip.isTrip
import com.pennywiseai.tracker.data.trip.tripEditNeedsPro
import com.pennywiseai.tracker.data.trip.tripStartMovedEarlier
import com.pennywiseai.tracker.utils.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionGroupDetailUiState(
    val group: TransactionGroupEntity? = null,
    val linkedTransactions: List<TransactionEntity> = emptyList(),
    // Per-currency totals — a group can mix currencies, which can't be summed
    // into one figure. Keyed by currency code.
    val expenseByCurrency: Map<String, Money> = emptyMap(),
    val investedByCurrency: Map<String, Money> = emptyMap(),
    val incomeByCurrency: Map<String, Money> = emptyMap(),
    val accountShares: List<AccountShare> = emptyList(),
    val isLoading: Boolean = true,
    val showAddSheet: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val isDeleted: Boolean = false,
    val ungroupedTransactions: List<TransactionEntity> = emptyList(),
    val addSearchQuery: String = "",
    val tripOverlap: String? = null,
    val isSaving: Boolean = false,
    val upgradeRequested: Boolean = false,
    val backfill: TripBackfillOffer? = null,
    // Non-null while "Mark N as reimbursable?" is asking.
    val markAllCount: Int? = null,
    // The rows the last "Mark all" changed, held only until its Undo snackbar is answered.
    val marked: MarkedBatch? = null
)

/** Compared by identity, so a second batch with the same ids still shows its own snackbar. */
class MarkedBatch(val ids: List<Long>)

/** The rows "Mark all as reimbursable" would touch: exactly those a single row's action would accept. */
fun reimbursementMarkIds(rows: List<TransactionEntity>): List<Long> = rows.filter(::canMarkReimbursable).map { it.id }

@HiltViewModel
class TransactionGroupDetailViewModel @Inject constructor(
    private val repository: TransactionGroupRepository,
    entitlementGate: EntitlementGate,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val isProEntitled: StateFlow<Boolean> = entitlementGate.isProEntitled

    private val groupId: Long = savedStateHandle.get<Long>("groupId") ?: -1L

    private val _uiState = MutableStateFlow(TransactionGroupDetailUiState())
    val uiState: StateFlow<TransactionGroupDetailUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var addSheetJob: Job? = null

    init {
        loadGroup()
    }

    private fun loadGroup() {
        viewModelScope.launch {
            val group = repository.getGroupById(groupId)
            _uiState.value = _uiState.value.copy(group = group, isLoading = false)
        }
        viewModelScope.launch {
            repository.getTransactionsForGroup(groupId).collect { txns ->
                // Same totals as the list and the Home card, from one definition.
                val totals = groupTotalsOf(txns)
                _uiState.value = _uiState.value.copy(
                    linkedTransactions = txns,
                    expenseByCurrency = totals.expense,
                    incomeByCurrency = totals.income,
                    investedByCurrency = totals.invested,
                    accountShares = accountSharesOf(txns)
                )
            }
        }
    }

    fun showAddSheet() {
        addSheetJob?.cancel()
        _uiState.value = _uiState.value.copy(showAddSheet = true)
        addSheetJob = viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        repository.getRecentUngroupedTransactions()
                    } else {
                        repository.searchUngroupedTransactions(query)
                    }
                }
                .collect { txns ->
                    _uiState.value = _uiState.value.copy(ungroupedTransactions = txns)
                }
        }
    }

    fun hideAddSheet() {
        addSheetJob?.cancel()
        addSheetJob = null
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
    fun hideEditDialog() { _uiState.value = _uiState.value.copy(showEditDialog = false, tripOverlap = null) }

    fun updateGroupName(name: String, note: String?, trip: TripDraft) {
        val group = _uiState.value.group ?: return
        // Turning a plain group into a trip is Pro; an existing trip keeps working after a lapse.
        val (newStart, newEnd) = trip.window
        // Free: no starting a trip, no moving its dates, no "until I stop it" again.
        if (tripEditNeedsPro(group, trip.enabled, newStart, newEnd) && !isProEntitled.value) {
            _uiState.value = _uiState.value.copy(upgradeRequested = true)
            return
        }
        if (_uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            val (start, end) = trip.window
            try {
                repository.updateGroup(
                    group.copy(
                        name = name.trim(),
                        note = note?.trim()?.takeIf { it.isNotEmpty() },
                        tripStart = start,
                        tripEnd = end
                    )
                )
                val saved = repository.getGroupById(groupId)
                // A trip that now reaches further back offers the rows it would have taken.
                // Published first: the candidate query below is a wait in which a Stop can land, and
                // the final update must not put this older read of the group back over it.
                _uiState.update { it.copy(group = saved, showEditDialog = false, tripOverlap = null) }
                val found = if (saved != null && tripStartMovedEarlier(group, saved)) repository.tripBackfillCandidates(groupId) else null
                _uiState.update {
                    it.copy(backfill = found?.takeIf { f -> f.rows.isNotEmpty() }?.let { f -> TripBackfillOffer(groupId, f.rows, f.capped) })
                }
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
        viewModelScope.launch {
            // The sheet closes only once the rows are in; a failed write leaves it open to retry.
            if (runCatching { repository.addTransactionsToGroup(ids, groupId) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.isSuccess) {
                _uiState.update { it.copy(backfill = null) }
            }
        }
    }

    fun requestMarkAll() {
        val count = reimbursementMarkIds(_uiState.value.linkedTransactions).size
        if (count > 0) _uiState.update { it.copy(markAllCount = count) }
    }

    fun dismissMarkAll() { _uiState.update { it.copy(markAllCount = null) } }

    /** One database transaction for the whole batch; the changed ids are kept for the Undo. */
    fun confirmMarkAll() {
        val ids = reimbursementMarkIds(_uiState.value.linkedTransactions)
        _uiState.update { it.copy(markAllCount = null) }
        viewModelScope.launch {
            val changed = try {
                repository.markGroupReimbursable(groupId, ids)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (changed.isNotEmpty()) _uiState.update { it.copy(marked = MarkedBatch(changed)) }
        }
    }

    fun undoMarkAll(batch: MarkedBatch) {
        _uiState.update { if (it.marked === batch) it.copy(marked = null) else it }
        viewModelScope.launch { repository.undoGroupReimbursable(batch.ids) }
    }

    /** The snackbar went away unanswered: the batch stands, and its ids are let go. */
    fun clearMarked(batch: MarkedBatch) { _uiState.update { if (it.marked === batch) it.copy(marked = null) else it } }

    fun dismissUpgrade() { _uiState.value = _uiState.value.copy(upgradeRequested = false) }

    fun clearTripOverlap() { _uiState.value = _uiState.value.copy(tripOverlap = null) }

    fun stopTrip() {
        viewModelScope.launch {
            repository.stopTrip(groupId)
            _uiState.value = _uiState.value.copy(group = repository.getGroupById(groupId))
        }
    }

    fun deleteGroup() {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
            _uiState.value = _uiState.value.copy(isDeleted = true, showDeleteDialog = false)
        }
    }
}
