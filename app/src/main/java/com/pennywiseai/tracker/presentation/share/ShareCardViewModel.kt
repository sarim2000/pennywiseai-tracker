package com.pennywiseai.tracker.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.share.ShareCardConfig
import com.pennywiseai.tracker.data.share.ShareHero
import com.pennywiseai.tracker.data.share.SharePeriod
import com.pennywiseai.tracker.data.share.ShareWindow
import com.pennywiseai.tracker.data.statement.isStatementSender
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.domain.model.filterTransactionsByProfile
import com.pennywiseai.tracker.presentation.common.getCycleAwareDateRange
import com.pennywiseai.tracker.domain.review.isCalendarMonth
import com.pennywiseai.tracker.utils.dateQueryWindow
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.DateRangeUtils
import com.pennywiseai.tracker.utils.forTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject

/**
 * Numbers behind the share card. Everything here is a count or a name — no amounts, by
 * design, which is why nothing in this file touches CurrencyFormatter or sumByCurrency.
 */
data class ShareCardData(
    /** Rows that really came from a bank text: the card's "0 typed" has to be true. */
    val transactionCount: Int = 0,
    val topCategories: List<String> = emptyList(),
    val subscriptionCount: Int = 0,
    val merchantCount: Int = 0,
    // null = all time; ShareCard renders the translated label.
    val periodLabel: String? = "",
) {
    fun countFor(hero: ShareHero): Int = when (hero) {
        ShareHero.TRANSACTIONS -> transactionCount
        ShareHero.SUBSCRIPTIONS -> subscriptionCount
        ShareHero.MERCHANTS -> merchantCount
    }
}

/**
 * Manual entries and recurring templates have no sender and statements have a parser's
 * name, so neither counts: "bank texts tracked, 0 typed" is the claim on the image.
 */
internal fun trackedFromBankTexts(rows: List<TransactionEntity>): Int =
    rows.count { row -> row.smsSender?.let { !isStatementSender(it) } == true }

/** Distinct merchants actually spent at; loan-only and excluded rows drop out via [forTotals]. */
internal fun merchantsPaid(rows: List<TransactionEntity>): Int = rows.forTotals()
    .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
    .map { it.merchantName.trim().lowercase() }
    .filter { it.isNotEmpty() }
    .distinct()
    .size

