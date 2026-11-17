package com.pennywiseai.tracker.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.domain.goals.GoalAffordability
import com.pennywiseai.tracker.domain.goals.GoalAffordabilityLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The Goals section on the Budgets screen (#914). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val entitlementGate: EntitlementGate,
    goalAffordabilityLoader: GoalAffordabilityLoader,
) : ViewModel() {

    /**
     * Budgets can stay open across days: moved on by [refreshDate] on resume, so what
     * goals need a month and the usual month they're weighed against follow the
     * calendar, as Insights does.
     */
    private val today = MutableStateFlow(LocalDate.now())

    /** Null while loading, so the section doesn't flash its empty state. */
    val activeGoals: StateFlow<List<GoalProgress>?> = today.flatMapLatest { goalRepository.observeActiveGoals(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val archivedGoals: StateFlow<List<GoalProgress>> = goalRepository.observeArchivedGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the goals in progress fit a usual month's surplus, per goal currency. */
    val affordability: StateFlow<List<GoalAffordability>> = today.flatMapLatest { goalAffordabilityLoader.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Called on resume; a new day reloads, the same day does nothing. */
    fun refreshDate(date: LocalDate = LocalDate.now()) {
        today.value = date
    }

    /** Runs [onAllowed] when a new goal may be created, else [onBlocked] (the upgrade sheet). */
    fun requestCreate(onAllowed: () -> Unit, onBlocked: () -> Unit) {
        viewModelScope.launch {
            if (goalRepository.canCreateGoal(entitlementGate.isProEntitled.value)) onAllowed() else onBlocked()
        }
    }

    // Lives in the ViewModel (scoped to the Budgets back-stack entry), so coming
    // back from the new goal or rotating doesn't start another one.
    private var createOnOpenHandled = false

    /** [requestCreate] the first time only: the goals widget's "+" opening Budgets. */
    fun createOnOpenOnce(onAllowed: () -> Unit, onBlocked: () -> Unit) {
        if (createOnOpenHandled) return
        createOnOpenHandled = true
        requestCreate(onAllowed, onBlocked)
    }

    fun unarchive(goalId: Long) {
        viewModelScope.launch { goalRepository.setArchived(goalId, false) }
    }
}
