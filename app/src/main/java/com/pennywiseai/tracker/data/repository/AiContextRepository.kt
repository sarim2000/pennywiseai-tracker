package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.CardDao
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.MerchantAliasDao
import com.pennywiseai.tracker.data.database.dao.ProfileDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
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
import com.pennywiseai.tracker.data.model.SpendingFilter
import com.pennywiseai.tracker.data.model.SpendingQuery
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
 * (#826), the credit-card setting (for all spending; see [creditAsExpense]), refunds netted and
 * forgiven loans added (#938), per currency.
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
    private val merchantAliasDao: MerchantAliasDao,
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

    /** [filter]'s days, or Home's period when it names none. */
    private suspend fun scopeOf(filter: SpendingFilter, today: LocalDate) = filter.window?.let { scope(it, shown()) } ?: homeScope(today)

    private suspend fun rows(scope: Scope, filter: SpendingFilter = SpendingFilter()): List<TransactionWithSplits> {
        val aliases = if (filter.merchant == null) emptyMap() else aliases()
        return transactionSplitDao.getTransactionsWithSplitsAllCurrencies(scope.start, scope.end).first()
            .filter { scope.keeps(it.transaction) && filter.keeps(it.transaction, aliases) }
    }

    private suspend fun aliases(): Map<String, String> = merchantAliasDao.getAllAliasesList().associate { it.merchantName to it.alias }

    /** Lent balances forgiven in [scope], per currency, in its profile: Home adds these to "Spent" (#938). */
    private suspend fun forgiven(scope: Scope): Map<String, BigDecimal> = loanRepository.settlementLossByCurrency(
        loanRepository.getLentLoansSettledInPeriod(scope.start, scope.end).first(),
        scope::keeps
    )

    private suspend fun creditAsExpense() = userPreferencesRepository.countCreditCardAsExpense.first()

    /**
     * Home's card setting, for a figure of all spending: left off, a card's purchases wait for
     * its bill, paid from another account, and counting both would count them twice. A figure
     * narrowed to a merchant (or a ranking of accounts) is that merchant's or account's own: the
     * bill is paid elsewhere, so left off, a card would read "nothing spent" beside a list full
     * of its purchases.
     */
    private suspend fun creditAsExpense(filter: SpendingFilter, byAccount: Boolean = false) =
        filter.narrowed || byAccount || creditAsExpense()

    /**
     * Home's "Spent this month" and income, with one converted figure only when Home shows one.
     * [filter] picks other days, or narrows it to a merchant: forgiven loans belong to none, so
     * they're only in a figure of all spending.
     */
    suspend fun spending(today: LocalDate = LocalDate.now(), filter: SpendingFilter = SpendingFilter()): PeriodSpending {
        val scope = scopeOf(filter, today)
        val rows = rows(scope, filter)
        val transactions = rows.map { it.transaction }
        val credit = creditAsExpense(filter)
        val breakdown = breakdownByCurrency(transactions, credit)
        val result = PeriodSpending(
            scope = scope.chat,
            spent = breakdown.mapValues { it.value.expenses }.filterValues { it.signum() > 0 },
            income = breakdown.mapValues { it.value.income }.filterValues { it.signum() > 0 },
            forgiven = if (filter.narrowed) emptyMap() else forgiven(scope),
            // The rows the breakdown adds up: not transfers or investments, card purchases only when counted.
            transactionCount = transactions.forTotals().count {
                it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.EXPENSE ||
                    (credit && it.transactionType == TransactionType.CREDIT)
            },
            cardPurchases = SpendingComparison.countsCardPurchases(rows, creditAsExpense = credit),
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
     * One category's spending in Home's period (or [filter]'s), for the chat's lookup tool
     * (#170): its subcategories and split shares in, refunds given back to it out. No
     * currencies when nothing in the period was spent in it.
     */
    suspend fun categorySpending(category: String, today: LocalDate = LocalDate.now(), filter: SpendingFilter = SpendingFilter()): CategorySpend {
        val scope = scopeOf(filter, today)
        val rows = rows(scope, filter)
        val credit = creditAsExpense(filter)
        val names = categoriesNamed(category, rows)
        // A currency refunded in full is nothing spent, as spending() leaves it out.
        val byCurrency = SpendingComparison.spend(rows, names, credit).filterValues { it.signum() > 0 }
        return CategorySpend(
            scope = scope.chat,
            category = category,
            byCurrency = byCurrency,
            transactionCount = SpendingComparison.transactionsIn(rows, names, credit),
            share = shareOfSpending(rows, credit, byCurrency),
            refunded = SpendingComparison.refundedOutside(rows, names, credit).filterKeys { it in byCurrency },
            cardPurchases = SpendingComparison.countsCardPurchases(rows, names, credit),
        )
    }

    /**
     * What came in under one category (Salary) in Home's period or [filter]'s, per currency, as
     * Home's income counts it: [categorySpending] counts only what went out.
     */
    suspend fun categoryIncome(category: String, today: LocalDate = LocalDate.now(), filter: SpendingFilter = SpendingFilter()): CategorySpend {
        val scope = scopeOf(filter, today)
        val rows = rows(scope, filter)
        val (byCurrency, count) = SpendingComparison.received(rows, categoriesNamed(category, rows))
        return CategorySpend(scope = scope.chat, category = category, byCurrency = byCurrency, transactionCount = count)
    }

    /**
     * Each currency's biggest categories in Home's period, with forgiven loans beside them. Or,
     * per [by], its biggest merchants (named as the user aliased them) or accounts, in
     * [filter]'s days and rows; with [category], only that category's part of each. Forgiven
     * loans are no category, merchant or account: only a ranking of all categories lists them.
     */
    suspend fun topCategories(
        today: LocalDate = LocalDate.now(),
        filter: SpendingFilter = SpendingFilter(),
        by: SpendingQuery.GroupBy = SpendingQuery.GroupBy.CATEGORY,
        category: String? = null,
    ): TopCategories {
        val scope = scopeOf(filter, today)
        val rows = rows(scope, filter)
        val credit = creditAsExpense(filter, byAccount = by == SpendingQuery.GroupBy.ACCOUNT)
        val key: ((TransactionEntity) -> String)? = when (by) {
            SpendingQuery.GroupBy.MERCHANT -> aliases().let { aliases -> { tx: TransactionEntity -> aliases[tx.merchantName] ?: tx.merchantName } }
            SpendingQuery.GroupBy.ACCOUNT -> accountBalanceDao.getAllLatestBalancesOnce().associateBy { SpendingFilter.accountKey(it) }.let { accounts ->
                { tx: TransactionEntity -> accounts[SpendingFilter.accountKey(tx)]?.displayLabel ?: tx.bankName?.let { AccountBalanceEntity.accountLabel(it, tx.accountNumber) } ?: NO_ACCOUNT }
            }
            else -> null
        }
        val names = category?.let { categoriesNamed(it, rows) }
        val ranking = SpendingComparison.ranking(rows, key, names, creditAsExpense = credit)
        val top = ranking.mapValues { (_, cats) -> cats.first().second }
        val forgiven = if (key == null && category == null && !filter.narrowed) forgiven(scope) else emptyMap()
        return TopCategories(scope.chat, ranking, forgiven, shareOfSpending(rows, credit, top), SpendingComparison.countsCardPurchases(rows, names, credit))
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
        val (rowsNow, rowsBefore) = rows(current) to rows(previous)
        val names = category?.let { categoriesNamed(it, rowsNow + rowsBefore) }
        return when (val result = SpendingComparison.compare(windows, rowsNow, rowsBefore, names, currentLoss, previousLoss, creditAsExpense)) {
            is SpendingComparison.Result.Rows -> result.copy(profile = shown.profile)
            is SpendingComparison.Result.NoData -> result.copy(profile = shown.profile)
        }
    }

    /**
     * The category names a figure for [asked] counts, matched against [rows] exactly. A saved
     * category (the model's casing isn't the saved name's) counts its children, as the category
     * filters do, and only the rows spelled as saved: the Transactions list a figure links to and
     * Analytics match exactly, so a CSV row filed under "food" isn't "Food" there either. A name
     * no category is saved under (one deleted while rows still carry it) has no link, so it
     * takes every spelling of it in [rows].
     */
    private suspend fun categoriesNamed(asked: String, rows: List<TransactionWithSplits>): Set<String> {
        val all = categoryDao.getAllCategoriesList()
        val saved = all.firstOrNull { it.name.equals(asked, ignoreCase = true) }?.name
            ?: return SpendingComparison.categoryNames(rows).filterTo(mutableSetOf(asked)) { it.equals(asked, ignoreCase = true) }
        return all.expandWithChildren(listOf(saved))
    }

    companion object {
        /** A ranking's name for spending on no account (cash typed in without one); the screen words it. */
        const val NO_ACCOUNT = ""
    }

    /** [part]'s share of all spending, in the user's own currency when it has some, else the first. */
    private suspend fun shareOfSpending(rows: List<TransactionWithSplits>, credit: Boolean, part: Map<String, BigDecimal>): Float? {
        val base = userPreferencesRepository.baseCurrency.first()
        val currency = if (base in part) base else part.keys.minOrNull() ?: return null
        val total = SpendingComparison.spend(rows, creditAsExpense = credit)[currency]?.takeIf { it.signum() > 0 } ?: return null
        return (part.getValue(currency).toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }
}
