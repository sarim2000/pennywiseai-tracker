package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.R
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.nameForBuiltin
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.utils.forTotals
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@HiltWorker
class RecentTransactionsWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val loanRepository: LoanRepository,
    private val categoryRepository: CategoryRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "recent_transactions_widget_update"
        private const val WORK_NAME_PERIODIC = "recent_transactions_widget_update_periodic"
        private const val MAX_ITEMS = 10

        fun resolveTargetCurrency(isUnified: Boolean, displayCurrency: String, baseCurrency: String): String {
            return if (isUnified) displayCurrency else baseCurrency
        }

        /** A row's "when": the time today, "Yesterday", else a short date. */
        fun whenLabel(
            at: LocalDateTime,
            today: LocalDate,
            yesterday: String,
            time: DateTimeFormatter,
            date: DateTimeFormatter
        ): String = when (at.toLocalDate()) {
            today -> at.format(time)
            today.minusDays(1) -> yesterday
            else -> at.format(date)
        }

        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<RecentTransactionsWidgetUpdateWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun enqueuePeriodicUpdate(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecentTransactionsWidgetUpdateWorker>(
                30, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val isUnifiedMode = userPreferencesRepository.unifiedCurrencyMode.first()
            val displayCurrency = userPreferencesRepository.displayCurrency.first()
            val baseCurrency = userPreferencesRepository.baseCurrency.first()
            val targetCurrency = resolveTargetCurrency(isUnifiedMode, displayCurrency, baseCurrency)

            val now = LocalDate.now()
            val startDay = userPreferencesRepository.getBudgetCycleStartDay()
            val (cycleStart, _) = BudgetCycle.currentCycle(now, startDay)

            // Scope to the selected profile and drop ignored accounts, like
            // the pie widget and the Transactions list.
            val ignored = ignoredAccountsStore.keys()
            val profileId = userPreferencesRepository.selectedProfileId.first()
            val profileKeys = buildProfileAccountKeys(accountBalanceRepository.getAllLatestBalances().first())
            fun counted(rows: List<com.pennywiseai.tracker.data.database.entity.TransactionEntity>) = filterTransactionsByProfile(
                rows.filterNot { IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountNumber) },
                profileId,
                profileKeys
            )
            // Through end of today, like Home: an SMS can carry a timestamp a
            // few seconds ahead of the device clock, and a `now` cutoff would
            // drop it from the widget right after it arrives.
            val allTransactions = counted(transactionRepository.getTransactionsBetweenDates(cycleStart, now).first())

            // Loan disbursements/repayments are tracked in the Loans feature, not
            // spending — exclude them, matching Home/Analytics. Analytics-excluded
            // transactions are opted out of every spend figure by the user, so they
            // must not count toward the widget's spend total either.
            val nonLoan = allTransactions.forTotals()
            // Null = no rate for this pair; the spend folds skip it rather than
            // counting a face-value foreign amount (#670).
            suspend fun inTarget(tx: com.pennywiseai.tracker.data.database.entity.TransactionEntity): BigDecimal? =
                if (!tx.currency.equals(targetCurrency, ignoreCase = true)) {
                    currencyConversionService.convertAmountOrNull(tx.amount, tx.currency, targetCurrency)
                } else {
                    tx.amount
                }

            // Same spend definition as Home: card purchases only when the user
            // opted in (#705), investments never.
            val creditAsExpense = userPreferencesRepository.countCreditCardAsExpense.first()
            suspend fun spentIn(rows: List<com.pennywiseai.tracker.data.database.entity.TransactionEntity>): BigDecimal {
                val grossSpent = rows
                    .filter {
                        it.transactionType == TransactionType.EXPENSE ||
                            (creditAsExpense && it.transactionType == TransactionType.CREDIT)
                    }
                    .fold(BigDecimal.ZERO) { acc, tx -> inTarget(tx)?.let { acc + it } ?: acc }

                // A "Refund" (INCOME + DEDUCT_SPENT) reverses a previous expense, so it
                // shrinks the spend total (floored at zero) — matching the Home card and
                // the Transactions page, which were already net of refunds.
                val refundTotal = rows
                    .filter { it.transactionType == TransactionType.INCOME && it.budgetImpactType == BudgetImpactType.DEDUCT_SPENT }
                    .fold(BigDecimal.ZERO) { acc, tx -> inTarget(tx)?.let { acc + it } ?: acc }

                return (grossSpent - refundTotal).coerceAtLeast(BigDecimal.ZERO)
            }

            // Lent loans forgiven in the window are spent, as on Home (#938).
            suspend fun lossSince(from: LocalDate): BigDecimal =
                loanRepository.settlementLossByCurrency(
                    loanRepository.getLentLoansSettledInPeriod(from.atStartOfDay(), now.atTime(LocalTime.MAX)).first()
                ) { counted(listOf(it)).isNotEmpty() }
                    .entries.fold(BigDecimal.ZERO) { acc, (currency, loss) ->
                        val inTargetCurrency = if (currency.equals(targetCurrency, ignoreCase = true)) loss
                        else currencyConversionService.convertAmountOrNull(loss, currency, targetCurrency)
                        inTargetCurrency?.let { acc + it } ?: acc
                    }

            val totalSpent = spentIn(nonLoan) + lossSince(cycleStart)
            val todaySpent = spentIn(nonLoan.filter { it.dateTime.toLocalDate() == now }) + lossSince(now)

            // The app's language, not the process's: an Arabic app on an
            // English phone got "Oct 12" and an English fallback title.
            val ctx = applicationContext.inAppLanguage()
            val locale = ctx.appLocale
            val timeFormat = DateTimeFormatter.ofPattern(
                DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(ctx)) "Hm" else "hma"),
                locale
            )
            val dateFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
            val yesterday = ctx.getString(R.string.txn_list_group_yesterday)
            // From the table, not CategoryMapping.userStyles / BuiltinCategoryNames:
            // a background process may not have mirrored them yet.
            val categories = categoryRepository.getAllCategories().first()
            val categoryByName = categories.associateBy { it.name }
            val unsorted = categories.nameForBuiltin("Others")

            val recentItems = allTransactions
                .take(MAX_ITEMS)
                .map { tx ->
                    // If a foreign txn has no rate, show it in its native currency
                    // rather than mislabeling a face value as targetCurrency (#670).
                    val converted = if (!tx.currency.equals(targetCurrency, ignoreCase = true)) {
                        currencyConversionService.convertAmountOrNull(tx.amount, tx.currency, targetCurrency)
                    } else {
                        tx.amount
                    }
                    val amount = converted ?: tx.amount
                    val itemCurrency = if (converted != null) targetCurrency else tx.currency
                    val title = tx.merchantName.takeIf { it.isNotBlank() }
                        ?: tx.description?.takeIf { it.isNotBlank() }
                        ?: ctx.getString(R.string.widget_recent_fallback_title)
                    val whenText = whenLabel(tx.dateTime, now, yesterday, timeFormat, dateFormat)
                    val meta = listOf(tx.category, whenText).filter { it.isNotBlank() }.joinToString(" · ")
                    val category = categoryByName[tx.category]
                    val badge = category?.icon?.takeIf { it.isNotBlank() }
                        ?: tx.category.trim().takeIf { it.isNotEmpty() }
                            ?.let { String(Character.toChars(it.codePointAt(0))).uppercase(locale) }
                        ?: ""

                    RecentTransactionItem(
                        title = title,
                        // WidgetLabel sets the line's direction, so an English category still reads right to left.
                        subtitle = meta,
                        amount = amount,
                        currency = itemCurrency,
                        transactionType = tx.transactionType,
                        transactionId = tx.id,
                        needsCategory = tx.category == unsorted,
                        badge = badge,
                        colorArgb = CategoryMapping.colorFor(tx.category, category?.color).toArgb().toLong() and 0xFFFFFFFFL
                    )
                }

            val data = RecentTransactionsWidgetData(
                totalSpent = totalSpent,
                todaySpent = todaySpent,
                currency = targetCurrency,
                transactions = recentItems
            )

            RecentTransactionsWidgetDataStore.update(applicationContext, data)
            RecentTransactionsWidget().updateAll(applicationContext)
            SpendingSummaryWidget().updateAll(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
