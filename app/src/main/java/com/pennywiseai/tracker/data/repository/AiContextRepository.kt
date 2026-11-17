package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.CardDao
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.ProfileDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.database.entity.expandWithChildren
import com.pennywiseai.tracker.data.ledger.breakdownByCurrency
import com.pennywiseai.tracker.data.ledger.convertedSpent
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.data.model.CategorySpend
import com.pennywiseai.tracker.data.model.ChatScope
import com.pennywiseai.tracker.data.model.PeriodSpending
import com.pennywiseai.tracker.data.model.SpendingComparison
import com.pennywiseai.tracker.data.model.TopCategories
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.domain.model.isTracked
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
 * "Spent this month": the budget cycle so far, the profile Home shows without ignored accounts
 * (#826), the credit-card setting, refunds netted and forgiven loans added (#938), per currency.
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
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val cardDao: CardDao,
) {

    /** Home's period: this budget cycle up to the end of [today], in the profile Home shows. */
    private class Scope(
        val chat: ChatScope,
        private val profileId: Long?,
        private val ignoredKeys: Set<String>,
        private val accountKeys: Map<Long, Set<String>>,
    ) {
        val start: LocalDateTime get() = chat.window.start
        val end: LocalDateTime get() = chat.window.end

        /** As Home and the Transactions list: the profile shown, no ignored account's rows (#826). */
        fun keeps(tx: TransactionEntity) = tx.isTracked(profileId, ignoredKeys, accountKeys)
    }

    /**
     * The profile Home shows ([id] null for all), read once per answer: switching profiles while
     * an answer reads the database leaves its rows, its label and its link on the same one.
     */
    private class Shown(val id: Long?, val profile: ProfileEntity?)

    private suspend fun shown(): Shown {
        val id = userPreferencesRepository.selectedProfileId.first()
        return Shown(id, id?.let { profileDao.getById(it) })
    }

    private suspend fun homeScope(today: LocalDate): Scope {
        val shown = shown()
        val (cycleStart, _) = BudgetCycle.currentCycle(today, userPreferencesRepository.budgetCycleStartDay.first())
        return scope(SpendingComparison.Window(cycleStart.atStartOfDay(), today.atTime(LocalTime.MAX)), shown)
    }

    private suspend fun scope(window: SpendingComparison.Window, shown: Shown): Scope {
        val balances = accountBalanceDao.getAllLatestBalancesOnce()
        val ignored = IgnoredAccountsStore.withLinkedCards(ignoredAccountsStore.keys(), cardDao.getAllCards().first(), balances)
        return Scope(ChatScope(window, shown.profile), shown.id, ignored, buildProfileAccountKeys(balances))
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
        val transactions = rows(scope).map { it.transaction }
        val credit = creditAsExpense()
        val breakdown = breakdownByCurrency(transactions, credit)
        val result = PeriodSpending(
            scope = scope.chat,
            spent = breakdown.mapValues { it.value.expenses }.filterValues { it.signum() > 0 },
            income = breakdown.mapValues { it.value.income }.filterValues { it.signum() > 0 },
            forgiven = forgiven(scope),
            // The rows the breakdown adds up: not transfers or investments, card purchases only when counted.
            transactionCount = transactions.forTotals().count {
                it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.EXPENSE ||
                    (credit && it.transactionType == TransactionType.CREDIT)
            },
        )
        val spent = result.spentWithForgiven
        val display = userPreferencesRepository.displayCurrency.first()
        if (!userPreferencesRepository.unifiedCurrencyMode.first() || spent.isEmpty() || spent.keys == setOf(display)) return result
        val estimate = convertedSpent(result.spent, result.forgiven, display) { a, from, to ->
            currencyConversionService.convertAmountOrNull(a, from, to)
        }
        // Nothing converted is no estimate: "about ₹0 in all" would be a false figure.
        return result.copy(estimate = estimate.takeIf { it.leftOut.size < spent.size })
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
        // A currency refunded in full is nothing spent, as spending() leaves it out.
        val byCurrency = SpendingComparison.spend(rows, names, credit).filterValues { it.signum() > 0 }
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
     * [SpendingComparison] on the saved transactions, up to the end of [today] as Home counts:
     * months are Home's budget cycles, in the profile Home shows, which the result names. Also
     * for one-tap questions that skip the model (M8).
     */
    suspend fun compareSpending(
        period: SpendingComparison.Period,
        category: String?,
        creditAsExpense: Boolean,
        today: LocalDate = LocalDate.now(),
    ): SpendingComparison.Result {
        val shown = shown()
        val windows = SpendingComparison.windows(
            period, today.atTime(LocalTime.MAX), WeekFields.of(Locale.getDefault()).firstDayOfWeek,
            userPreferencesRepository.budgetCycleStartDay.first()
        )
        val (current, previous) = windows.toList().map { scope(it, shown) }
        // Forgiven loan balances count as spent, as on Home (#938); a category comparison leaves them out.
        val (currentLoss, previousLoss) = if (category == null) forgiven(current) to forgiven(previous) else emptyMap<String, BigDecimal>() to emptyMap()
        return when (val result = SpendingComparison.compare(windows, rows(current), rows(previous), category?.let { categoriesNamed(it) }, currentLoss, previousLoss, creditAsExpense)) {
            is SpendingComparison.Result.Rows -> result.copy(profile = shown.profile)
            is SpendingComparison.Result.NoData -> result.copy(profile = shown.profile)
        }
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
