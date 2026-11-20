package com.pennywiseai.tracker.domain.review

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.database.entity.parentNameOf
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.domain.model.filterTransactionsByProfile
import com.pennywiseai.tracker.utils.dateQueryWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/** The week starts when it does where the user lives: Saturday in Jordan, Monday in India. */
internal fun localFirstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

/** Loads a [PeriodReview] over the same rows as Home: the selected profile, no ignored accounts. */
class PeriodReviewLoader @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val loanRepository: LoanRepository,
    private val categoryRepository: CategoryRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val cardRepository: CardRepository,
) {
    /**
     * [detail] adds [PeriodReview.detail] for a cycle: the [DETAIL_CYCLES] cycles ending
     * with it. Weeks and years don't carry it. [profileIds] pins the profile, for a caller
     * that pairs the review with other data of one profile; by default it follows the
     * selected one.
     */
    fun observe(
        kind: ReviewKind,
        offset: Int,
        today: LocalDate = LocalDate.now(),
        detail: Boolean = false,
        profileIds: Flow<Long?>? = null,
    ): Flow<PeriodReview> = flow {
        val cycleStartDay = userPreferencesRepository.getBudgetCycleStartDay()
        val range = reviewRange(kind, offset, today, cycleStartDay, localFirstDayOfWeek())
        val cycles = if (detail && kind == ReviewKind.MONTH) cyclesEndingWith(range, cycleStartDay, DETAIL_CYCLES) else null
        emitAll(
            combine(
                rowsIn(range.start, range.end),
                rowsIn(range.previousStart, range.previousEnd),
                ignoredKeys(),
                profileIds ?: userPreferencesRepository.selectedProfileId,
                accountBalanceRepository.getAllLatestBalances(),
            ) { rows, previousRows, ignored, profileId, balances ->
                val keep = scopeOf(ignored, profileId, balances)
                val creditAsExpense = userPreferencesRepository.countCreditCardAsExpense.first()
                val parentOf = categoryRepository.getAllCategories().first().parentNameOf()
                buildPeriodReview(
                    range = range,
                    rows = rows.filter { keep(it.transaction) },
                    previousRows = previousRows.filter { keep(it.transaction) },
                    creditAsExpense = creditAsExpense,
                    parentOf = parentOf,
                    settlementLoss = settlementLoss(range.start, range.end, keep),
                    previousSettlementLoss = settlementLoss(range.previousStart, range.previousEnd, keep),
                ).let { review ->
                    // An empty month mustn't wall off the months before it. Only looks
                    // further back when the previous period didn't already answer it.
                    if (review.hasOlder) review
                    else review.copy(hasOlder = hasRowsBefore(range.start, keep))
                }.let { review ->
                    // Read on each emission: any write to the table re-runs the queries above.
                    if (cycles == null) return@let review
                    review.copy(
                        detail = buildPeriodDetail(
                            cycles = cycles,
                            rows = rowsIn(cycles.first().first, range.end).first().filter { keep(it.transaction) },
                            creditAsExpense = creditAsExpense,
                            parentOf = parentOf,
                            settlementLoss = cycles.map { (start, end) -> settlementLoss(start, end, keep) },
                        )
                    )
                }
            }
        )
    }

    /**
     * Emits when what [rowCount] depends on may have changed. DataStore re-emits on any
     * write, and marking a banner handled mustn't count as a change.
     */
    fun rowCountChanges(): Flow<Unit> = combine(
        ignoredKeys(),
        userPreferencesRepository.selectedProfileId.distinctUntilChanged(),
        accountBalanceRepository.getAllLatestBalances(),
        transactionRepository.observeActiveTransactionCount(),
    ) { _, _, _, _ -> }

    /** How many transactions a period holds, by the same scope the review itself uses. */
    suspend fun rowCount(start: LocalDate, end: LocalDate): Int {
        val keep = currentScope()
        return rowsIn(start, end).first().count { keep(it.transaction) }
    }

    /** Rows dated [start]..[end] in the review's scope: the selected profile, no ignored accounts. */
    fun scopedRows(start: LocalDate, end: LocalDate): Flow<List<TransactionWithSplits>> = combine(
        rowsIn(start, end),
        ignoredKeys(),
        userPreferencesRepository.selectedProfileId,
        accountBalanceRepository.getAllLatestBalances(),
    ) { rows, ignored, profileId, balances ->
        val keep = scopeOf(ignored, profileId, balances)
        rows.filter { keep(it.transaction) }
    }

    /** The day of every transaction in scope, oldest first. */
    suspend fun recordedDates(today: LocalDate = LocalDate.now()): List<LocalDate> {
        val keep = currentScope()
        // Every active row, however old an import goes; cut at today on the parsed date
        // rather than in SQL: see [dateQueryWindow].
        return transactionRepository.getAllTransactions().first()
            .filter(keep).map { it.dateTime.toLocalDate() }.filter { !it.isAfter(today) }.sorted()
    }

    private suspend fun currentScope(): (TransactionEntity) -> Boolean = scopeOf(
        ignoredKeys().first(),
        userPreferencesRepository.selectedProfileId.first(),
        accountBalanceRepository.getAllLatestBalances().first(),
    )

    /** Asks SQL for a wider window and filters to the exact dates: see [dateQueryWindow]. */
    private fun rowsIn(start: LocalDate, end: LocalDate): Flow<List<TransactionWithSplits>> {
        val from = start.atStartOfDay()
        val to = end.atTime(LocalTime.MAX)
        val (queryStart, queryEnd) = dateQueryWindow(from, to)
        return transactionRepository
            .getTransactionsWithSplitsFiltered(queryStart.toLocalDate(), queryEnd.toLocalDate())
            .map { rows -> rows.filter { it.transaction.dateTime in from..to } }
    }

    /** The ignored account keys, with the cards linked to an ignored account, as every review row is scoped. */
    fun ignoredKeys(): Flow<Set<String>> = combine(
        ignoredAccountsStore.keysFlow,
        cardRepository.getAllCards(),
        accountBalanceRepository.getAllLatestBalances(),
        IgnoredAccountsStore.Companion::withLinkedCards,
    )

    private suspend fun hasRowsBefore(date: LocalDate, keep: (TransactionEntity) -> Boolean): Boolean =
        transactionRepository.getTransactionsBetweenDates(LocalDate.of(2000, 1, 1), date.minusDays(1)).first().any(keep)

    private fun scopeOf(
        ignored: Set<String>,
        profileId: Long?,
        balances: List<AccountBalanceEntity>,
    ): (TransactionEntity) -> Boolean {
        val profileKeys = buildProfileAccountKeys(balances)
        return { tx ->
            !IgnoredAccountsStore.isIgnored(ignored, tx.bankName, tx.accountNumber) &&
                filterTransactionsByProfile(listOf(tx), profileId, profileKeys).isNotEmpty()
        }
    }

    private suspend fun settlementLoss(
        start: LocalDate,
        end: LocalDate,
        keep: (TransactionEntity) -> Boolean,
    ): Map<String, BigDecimal> = loanRepository.settlementLossByCurrency(
        loanRepository.getLentLoansSettledInPeriod(start.atStartOfDay(), end.atTime(LocalTime.MAX)).first(),
        keep,
    )

    private companion object {
        /** This cycle and the five before it: a half-year trend, with a 3-cycle baseline inside it. */
        const val DETAIL_CYCLES = 6
    }
}
