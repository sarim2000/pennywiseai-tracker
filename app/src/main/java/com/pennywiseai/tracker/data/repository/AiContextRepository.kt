package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.ProfileDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.database.entity.expandWithChildren
import com.pennywiseai.tracker.data.ledger.breakdownByCurrency
import com.pennywiseai.tracker.data.ledger.convertedEstimate
import com.pennywiseai.tracker.data.model.CategorySpend
import com.pennywiseai.tracker.data.model.ChatScope
import com.pennywiseai.tracker.data.model.PeriodSpending
import com.pennywiseai.tracker.data.model.SpendingComparison
import com.pennywiseai.tracker.data.model.TopCategories
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.domain.model.effectiveProfileId
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The figures the AI chat reads, typed or one-tap. Each is worked out the way Home works out
 * "Spent this month": the budget cycle so far, the profile Home shows, the credit-card setting,
 * refunds netted and forgiven loans added (#938), per currency.
 */
@Singleton
class AiContextRepository @Inject constructor(
    private val transactionSplitDao: TransactionSplitDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val loanRepository: LoanRepository,
    private val categoryDao: CategoryDao,
    private val accountBalanceDao: AccountBalanceDao,
    private val profileDao: ProfileDao,
) {

    /** Home's period: this budget cycle up to the end of [today], in the profile Home shows. */
    private class Scope(val chat: ChatScope, private val profileId: Long?, private val accountKeys: Map<Long, Set<String>>) {
        val start: LocalDateTime get() = chat.window.start
        val end: LocalDateTime get() = chat.window.end

        /** As Home's profile filter: a transaction's own profile, else its account's, else Personal. */
        fun keeps(tx: TransactionEntity) = profileId == null || tx.effectiveProfileId(accountKeys) == profileId
    }

    private suspend fun homeScope(today: LocalDate): Scope {
        val (cycleStart, _) = BudgetCycle.currentCycle(today, userPreferencesRepository.budgetCycleStartDay.first())
        return scope(SpendingComparison.Window(cycleStart.atStartOfDay(), today.atTime(LocalTime.MAX)))
    }

    private suspend fun scope(window: SpendingComparison.Window): Scope {
        val profileId = userPreferencesRepository.selectedProfileId.first()
        val keys = if (profileId == null) emptyMap() else buildProfileAccountKeys(accountBalanceDao.getAllLatestBalancesOnce())
        val name = profileId?.let { profileDao.getById(it)?.name }
        return Scope(ChatScope(window, name), profileId, keys)
    }

    private suspend fun rows(scope: Scope): List<TransactionWithSplits> =
        transactionSplitDao.getTransactionsWithSplitsAllCurrencies(scope.start, scope.end).first()
            .filter { scope.keeps(it.transaction) }

    /** Lent balances forgiven in [scope], per currency, in its profile: Home adds these to "Spent" (#938). */
    private suspend fun forgiven(scope: Scope): Map<String, BigDecimal> = loanRepository.settlementLossByCurrency(
        loanRepository.getLentLoansSettledInPeriod(scope.start, scope.end).first(),
        scope::keeps
    )

    private suspend fun creditAsExpense() = userPreferencesRepository.countCreditCardAsExpense.first()

    /** Home's "Spent this month" and income, with one converted figure only when Home shows one. */
    suspend fun spending(today: LocalDate = LocalDate.now()): PeriodSpending {
        val scope = homeScope(today)
        val breakdown = breakdownByCurrency(rows(scope).map { it.transaction }, creditAsExpense())
        val result = PeriodSpending(
            scope = scope.chat,
            spent = breakdown.mapValues { it.value.expenses }.filterValues { it.signum() > 0 },
            income = breakdown.mapValues { it.value.income }.filterValues { it.signum() > 0 },
            forgiven = forgiven(scope),
        )
        val spent = result.spentWithForgiven
        val display = userPreferencesRepository.displayCurrency.first()
        if (!userPreferencesRepository.unifiedCurrencyMode.first() || spent.isEmpty() || spent.keys == setOf(display)) return result
        return result.copy(estimate = convertedEstimate(spent, display) { a, from, to -> currencyConversionService.convertAmountOrNull(a, from, to) })
    }

    /**
     * One category's spending in Home's period, for the chat's lookup tool (#170): its
     * subcategories and split shares in, refunds given back to it out. No currencies when
     * nothing in the period was spent in it.
     */
    suspend fun categorySpending(category: String, today: LocalDate = LocalDate.now()): CategorySpend {
        val scope = homeScope(today)
        val rows = rows(scope)
        val credit = creditAsExpense()
        val names = categoriesNamed(category)
        val byCurrency = SpendingComparison.spend(rows, names, credit)
        return CategorySpend(
            scope = scope.chat,
            category = category,
            byCurrency = byCurrency,
            transactionCount = SpendingComparison.transactionsIn(rows, names, credit),
            share = shareOfSpending(rows, credit, byCurrency),
        )
    }

    /** Each currency's biggest categories in Home's period, with forgiven loans beside them. */
    suspend fun topCategories(today: LocalDate = LocalDate.now()): TopCategories {
        val scope = homeScope(today)
        val rows = rows(scope)
        val credit = creditAsExpense()
        val ranking = SpendingComparison.topCategories(rows, creditAsExpense = credit)
        val top = ranking.mapValues { (_, cats) -> cats.first().second }
        return TopCategories(scope.chat, ranking, forgiven(scope), shareOfSpending(rows, credit, top))
    }

    /**
     * [SpendingComparison] on the saved transactions, as of now: months are Home's budget
     * cycles, in the profile Home shows. Also for one-tap questions that skip the model (M8).
     */
    suspend fun compareSpending(period: SpendingComparison.Period, category: String?, creditAsExpense: Boolean): SpendingComparison.Result {
        val windows = SpendingComparison.windows(
            period, LocalDateTime.now(), WeekFields.of(Locale.getDefault()).firstDayOfWeek,
            userPreferencesRepository.budgetCycleStartDay.first()
        )
        val (current, previous) = windows.toList().map { scope(it) }
        // Forgiven loan balances count as spent, as on Home (#938); a category comparison leaves them out.
        val (currentLoss, previousLoss) = if (category == null) forgiven(current) to forgiven(previous) else emptyMap<String, BigDecimal>() to emptyMap()
        return SpendingComparison.compare(windows, rows(current), rows(previous), category?.let { categoriesNamed(it) }, currentLoss, previousLoss, creditAsExpense)
    }

    /** A parent counts its children, as the category filters do. The model's casing isn't the saved name's. */
    private suspend fun categoriesNamed(asked: String): Set<String> {
        val all = categoryDao.getAllCategoriesList()
        return all.expandWithChildren(listOf(all.firstOrNull { it.name.equals(asked, ignoreCase = true) }?.name ?: asked))
    }

    /** [part]'s share of all spending, in the user's own currency when it has some, else the first. */
    private suspend fun shareOfSpending(rows: List<TransactionWithSplits>, credit: Boolean, part: Map<String, BigDecimal>): Float? {
        val base = userPreferencesRepository.baseCurrency.first()
        val currency = if (base in part) base else part.keys.minOrNull() ?: return null
        val total = SpendingComparison.spend(rows, creditAsExpense = credit)[currency]?.takeIf { it.signum() > 0 } ?: return null
        return (part.getValue(currency).toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }
}
