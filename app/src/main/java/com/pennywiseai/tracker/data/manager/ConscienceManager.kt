package com.pennywiseai.tracker.data.manager

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.pennywiseai.tracker.PennyWiseApplication
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.builtinKey
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.conscience.ConscienceDetectors
import com.pennywiseai.tracker.domain.conscience.ConscienceEligibility
import com.pennywiseai.tracker.domain.conscience.ConscienceRemark
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.random.Random

/**
 * Decides whether a transaction that just arrived earns a conscience line, and
 * posts it. Only the live SMS and notification paths call this: scans, imports
 * and manual adds describe purchases the user has already moved on from.
 */
@Singleton
class ConscienceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: CardRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore
) {
    /** Never throws (bar cancellation): a joke must not cost a saved transaction its alert. */
    suspend fun onLiveTransaction(transactionId: Long, eventTimeMillis: Long) {
        try {
            evaluate(transactionId, eventTimeMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The class only: a message could quote a row.
            Log.e(TAG, "Conscience check failed: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun evaluate(transactionId: Long, eventTimeMillis: Long) {
        if (!preferences.conscienceEnabled.first()) return
        if ((context.applicationContext as? PennyWiseApplication)?.isAppInForeground == true) return
        // Checked before a slot is claimed, so none is spent on something invisible.
        if (!ConscienceNotifier.canPost(context)) return

        val saved = transactionRepository.getTransactionById(transactionId) ?: return
        val nowMillis = System.currentTimeMillis()
        if (!isFresh(eventTimeMillis, saved.dateTime, nowMillis, ZoneId.systemDefault())) return

        val balances = accountBalanceRepository.getAllLatestBalances().first()
        val inScope = ConscienceEligibility.inScope(
            IgnoredAccountsStore.withLinkedCards(ignoredAccountsStore.keys(), cardRepository.getAllCards().first(), balances),
            buildProfileAccountKeys(balances)
        )
        if (!inScope(saved)) return

        val builtinKeys = categoryRepository.getAllCategories().first()
            .mapNotNull { category -> category.builtinKey?.let { category.name to it } }
            .toMap()
        val current = ConscienceEligibility.toPurchase(
            saved,
            builtinKeys,
            preferences.countCreditCardAsExpense.first(),
            transactionRepository.hasSplits(transactionId)
        ) ?: return
        val today = saved.dateTime.toLocalDate()
        // Late at night, reach back far enough to see two cycles of a quarterly or yearly renewal.
        val days = if (ConscienceDetectors.isLateNight(saved.dateTime.toLocalTime())) RENEWAL_HISTORY_DAYS else HISTORY_DAYS
        // This overload stops at 23:59:59, so run past today; the detectors drop anything after the purchase.
        val rows = transactionRepository.getTransactionsWithSplitsFiltered(today.minusDays(days), today.plusDays(1)).first()
            .filter { it.transaction.id != transactionId && inScope(it.transaction) }
        val history = rows.flatMap { ConscienceEligibility.historyOf(it, builtinKeys) }
        // Days with any spending at all: a quiet week only counts as a win when the ledger was still being fed.
        val activeDates = rows
            .filter { it.transaction.transactionType == TransactionType.EXPENSE || it.transaction.transactionType == TransactionType.CREDIT }
            .map { it.transaction.dateTime.toLocalDate() }
            .toSet() + today
        val ledgerStart = transactionRepository.getOldestTransactionDateTime()?.toLocalDate()
        val facts = ConscienceDetectors.detect(current, history, ledgerStart, activeDates)
        if (facts.isEmpty()) return

        val muted = preferences.conscienceMutedKinds.first()
        val address = preferences.conscienceAddress.first()
        val text = ConscienceText(context)
        val candidates = facts.filter { it.kind !in muted }.map { text.candidate(it, address) }
        // Late at night with the screen off the user is likely asleep: keep the remark, skip the notification.
        val quiet = isQuietHour(LocalTime.now()) && !isScreenOn()

        // The candidates were rendered up front, so rendering inside the claim can't fail on a bad line.
        val remark = preferences.tryClaimConscience(
            candidates,
            text.contentLocale,
            nowMillis,
            LocalDate.now().toEpochDay(),
            Random.Default
        ) { choice ->
            text.render(choice.fact, choice.line, address)?.let { line ->
                ConscienceRemark(
                    id = nowMillis,
                    atMillis = nowMillis,
                    text = line,
                    kind = choice.fact.kind,
                    lineKey = choice.lineKey,
                    notified = !quiet
                )
            }
        } ?: return
        if (remark.notified) ConscienceNotifier.post(context, text, remark.text, remark.id, transactionId)
    }

    private fun isScreenOn(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true

    companion object {
        private const val TAG = "ConscienceManager"
        private const val HISTORY_DAYS = 95L
        private const val RENEWAL_HISTORY_DAYS = 2 * 380L + 1
        private const val FRESH_WINDOW_MILLIS = 10 * 60 * 1000L
        private val QUIET_FROM: LocalTime = LocalTime.of(23, 0)
        private val QUIET_UNTIL: LocalTime = LocalTime.of(7, 0)

        internal fun isQuietHour(time: LocalTime): Boolean = !time.isBefore(QUIET_FROM) || time.isBefore(QUIET_UNTIL)

        /**
         * A new notification can describe an old purchase, so the event and the
         * saved row must both be within the window of now, either side.
         */
        internal fun isFresh(eventTimeMillis: Long, rowTime: LocalDateTime, nowMillis: Long, zone: ZoneId): Boolean {
            val rowMillis = rowTime.atZone(zone).toInstant().toEpochMilli()
            return abs(nowMillis - eventTimeMillis) <= FRESH_WINDOW_MILLIS &&
                abs(nowMillis - rowMillis) <= FRESH_WINDOW_MILLIS
        }
    }
}
