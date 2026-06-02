package com.pennywiseai.tracker.billing

import android.util.Log
import com.pennywiseai.tracker.data.database.dao.RuleDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-responsibility: decide whether the current install predates the Pro
 * launch and should keep all currently-Pro features free, forever. Knows
 * nothing about billing or entitlement composition — that lives in
 * [EntitlementGate].
 *
 * The check runs exactly once per device + Pro-release version (state is
 * persisted in DataStore via [UserPreferencesRepository.recordLegacyEvaluation]).
 * Subsequent launches read the cached verdict; no DAO hits on the hot path.
 *
 * Factory-reset wipes the DataStore → check runs again. That's the correct
 * semantic: a wiped device is indistinguishable from a fresh install and
 * Play Billing will still recognise any previously-purchased Pro
 * entitlement separately via the gateway.
 */
@Singleton
class LegacyUserDetector @Inject constructor(
    private val transactionDao: TransactionDao,
    private val ruleDao: RuleDao,
    private val preferences: UserPreferencesRepository,
) {

    /**
     * Observable verdict. Emits `false` until [ensureEvaluated] has run at
     * least once. After that, emits the persisted decision on every launch.
     */
    val isLegacyUser: Flow<Boolean> = preferences.proIsLegacyUser

    /**
     * Runs the one-shot check if not already evaluated, then persists the
     * verdict. Idempotent and cheap after the first call. Safe to invoke
     * on every cold start.
     */
    suspend fun ensureEvaluated() {
        if (preferences.proLegacyEvaluated.first()) return

        val txCount = transactionDao.getTransactionCount()
        val ruleCount = ruleDao.getRuleCount()
        val legacy = txCount > LEGACY_TX_THRESHOLD || ruleCount > 0

        preferences.recordLegacyEvaluation(legacy)

        Log.i(
            TAG,
            "Legacy-user check: txCount=$txCount ruleCount=$ruleCount → legacy=$legacy",
        )
    }

    companion object {
        private const val TAG = "LegacyUserDetector"

        // Anything above this is "definitely used the app pre-Pro." High
        // enough that a brand-new install which auto-scanned several SMS
        // messages on first launch (months of bank-SMS history) doesn't
        // get falsely grandfathered. Trade-off: a genuine pre-Pro user
        // with <10 transactions will see Pro gates. Acceptable — they're
        // not the segment using the Pro-gated power features anyway.
        const val LEGACY_TX_THRESHOLD = 10
    }
}
