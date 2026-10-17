package com.pennywiseai.tracker.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.repository.GoalDetail
import com.pennywiseai.tracker.data.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** Loading → Loaded(detail) or Gone (deleted, or a stale id). */
sealed interface GoalDetailState {
    data object Loading : GoalDetailState
    data class Loaded(val detail: GoalDetail) : GoalDetailState
    data object Gone : GoalDetailState
}

/** State of the open Add money / Take out / edit sheet. [done] closes it. */
data class MoneySheetState(val saving: Boolean = false, val error: MoneyError? = null, val done: Boolean = false)

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val conversionService: CurrencyConversionService,
    private val entitlementGate: EntitlementGate,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val goalId: Long = savedStateHandle.get<Long>("goalId") ?: -1L

    val state: StateFlow<GoalDetailState> = goalRepository.observeGoalDetail(goalId)
        .map { detail -> detail?.let { GoalDetailState.Loaded(it) } ?: GoalDetailState.Gone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailState.Loading)

    /** Merchant of each linked transaction, by transaction id, for the money list; follows renames. */
    val merchants: StateFlow<Map<Long, String>> = goalRepository.observeLinkedMerchants(goalId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _sheet = MutableStateFlow(MoneySheetState())
    val sheet: StateFlow<MoneySheetState> = _sheet.asStateFlow()

    private val saved: BigDecimal
        get() = (state.value as? GoalDetailState.Loaded)?.detail?.progress?.saved ?: BigDecimal.ZERO

    fun resetSheet() { _sheet.value = MoneySheetState() }

    /**
     * Adds money (or takes it out). [amount] is in [currency]; when that isn't
     * the goal's, it's converted at today's rate and the original kept. With no
     * rate the sheet asks for [manualConverted], the amount in the goal's currency.
     */
    fun saveMovement(
        isWithdrawal: Boolean,
        amount: BigDecimal?,
        currency: String,
        date: LocalDate,
        note: String,
        manualConverted: BigDecimal?,
    ) {
        val goal = (state.value as? GoalDetailState.Loaded)?.detail?.progress?.goal ?: return
        if (_sheet.value.saving) return
        if (amount == null || amount.signum() <= 0) { _sheet.value = MoneySheetState(error = MoneyError.AMOUNT_INVALID); return }
        _sheet.value = MoneySheetState(saving = true)
        viewModelScope.launch {
            val foreign = !currency.equals(goal.currency, ignoreCase = true)
            // ponytail: latest rate, not the rate on [date] — the service keeps no history.
            val converted = when {
                !foreign -> amount
                manualConverted != null -> manualConverted
                else -> conversionService.convertAmountOrNull(amount, currency, goal.currency)
            }
            if (converted == null) { _sheet.value = MoneySheetState(error = MoneyError.NO_RATE); return@launch }
            validateMovement(converted, isWithdrawal, saved)?.let { _sheet.value = MoneySheetState(error = it); return@launch }
            val result = runCatching {
                val original = if (foreign) amount else null
                val originalCurrency = if (foreign) currency else null
                if (isWithdrawal) goalRepository.addWithdrawal(goalId, converted, date, note, original, originalCurrency)
                else goalRepository.addContribution(goalId, converted, date, note, original, originalCurrency)
            }
            _sheet.value = if (result.isSuccess) MoneySheetState(done = true) else MoneySheetState(error = MoneyError.FAILED)
        }
    }

    fun saveContributionEdit(existing: GoalContributionEntity, amount: BigDecimal?, date: LocalDate, note: String) {
        if (_sheet.value.saving) return
        val edited = editContribution(existing, amount, date, note, saved)
            .getOrElse { _sheet.value = MoneySheetState(error = (it as? MoneyException)?.error ?: MoneyError.FAILED); return }
        _sheet.value = MoneySheetState(saving = true)
        viewModelScope.launch {
            val result = runCatching { goalRepository.updateContribution(edited) }
            _sheet.value = if (result.isSuccess) MoneySheetState(done = true) else MoneySheetState(error = MoneyError.FAILED)
        }
    }

    fun deleteContribution(id: Long) {
        viewModelScope.launch { goalRepository.deleteContribution(id) }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { goalRepository.setArchived(goalId, archived) }
    }

    fun delete() {
        viewModelScope.launch { goalRepository.deleteGoal(goalId) }
    }

    /** Repeat creates a new goal, so it passes the same Pro gate as creating one. */
    fun repeatForNextYear(onCreated: (Long) -> Unit, onBlocked: () -> Unit) {
        viewModelScope.launch {
            if (!goalRepository.canCreateGoal(entitlementGate.isProEntitled.value)) { onBlocked(); return@launch }
            runCatching { goalRepository.repeatForNextYear(goalId) }.onSuccess(onCreated)
        }
    }
}
