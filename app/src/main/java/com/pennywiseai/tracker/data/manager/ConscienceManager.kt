package com.pennywiseai.tracker.data.manager

import android.content.Context
import android.util.Log
import com.pennywiseai.tracker.PennyWiseApplication
import com.pennywiseai.tracker.data.database.entity.builtinKey
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.conscience.ConscienceDetectors
import com.pennywiseai.tracker.domain.conscience.ConscienceEligibility
import com.pennywiseai.tracker.domain.conscience.ConscienceLines
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
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
    private val categoryRepository: CategoryRepository
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
        // This overload stops at 23:59:59, so run past today; the detectors drop anything after the purchase.
        val history = transactionRepository.getTransactionsWithSplitsFiltered(today.minusDays(HISTORY_DAYS), today.plusDays(1)).first()
            .filter { it.transaction.id != transactionId }
            .flatMap { ConscienceEligibility.historyOf(it, builtinKeys) }
        val ledgerStart = transactionRepository.getOldestTransactionDateTime()?.toLocalDate()
        val fact = ConscienceDetectors.detect(current, history, ledgerStart) ?: return

        // Read before the claim: nothing may suspend between spending a slot and posting.
        val voice = preferences.conscienceVoice.first()
        val address = preferences.conscienceAddress.first()
        val frequency = preferences.conscienceFrequency.first()
        val line = preferences.tryClaimConscienceLine(
            ConscienceLines.pool(voice, fact),
            nowMillis,
            LocalDate.now().toEpochDay(),
            Random.Default
        ) ?: return
        ConscienceNotifier.post(
            context,
            transactionId,
            voice,
            ConscienceLines.render(line, voice, address, fact),
            frequency
        )
    }

    companion object {
        private const val TAG = "ConscienceManager"
        private const val HISTORY_DAYS = 95L
        private const val FRESH_WINDOW_MILLIS = 10 * 60 * 1000L

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
