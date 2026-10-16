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

    fun unarchive(goalId: Long) {
        viewModelScope.launch { goalRepository.setArchived(goalId, false) }
    }
}
