package com.pennywiseai.tracker.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/** A contribution taken from the open transaction, with its goal (null if the goal is gone). */
data class LinkedGoal(val contribution: GoalContributionEntity, val goal: GoalEntity?)

/** The Add to goal sheet. [left] is null while loading; [done] closes it. */
data class AddToGoalState(
    val left: BigDecimal? = null,
    val saving: Boolean = false,
    val error: MoneyError? = null,
    val done: Boolean = false,
)

/**
 * Goals on the transaction screen (#914): the "Toward goals" rows and the
 * Add to goal sheet. Goals never change the transaction itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionGoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val conversionService: CurrencyConversionService,
) : ViewModel() {

    private val transaction = MutableStateFlow<TransactionEntity?>(null)

    /** Goals that can take money: everything not archived, soonest due first. */
    val goals: StateFlow<List<GoalProgress>> = goalRepository.observeActiveGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val linked: StateFlow<List<LinkedGoal>> = transaction.map { it?.id }.distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else combine(goalRepository.observeContributionsForTransaction(id), goalRepository.observeGoals()) { rows, all ->
                val byId = all.associate { it.goal.id to it.goal }
                rows.map { LinkedGoal(it, byId[it.goalId]) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sheet = MutableStateFlow(AddToGoalState())
    val sheet: StateFlow<AddToGoalState> = _sheet.asStateFlow()

    fun setTransaction(txn: TransactionEntity?) { transaction.value = txn }

    /** Loads what's left of the transaction to put toward goals into the open sheet. */
    fun openSheet() {
        val id = transaction.value?.id ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _sheet.value = AddToGoalState(left = runCatching { goalRepository.unallocatedAmount(id) }.getOrDefault(BigDecimal.ZERO))
        }
    }

    /**
     * Forgets the closed sheet, so the next open loads a fresh amount left. A
     * load still running is cancelled, or it could refill the closed sheet with
     * a stale amount that the next open would then reuse.
     */
    fun closeSheet() {
        loadJob?.cancel()
        loadJob = null
        _sheet.value = AddToGoalState()
    }

    private var loadJob: kotlinx.coroutines.Job? = null

    fun clearError() { _sheet.value = _sheet.value.copy(error = null) }

    /**
     * Adds [share] (transaction currency) to [goal], dated the transaction's day.
     * [manual] is the amount in the goal's currency when there's no rate.
     */
    fun add(goal: GoalEntity, share: BigDecimal?, note: String, manual: BigDecimal?) {
        val txn = transaction.value ?: return
        val current = _sheet.value
        val left = current.left ?: return
        if (current.saving) return
        _sheet.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            // ponytail: latest rate, not the rate on the transaction's date — the service keeps no history.
            val result = shareForGoal(share, left, txn.currency, goal.currency, manual) {
                conversionService.convertAmountOrNull(it, txn.currency, goal.currency)
            }.mapCatching {
                goalRepository.addContribution(
                    goalId = goal.id,
                    amount = it.amount,
                    date = txn.dateTime.toLocalDate(),
                    note = note,
                    originalAmount = it.originalAmount,
                    originalCurrency = it.originalCurrency,
                    transactionId = txn.id,
                )
            }
            _sheet.value = result.fold(
                onSuccess = { AddToGoalState(left = left, done = true) },
                onFailure = { e ->
                    val error = when (e) {
                        is MoneyException -> e.error
                        // The repository re-checks the fit; another save may have used it up.
                        is IllegalArgumentException -> MoneyError.MORE_THAN_LEFT
                        else -> MoneyError.FAILED
                    }
                    val freshLeft = runCatching { goalRepository.unallocatedAmount(txn.id) }.getOrDefault(left)
                    AddToGoalState(left = freshLeft, error = error)
                },
            )
        }
    }

    /** Takes this transaction's money back out of one goal (the transaction is untouched). */
    fun remove(contributionId: Long) {
        viewModelScope.launch { goalRepository.deleteContribution(contributionId) }
    }
}
