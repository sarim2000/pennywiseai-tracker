package com.pennywiseai.tracker.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.repository.GoalStatus
import com.pennywiseai.tracker.domain.goals.GoalAffordability
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.spending.CycleAverages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Goals section on the Budgets screen (#914). */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val entitlementGate: EntitlementGate,
    private val periodReviewLoader: PeriodReviewLoader,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    /** Null while loading, so the section doesn't flash its empty state. */
    val activeGoals: StateFlow<List<GoalProgress>?> = goalRepository.observeActiveGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val archivedGoals: StateFlow<List<GoalProgress>> = goalRepository.observeArchivedGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Whether the goals in progress fit a usual month's surplus, per goal currency.
     * Goals span every profile, so this is empty while one profile is selected; the
     * cycles behind it load only once a goal is in progress.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val affordability: StateFlow<List<GoalAffordability>> = run {
        val goals = activeGoals.filterNotNull()
        val averages = combine(
            goals.map { list -> list.any { it.status == GoalStatus.IN_PROGRESS } },
            userPreferencesRepository.selectedProfileId,
        ) { inProgress, profileId -> inProgress && profileId == null }
            .distinctUntilChanged()
            .flatMapLatest { show ->
                if (!show) {
                    flowOf(null)
                } else {
                    periodReviewLoader.observe(ReviewKind.MONTH, 0, detail = true)
                        .map<_, Map<String, CycleAverages>?> { CycleAverages.of(it.detail?.cycles.orEmpty()) }
                        // Goals still show if the history can't load, just without this line.
                        .catch { emit(null) }
                }
            }
        combine(goals, averages) { list, byCurrency ->
            byCurrency?.let { GoalAffordability.of(list, it) }.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
