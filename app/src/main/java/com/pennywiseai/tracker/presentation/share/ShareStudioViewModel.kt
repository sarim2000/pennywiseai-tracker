package com.pennywiseai.tracker.presentation.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetSummaryProvider
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.repository.GoalRules
import com.pennywiseai.tracker.data.repository.GoalStatus
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.ReviewSelection
import com.pennywiseai.tracker.domain.review.defaultReviewOffset
import com.pennywiseai.tracker.domain.review.latestReviewOffset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Everything the user has picked in the studio. Never persisted: each share starts private. */
data class ShareStudioSelection(
    val kind: ShareKind,
    val period: ReviewSelection,
    /** Null picks the period's most active currency. */
    val currency: String? = null,
    /** Null picks the first goal shown. */
    val goalId: Long? = null,
    val theme: ShareTheme = ShareTheme.MIDNIGHT,
    val details: Set<ShareDetail> = DEFAULT_SHARE_DETAILS,
)

/** What the selected card is built from; [Loading] while a selection's data is on its way. */
sealed interface ShareStudioData {
    data object Loading : ShareStudioData

    /** The selected period first, then the ones before it (only the savings card asks for those). */
    data class Reviews(val reviews: List<PeriodReview>) : ShareStudioData

    data class Goals(val goals: List<GoalProgress>) : ShareStudioData

    data class Budget(val summary: BudgetOverallSummary) : ShareStudioData
}

/** How far back the budget card can go; the summary can't say whether anything is older. */
internal const val BUDGET_MONTHS_BACK = 12

/** How many periods the savings trend reaches back over, the selected one included. */
internal fun savingsTrendLength(kind: ReviewKind): Int = if (kind == ReviewKind.YEAR) 3 else 6

@HiltViewModel
class ShareStudioViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loader: PeriodReviewLoader,
    private val goalRepository: GoalRepository,
    private val budgetSummaryProvider: BudgetSummaryProvider,
) : ViewModel() {

    private val _selection = MutableStateFlow(routeSelection(savedStateHandle, LocalDate.now()))
    private val routeGoalId = _selection.value.goalId
    val selection: StateFlow<ShareStudioSelection> = _selection.asStateFlow()

    /** Which data a card needs: recap and spending share one review, so switching between them doesn't reload. */
    private enum class Source { REVIEW, HISTORY, GOALS, BUDGET }

    private data class Query(val source: Source, val period: ReviewSelection)

    private val ShareKind.source: Source
        get() = when (this) {
            ShareKind.RECAP, ShareKind.SPENDING -> Source.REVIEW
            ShareKind.SAVINGS -> Source.HISTORY
            ShareKind.GOAL -> Source.GOALS
            ShareKind.BUDGET -> Source.BUDGET
        }

    /**
     * The selected card's data, reloading only when what it depends on changes. Loading is
     * published for the gap so one period's numbers never sit under another's label.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<ShareStudioData> = _selection
        .map { Query(it.kind.source, it.period) }
        .distinctUntilChanged()
        .flatMapLatest { query -> load(query).onStart { emit(ShareStudioData.Loading) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareStudioData.Loading)

    private fun load(query: Query): Flow<ShareStudioData> {
        val (kind, offset, today) = query.period
        return when (query.source) {
            Source.REVIEW -> loader.observe(kind, offset, today).map { ShareStudioData.Reviews(listOf(it)) }
            Source.HISTORY -> combine(
                (0 until savingsTrendLength(kind)).map { back -> loader.observe(kind, offset - back, today) }
            ) { ShareStudioData.Reviews(it.toList()) }
            // An archived goal stays shareable from its own screen, after the active ones.
            Source.GOALS -> goalRepository.observeGoals(today).map { goals ->
                val opened = goals.filter { it.status == GoalStatus.ARCHIVED && it.goal.id == routeGoalId }
                ShareStudioData.Goals(GoalRules.orderForDisplay(goals) + opened)
            }
            Source.BUDGET -> budgetSummaryProvider.summary(budgetMonth(query.period)).map { ShareStudioData.Budget(it) }
        }
    }

    fun selectKind(kind: ShareKind) = _selection.update { current ->
        // Budgets are monthly: the budget card has no week or year.
        val period = if (kind == ShareKind.BUDGET && current.period.kind != ReviewKind.MONTH) {
            defaultSelection(ReviewKind.MONTH, current.period.today)
        } else current.period
        current.copy(kind = kind, period = period)
    }

    fun selectPeriodKind(kind: ReviewKind) = _selection.update {
        it.copy(period = defaultSelection(kind, it.period.today), currency = null)
    }

    fun older() = _selection.update { it.copy(period = it.period.copy(offset = it.period.offset - 1)) }

    fun newer() = _selection.update {
        val latest = latestReviewOffset(it.period.kind)
        it.copy(period = it.period.copy(offset = (it.period.offset + 1).coerceAtMost(latest)))
    }

    fun selectCurrency(currency: String) = _selection.update { it.copy(currency = currency) }

    fun selectGoal(goalId: Long) = _selection.update { it.copy(goalId = goalId) }

    fun selectTheme(theme: ShareTheme) = _selection.update { it.copy(theme = theme) }

    fun setDetail(detail: ShareDetail, shown: Boolean) = _selection.update {
        it.copy(details = if (shown) it.details + detail else it.details - detail)
    }
}

/** The calendar month the Budgets screen would show for [period]: the current one, counted back. */
internal fun budgetMonth(period: ReviewSelection): YearMonth = YearMonth.from(period.today).plusMonths(period.offset.toLong())

private fun defaultSelection(kind: ReviewKind, today: LocalDate) =
    ReviewSelection(kind, defaultReviewOffset(kind, today), today)

/**
 * The route's card and period: a review shares the period it shows, a goal its own goal,
 * Home the last finished cycle.
 */
internal fun routeSelection(savedStateHandle: SavedStateHandle, today: LocalDate): ShareStudioSelection {
    val kind = ShareKind.fromName(savedStateHandle.get<String>("kind")) ?: ShareKind.RECAP
    val reviewKind = savedStateHandle.get<String>("reviewKind")?.let(ReviewKind::fromName)
    val period = if (reviewKind != null) {
        val offset = (savedStateHandle.get<Int>("offset") ?: latestReviewOffset(reviewKind))
            .coerceAtMost(latestReviewOffset(reviewKind))
        // The review's own day, so a period shared across midnight is still the one it showed.
        val from = savedStateHandle.get<Long>("today")?.let(LocalDate::ofEpochDay) ?: today
        ReviewSelection(reviewKind, offset, from)
    } else {
        defaultSelection(ReviewKind.MONTH, today)
    }
    return ShareStudioSelection(
        kind = kind,
        period = if (kind == ShareKind.BUDGET && period.kind != ReviewKind.MONTH) defaultSelection(ReviewKind.MONTH, today) else period,
        goalId = savedStateHandle.get<Long>("goalId")?.takeIf { it > 0 },
    )
}
