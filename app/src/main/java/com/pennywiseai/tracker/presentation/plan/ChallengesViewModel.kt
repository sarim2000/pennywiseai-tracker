package com.pennywiseai.tracker.presentation.plan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.database.entity.nameForBuiltin
import com.pennywiseai.tracker.data.database.entity.parentNameOf
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.domain.challenges.ChallengeKind
import com.pennywiseai.tracker.domain.challenges.ChallengeOutcome
import com.pennywiseai.tracker.domain.challenges.ChallengeProgress
import com.pennywiseai.tracker.domain.challenges.ChallengeRow
import com.pennywiseai.tracker.domain.challenges.ChallengeRun
import com.pennywiseai.tracker.domain.challenges.FOOD_AND_DINING
import com.pennywiseai.tracker.domain.challenges.OTHERS
import com.pennywiseai.tracker.domain.challenges.challengeCategories
import com.pennywiseai.tracker.domain.challenges.progress
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.spending.analyticsRowsFor
import com.pennywiseai.tracker.utils.BackgroundAccessState
import com.pennywiseai.tracker.utils.TrackingProblem
import com.pennywiseai.tracker.utils.canReadSms
import com.pennywiseai.tracker.utils.canReceiveSms
import com.pennywiseai.tracker.utils.isBackgroundRestricted
import com.pennywiseai.tracker.utils.trackingProblem
import com.pennywiseai.tracker.worker.SmsScanManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
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
    /** What stops bank messages being read; while set, nothing starts and no day settles. */
    val trackingProblem: TrackingProblem? = null,
    /** An inbox scan is running, so days waiting for one are being read now. */
    val scanning: Boolean = false,
)

