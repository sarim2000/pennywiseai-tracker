package com.pennywiseai.tracker.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.database.entity.parentNameOf
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.domain.challenges.ChallengeKind
import com.pennywiseai.tracker.domain.challenges.ChallengeOutcome
import com.pennywiseai.tracker.domain.challenges.ChallengeProgress
import com.pennywiseai.tracker.domain.challenges.ChallengeRow
import com.pennywiseai.tracker.domain.challenges.ChallengeRun
import com.pennywiseai.tracker.domain.challenges.challengeCategories
import com.pennywiseai.tracker.domain.challenges.progress
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.spending.analyticsRowsFor
import com.pennywiseai.tracker.domain.spending.defaultTypeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class ChallengesUiState(
    /** The running challenge; null once its window is over (or it's won). */
    val active: ChallengeProgress? = null,
    /** The latest result, until the user moves on from it. */
    val result: ChallengeOutcome? = null,
    /** The categories a "week without" is offered for. */
    val categories: List<String> = emptyList(),
)

/** Plan → Challenges: one challenge at a time, its days worked out from the same rows as Review. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChallengesViewModel @Inject constructor(
    private val loader: PeriodReviewLoader,
    private val categoryRepository: CategoryRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    /** Today, moving on at midnight while the screen stays open. */
    private val today = flow {
        while (true) {
            val day = LocalDate.now()
            emit(day)
            delay(Duration.between(LocalDateTime.now(), day.plusDays(1).atStartOfDay()).toMillis() + 1)
        }
    }.distinctUntilChanged()

    val state: StateFlow<ChallengesUiState?> = combine(preferences.challenges, today, ::Pair)
        .flatMapLatest { (stored, today) ->
            val run = stored.active
            // The run's window and the four weeks the category list is ranked over.
            val lookback = today.minusDays(LOOKBACK_DAYS)
            val from = run?.start?.let { minOf(it, lookback) } ?: lookback
            val to = run?.end?.let { maxOf(it, today) } ?: today
            combine(
                loader.scopedRows(from, to),
                preferences.countCreditCardAsExpense,
                categoryRepository.getAllCategories(),
            ) { rows, creditAsExpense, categories ->
                val counted = challengeRows(rows, creditAsExpense, categories.parentNameOf())
                val progress = run?.progress(counted, today)
                // Shown as the result at once, not after the write lands: a list that briefly
                // showed only the presets would keep them anchored and push the result off screen.
                val finished = progress?.takeIf { it.finished }?.let { ChallengeOutcome(run, it.cleanDays, it.won) }
                if (finished != null) {
                    viewModelScope.launch { preferences.updateChallenges { it.finish(run, finished.cleanDays, finished.won) } }
                }
                ChallengesUiState(
                    active = progress?.takeUnless { it.finished },
                    result = finished ?: stored.unseenResult,
                    categories = challengeCategories(counted.filter { it.date >= lookback && it.date < today }),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _celebrate = MutableStateFlow(false)

    /** True while a won challenge is being celebrated. */
    val celebrate: StateFlow<Boolean> = _celebrate.asStateFlow()

    /** The screen shows [result]: a win cheers, once per run. */
    fun onShownResult(result: ChallengeOutcome) {
        if (!result.won) return
        viewModelScope.launch { if (preferences.celebrateOnce(result.run.celebrationKey)) _celebrate.value = true }
    }

    fun onCelebrated() { _celebrate.value = false }

    fun start(kind: ChallengeKind, categoryName: String?) {
        val run = ChallengeRun.start(kind, categoryName, LocalDate.now())
        viewModelScope.launch { preferences.updateChallenges { it.markSeen().copy(active = run) } }
    }

    /** Leaves quietly: nothing is recorded against the user. */
    fun giveUp() {
        viewModelScope.launch { preferences.updateChallenges { it.copy(active = null) } }
    }

    fun dismissResult() {
        viewModelScope.launch { preferences.updateChallenges { it.markSeen() } }
    }

    private companion object {
        const val LOOKBACK_DAYS = 28L
    }
}

/**
 * The expenses Review counts ([analyticsRowsFor]: no transfers between own accounts,
 * loans or exclusions), one per row whatever its amount or currency.
 */
internal fun challengeRows(
    rows: List<TransactionWithSplits>,
    creditAsExpense: Boolean,
    parentOf: Map<String, String>,
): List<ChallengeRow> = analyticsRowsFor(rows, defaultTypeFilter(creditAsExpense)).map { row ->
    val tx = row.transaction
    ChallengeRow(
        id = tx.id,
        date = tx.dateTime.toLocalDate(),
        categories = row.getAmountByCategory().keys.map { it.ifEmpty { "Others" }.let { name -> parentOf[name] ?: name } }.toSet(),
        merchant = tx.merchantName,
    )
}
