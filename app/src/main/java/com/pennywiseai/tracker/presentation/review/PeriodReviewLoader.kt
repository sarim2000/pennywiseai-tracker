package com.pennywiseai.tracker.presentation.review

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.database.entity.parentNameOf
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
) {
    fun observe(kind: ReviewKind, offset: Int): Flow<PeriodReview> = flow {
        val range = reviewRange(
            kind, offset, LocalDate.now(), userPreferencesRepository.getBudgetCycleStartDay(), localFirstDayOfWeek()
        )
        emitAll(
            combine(
                transactionRepository.getTransactionsWithSplitsFiltered(range.start, range.end),
                transactionRepository.getTransactionsWithSplitsFiltered(range.previousStart, range.previousEnd),
                ignoredAccountsStore.keysFlow,
                userPreferencesRepository.selectedProfileId,
                accountBalanceRepository.getAllLatestBalances(),
            ) { rows, previousRows, ignored, profileId, balances ->
                val keep = scopeOf(ignored, profileId, balances)
                buildPeriodReview(
                    range = range,
                    rows = rows.filter { keep(it.transaction) },
                    previousRows = previousRows.filter { keep(it.transaction) },
                    creditAsExpense = userPreferencesRepository.countCreditCardAsExpense.first(),
                    parentOf = categoryRepository.getAllCategories().first().parentNameOf(),
                    settlementLoss = settlementLoss(range.start, range.end, keep),
                    previousSettlementLoss = settlementLoss(range.previousStart, range.previousEnd, keep),
                ).let { review ->
                    // An empty month mustn't wall off the months before it. Only looks
                    // further back when the previous period didn't already answer it.
                    if (review.hasOlder) review
                    else review.copy(hasOlder = hasRowsBefore(range.previousStart, keep))
                }
            }
        )
    }

    /** Emits when what [rowCount] depends on may have changed. */
    fun rowCountChanges(): Flow<Unit> = combine(
        ignoredAccountsStore.keysFlow,
        userPreferencesRepository.selectedProfileId,
        accountBalanceRepository.getAllLatestBalances(),
        transactionRepository.observeActiveTransactionCount(),
    ) { _, _, _, _ -> }

    /** How many transactions a period holds, by the same scope the review itself uses. */
    suspend fun rowCount(start: LocalDate, end: LocalDate): Int {
        val keep = scopeOf(
            ignoredAccountsStore.keys(),
            userPreferencesRepository.selectedProfileId.first(),
            accountBalanceRepository.getAllLatestBalances().first(),
        )
        return transactionRepository.getTransactionsBetweenDates(start, end).first().count(keep)
    }

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
}