@HiltViewModel
class ShareCardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val cardRepository: CardRepository,
) : ViewModel() {

    private val _config = MutableStateFlow(ShareCardConfig())
    val config: StateFlow<ShareCardConfig> = _config.asStateFlow()

    /** The dates a review opened the sheet on; null uses the saved period. Never persisted. */
    private val window = MutableStateFlow<ShareWindow?>(null)

    /**
     * Gates [data]: queries wait for the saved config so the card never loads the
     * default period's data only to flip to the saved one a frame later.
     */
    private val configLoaded = MutableStateFlow(false)

    /**
     * Edits made before the saved config finished loading, kept so the load can replay
     * them on top of the snapshot it read. Simply letting either side win loses data:
     * the snapshot winning reverts the user's tap, and the edit winning persists a
     * candidate derived from the *default* config — a pre-load period tap would
     * silently reset a saved non-default hero.
     */
    private val preLoadEdits = mutableListOf<(ShareCardConfig) -> ShareCardConfig>()

    init {
        viewModelScope.launch {
            var merged = userPreferencesRepository.shareCardConfig.first()
            preLoadEdits.forEach { merged = it(merged) }
            _config.value = merged
            if (preLoadEdits.isNotEmpty()) {
                preLoadEdits.clear()
                // The pre-load updateConfig persisted its default-derived candidate;
                // re-persist the merge to put the fields it never touched back.
                userPreferencesRepository.setShareCardConfig(merged)
            }
            configLoaded.value = true
        }
    }

    /**
     * Everything on the card, or null while it is still being computed — the sheet keeps
     * Share disabled on null so a half-refreshed card can never be exported.
     *
     * Built as one reactive pipeline rather than one-shot reads because this ViewModel
     * outlives a single viewing of the sheet: any snapshot taken "on open" goes stale the
     * moment the underlying data changes while the sheet is closed. Deriving from the
     * live flows means a profile switch, an account being reassigned between profiles,
     * or new transactions all reach the card without anyone having to remember to
     * trigger a refresh. flatMapLatest drops the old period/profile's in-flight query
     * when the selection changes, and the onStart null publishes "loading" for the gap
     * so the previous selection's numbers are never shown — or shared — under the new
     * selection's label.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<ShareCardData?> =
        combine(
            configLoaded,
            _config,
            window,
            userPreferencesRepository.selectedProfileId,
            userPreferencesRepository.budgetCycleStartDay,
        ) { loaded, config, window, profileId, cycleStartDay ->
            if (loaded) CardQuery(window, config.period, profileId, cycleStartDay) else null
        }
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { cardData(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class CardQuery(
        val window: ShareWindow?,
        val period: SharePeriod,
        val profileId: Long?,
        val cycleStartDay: Int,
    )

    private fun cardData(query: CardQuery): Flow<ShareCardData?> {
        val now = LocalDate.now()
        fun cycle(period: TimePeriod) = getCycleAwareDateRange(period, query.cycleStartDay, now)!!
        val (start, end) = when {
            query.window != null -> query.window.start to query.window.end
            query.period == SharePeriod.THIS_MONTH -> cycle(TimePeriod.THIS_MONTH).first to now
            query.period == SharePeriod.LAST_MONTH -> cycle(TimePeriod.LAST_MONTH)
            else -> LocalDate.of(2000, 1, 1) to now.plusYears(10)
        }
        val label = when {
            query.window != null -> query.window.label
            query.period == SharePeriod.ALL_TIME -> null
            else -> cycleLabel(if (query.period == SharePeriod.THIS_MONTH) cycle(TimePeriod.THIS_MONTH) else start to end)
        }

        val from = start.atStartOfDay()
        val to = end.atTime(LocalTime.MAX)
        val (queryStart, queryEnd) = dateQueryWindow(from, to)

        return combine(
            transactionRepository.getTransactionsBetweenDates(queryStart, queryEnd),
            accountBalanceRepository.getAllLatestBalances(),
            subscriptionRepository.getActiveSubscriptions(),
            ignoredAccountsStore.keysFlow,
            cardRepository.getAllCards(),
        ) { allTransactions, balances, subscriptions, ignored, cards ->
            val ignoredKeys = IgnoredAccountsStore.withLinkedCards(ignored, cards, balances)
            // Scope to the profile the user is currently viewing, without ignored accounts,
            // as Home does for every figure it shows. A recap that didn't would count a
            // Business profile's card, or someone else's account, as the owner's — wrong,
            // and it would put those counts into an image built for sharing.
            val transactions = filterTransactionsByProfile(
                allTransactions.filter {
                    it.dateTime in from..to && !IgnoredAccountsStore.isIgnored(ignoredKeys, it.bankName, it.accountNumber)
                },
                query.profileId,
                buildProfileAccountKeys(balances),
            )

            // Ranked in Kotlin from the same filtered list rather than by a GROUP BY:
            // the query can't see the profile filter, and deriving both figures from one
            // list means the count and the categories can never describe different
            // populations.
            val categories = transactions
                .groupingBy { it.category }
                .eachCount()
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
                )
                .take(3)
                .map { it.key }

            ShareCardData(
                transactionCount = trackedFromBankTexts(transactions),
                topCategories = categories,
                // Subscriptions are today's count, so a card about a chosen period can't
                // claim them. Deliberately not filtered by profile otherwise:
                // SubscriptionEntity carries no profileId and Home doesn't scope them
                // either, so filtering here would invent semantics the rest of the app
                // doesn't have.
                subscriptionCount = if (query.window != null) 0 else subscriptions.size,
                merchantCount = merchantsPaid(transactions),
                periodLabel = label,
            )
        }.onStart<ShareCardData?> { emit(null) }
    }

    /** "OCTOBER 2026", or the dates on a non-calendar cycle, where a month name would be wrong. */
    private fun cycleLabel(cycle: Pair<LocalDate, LocalDate>): String {
        val (start, end) = cycle
        return (
            if (isCalendarMonth(start, end)) start.format(AppDates.pattern("MMMM yyyy"))
            else DateRangeUtils.formatDateRange(start, end)
            ).uppercase(Locale.getDefault())
    }

    /**
     * Applies [update]. No emptiness guard is needed any more: with a single hero there is
     * no configuration that produces a blank card.
     */
    fun updateConfig(update: (ShareCardConfig) -> ShareCardConfig) {
        if (!configLoaded.value) preLoadEdits += update
        val candidate = update(_config.value)
        _config.value = candidate
        viewModelScope.launch { userPreferencesRepository.setShareCardConfig(candidate) }
    }

    /**
     * Points the card at a review's exact dates for this viewing only. Null means the
     * saved period.
     */
    fun setWindow(window: ShareWindow?) {
        this.window.value = window
    }
}