/** Plan → Challenges: one challenge at a time, its days worked out from the same rows as Review. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChallengesViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val loader: PeriodReviewLoader,
    private val categoryRepository: CategoryRepository,
    private val preferences: UserPreferencesRepository,
    private val smsScanManager: SmsScanManager,
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

    /** Whether bank messages are being read at all: a win needs days that were watched. */
    private val tracking = MutableStateFlow(currentTrackingProblem())

    private fun currentTrackingProblem(): TrackingProblem? = trackingProblem(
        context.canReadSms() && context.canReceiveSms(),
        BackgroundAccessState(backgroundRestricted = context.isBackgroundRestricted()),
    )

    /** Call on resume and after a grant: every fix happens outside this screen. */
    fun refreshTracking() {
        tracking.value = currentTrackingProblem()
    }

    val state: StateFlow<ChallengesUiState?> = combine(preferences.challenges, today, tracking, ::Triple)
        .flatMapLatest { (stored, today, problem) ->
            val run = stored.active
            // The run's window and the four weeks the category list is ranked over.
            val lookback = today.minusDays(LOOKBACK_DAYS)
            val from = run?.start?.let { minOf(it, lookback) } ?: lookback
            val to = run?.end?.let { maxOf(it, today) } ?: today
            combine(
                // A run counts the profile it was started under, whichever is selected now.
                loader.scopedRows(from, to, run?.let { flowOf(it.profileId) } ?: preferences.selectedProfileId),
                categoryRepository.getAllCategories(),
                // Where the last finished inbox scan read up to: a clean day must be behind it.
                preferences.getLastScanTimestamp().distinctUntilChanged(),
                smsScanManager.isScanRunning(),
            ) { rows, categories, scannedAt, scanning ->
                val counted = challengeRows(rows, categories.parentNameOf())
                // Matched by the category's current name: a rename moves its expenses with it.
                val current = run?.let { r -> r.categoryId?.let { id -> categories.firstOrNull { it.id == id } }?.let { r.copy(categoryName = it.name) } ?: r }
                val progress = current?.progress(counted, today, tracking = problem == null, inboxReadBefore = readBefore(scannedAt))
                // Days a gap in tracking left without rows: read the inbox before they count.
                if (progress?.needsInboxRead == true && !scanning) catchUp(scannedAt, today)
                // Shown as the result at once, not after the write lands: a list that briefly
                // showed only the presets would keep them anchored and push the result off screen.
                val finished = progress?.takeIf { it.finished }?.let { ChallengeOutcome(run, it.cleanDays, it.won) }
                if (finished != null) commit(finished)
                ChallengesUiState(
                    active = progress?.takeUnless { it.finished },
                    result = finished ?: stored.unseenResult,
                    categories = challengeCategories(
                        counted.filter { it.date >= lookback && it.date < today },
                        food = categories.nameForBuiltin(FOOD_AND_DINING),
                        others = setOf(OTHERS, categories.nameForBuiltin(OTHERS)),
                    ),
                    trackingProblem = problem,
                    scanning = scanning,
                )
            }.flowOn(Dispatchers.Default)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The (scan cursor, day) a catch-up scan was asked for: a scan that fails isn't re-asked in a loop. */
    private var catchUpAsked: Pair<Long?, LocalDate>? = null

    private fun catchUp(scannedAt: Long?, today: LocalDate) {
        if (catchUpAsked == scannedAt to today) return
        catchUpAsked = scannedAt to today
        smsScanManager.startScanUnlessRunning()
    }

    private val _celebrate = MutableStateFlow(false)

    /** True while a won challenge is being celebrated. */
    val celebrate: StateFlow<Boolean> = _celebrate.asStateFlow()

    /** Records [outcome]; a win cheers once per run, and only once the record has landed. */
    private fun commit(outcome: ChallengeOutcome) {
        viewModelScope.launch {
            var recorded = false
            val saved = preferences.updateChallenges { state ->
                state.finish(outcome.run, outcome.cleanDays, outcome.won).also { recorded = it.active == null && state.active != null }
            }
            if (saved && recorded && outcome.won && preferences.celebrateOnce(outcome.run.celebrationKey)) _celebrate.value = true
        }
    }

    fun onCelebrated() { _celebrate.value = false }

    fun start(kind: ChallengeKind, categoryName: String?, categoryId: Long? = null) {
        if (tracking.value != null) return
        viewModelScope.launch {
            val categories = categoryRepository.getAllCategories().first()
            // A retry passes the id from the run that just ended, so a rename still
            // starts the same category. A first start has no id, and the name is the lookup.
            val renamed = categoryId?.let { id -> categories.firstOrNull { it.id == id } }
            val name = renamed?.name ?: categoryName
            val id = renamed?.id
                ?: categoryName?.let { wanted -> categories.firstOrNull { it.name == wanted }?.id }
            val run = ChallengeRun.start(kind, name, LocalDate.now(), id, preferences.selectedProfileId.first())
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

        /** The first day an inbox scan finished at [scannedAt] hasn't wholly read; nothing read without one. */
        fun readBefore(scannedAt: Long?): LocalDate =
            scannedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.MIN
    }
}

/**
 * The spending Review counts ([analyticsRowsFor]: no transfers between own accounts,
 * loans or exclusions), one per row whatever its amount or currency. Card purchases
 * always count, whatever "count credit card as expense" says: that setting picks how
 * money is totalled, and a swipe at a restaurant is still spending that day.
 */
internal fun challengeRows(
    rows: List<TransactionWithSplits>,
    parentOf: Map<String, String>,
): List<ChallengeRow> = analyticsRowsFor(rows, TransactionTypeFilter.SPENDING).map { row ->
    val tx = row.transaction
    ChallengeRow(
        id = tx.id,
        date = tx.dateTime.toLocalDate(),
        // Only shares with something counted: a split's zero share doesn't spend its category.
        categories = row.getAmountByCategory().filterValues { it.signum() > 0 }.keys.map { it.ifEmpty { OTHERS }.let { name -> parentOf[name] ?: name } }.toSet(),
        merchant = tx.merchantName,
    )
}
