package com.pennywiseai.tracker.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.ui.components.PRESET_COLORS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class GoalEditUiState(
    val isLoading: Boolean = true,
    val form: GoalForm = GoalForm(),
    /** The stored goal when editing; null when creating. */
    val original: GoalEntity? = null,
    val contributions: List<GoalContributionEntity> = emptyList(),
    /** Errors only show after the first save attempt, so an empty form isn't red. */
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = original != null
    /**
     * Currency is locked once the goal holds any money — contributions or an
     * Already saved amount — or ₹5,000 would silently become $5,000. The
     * repository enforces it too.
     */
    val currencyLocked: Boolean get() = contributions.isNotEmpty() || (original?.alreadySaved?.signum() ?: 0) > 0
}

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val goalId: Long = savedStateHandle.get<Long>("goalId") ?: -1L
    val today: LocalDate = LocalDate.now()

    private val _uiState = MutableStateFlow(GoalEditUiState())
    val uiState: StateFlow<GoalEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val detail = if (goalId > 0) goalRepository.observeGoalDetail(goalId, today).first() else null
            _uiState.value = if (detail != null) {
                GoalEditUiState(
                    isLoading = false,
                    form = GoalForm.from(detail.progress.goal),
                    original = detail.progress.goal,
                    contributions = detail.contributions,
                )
            } else {
                GoalEditUiState(
                    isLoading = false,
                    form = GoalForm(
                        currency = userPreferencesRepository.baseCurrency.first(),
                        dueDate = today.plusYears(1),
                        color = PRESET_COLORS[5],
                    ),
                )
            }
        }
    }

    fun update(transform: (GoalForm) -> GoalForm) = _uiState.update { it.copy(form = transform(it.form), saveFailed = false) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.form.errors(today, state.original).isNotEmpty()) {
            _uiState.update { it.copy(showErrors = true) }
            return
        }
        val form = state.form
        _uiState.update { it.copy(isSaving = true, showErrors = true) }
        viewModelScope.launch {
            val result = runCatching {
                val original = state.original
                if (original == null) {
                    goalRepository.createGoal(
                        name = form.name, targetAmount = form.targetAmount!!, currency = form.currency,
                        dueDate = form.dueDate!!, color = form.color, alreadySaved = form.alreadySavedAmount!!,
                    )
                } else {
                    goalRepository.updateGoal(
                        original.copy(
                            name = form.name, targetAmount = form.targetAmount!!, currency = form.currency,
                            dueDate = form.dueDate!!, color = form.color,
                        )
                    )
                }
            }
            _uiState.update { it.copy(isSaving = false, saved = result.isSuccess, saveFailed = result.isFailure) }
        }
    }
}
