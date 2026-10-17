package com.pennywiseai.tracker.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Goals section on the Budgets screen (#914). */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val entitlementGate: EntitlementGate,
) : ViewModel() {

    /** Null while loading, so the section doesn't flash its empty state. */
    val activeGoals: StateFlow<List<GoalProgress>?> = goalRepository.observeActiveGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val archivedGoals: StateFlow<List<GoalProgress>> = goalRepository.observeArchivedGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
