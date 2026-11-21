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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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
            // Zoned, so a DST night (23 or 25 hours) still wakes at the real midnight.
            val zone = ZoneId.systemDefault()
            delay(Duration.between(ZonedDateTime.now(zone), day.plusDays(1).atStartOfDay(zone)).toMillis() + 1)
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
                // Matched by the category's current name: a rename moves its expenses with it.
                val current = run?.let { r -> r.categoryId?.let { id -> categories.firstOrNull { it.id == id } }?.let { r.copy(categoryName = it.name) } ?: r }
                val progress = current?.progress(counted, today)
                // Shown as the result at once, not after the write lands: a list that briefly
                // showed only the presets would keep them anchored and push the result off screen.
                val finished = progress?.takeIf { it.finished }?.let { ChallengeOutcome(run, it.cleanDays, it.won) }
                if (finished != null) commit(finished)
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

    /** Records [outcome]; a win cheers once per run, and only once the record has landed. */
    private fun commit(outcome: ChallengeOutcome) {
        viewModelScope.launch {
            var recorded = false
            preferences.updateChallenges { state ->
                state.finish(outcome.run, outcome.cleanDays, outcome.won).also { recorded = it.active == null && state.active != null }
            }
            if (recorded && outcome.won && preferences.celebrateOnce(outcome.run.celebrationKey)) _celebrate.value = true
        }
    }

    fun onCelebrated() { _celebrate.value = false }

    fun start(kind: ChallengeKind, categoryName: String?, categoryId: Long? = null) {
        viewModelScope.launch {
            val categories = categoryRepository.getAllCategories().first()
            // A retry passes the id from the run that just ended, so a rename still
            // starts the same category. A first start has no id, and the name is the lookup.
            val renamed = categoryId?.let { id -> categories.firstOrNull { it.id == id } }
            val name = renamed?.name ?: categoryName
            val id = renamed?.id
                ?: categoryName?.let { wanted -> categories.firstOrNull { it.name == wanted }?.id }
            val run = ChallengeRun.start(kind, name, LocalDate.now(), id)
            preferences.updateChallenges { it.markSeen().copy(active = run) }
        }
    }

    /** Leaves [run] quietly: nothing is recorded against the user. */
    fun giveUp(run: ChallengeRun) {
        viewModelScope.launch { preferences.updateChallenges { it.giveUp(run) } }
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
        // Only shares with something counted: a split's zero share doesn't spend its category.
        categories = row.getAmountByCategory().filterValues { it.signum() > 0 }.keys.map { it.ifEmpty { "Others" }.let { name -> parentOf[name] ?: name } }.toSet(),
        merchant = tx.merchantName,
    )
}
