package com.pennywiseai.tracker.core.entitlement

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A Pro feature a free user can try once, for [FeatureTrials.TRIAL_DAYS] days. */
enum class TrialFeature {
    /** The Pro Analytics groupings (tag, day of week). */
    ANALYTICS_GROUPING,

    /** CSV export past [FreeTierLimits.MAX_CSV_EXPORT_ROWS_PER_MONTH] rows. */
    CSV_EXPORT,
}

sealed interface TrialStatus {
    data object NotStarted : TrialStatus

    /**
     * [daysLeft] counts a part day as a whole one, so a fresh trial reads 7 and the
     * final day (24 hours or less) reads 1, which gates show as less than a day.
     */
    data class Active(val daysLeft: Int) : TrialStatus

    data object Expired : TrialStatus
}

/**
 * Whether a gated feature is open: Pro, or its trial running. [isPro] is
 * `ProEntitlement.resolvedProEntitlement`: null while Pro is still loading.
 */
data class FeatureAccess(val isPro: Boolean?, val trial: TrialStatus) {
    val unlocked: Boolean get() = isPro == true || trial is TrialStatus.Active

    /**
     * The gate offers "Try free" only to a user known to be free who never tried this
     * feature, never while Pro is loading (a Pro user would burn the trial).
     */
    val canStartTrial: Boolean get() = isPro == false && trial == TrialStatus.NotStarted

    /** Days left to show, or null when there's no trial to show (Pro, or none running). */
    val trialDaysLeft: Int? get() = if (isPro == true) null else (trial as? TrialStatus.Active)?.daysLeft
}

/**
 * One 7-day trial per [TrialFeature], started by the user from the feature's gate.
 * The start time lives in its own device-only prefs file: no backup carries it and
 * starting again never restarts it, so each feature has exactly one trial per install.
 * Once a trial is seen ended it stays ended, and a clock behind the start or behind
 * the latest time that trial saw reads as expired, so winding the date back doesn't
 * reopen one. Each feature keeps its own start and latest time seen.
 */
@Singleton
class FeatureTrials internal constructor(
    private val prefs: SharedPreferences,
    private val now: () -> Long,
) {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), System::currentTimeMillis)

    /** Bumped on every start and [recheck], so open gates re-read. */
    private val version = MutableStateFlow(0)

    fun status(feature: TrialFeature): TrialStatus = evaluate(feature).first

    /** [feature]'s status now, and the ms until it next changes (null: not by itself). */
    @Synchronized
    private fun evaluate(feature: TrialFeature): Pair<TrialStatus, Long?> {
        val start = prefs.getLong(feature.name, NOT_STARTED)
        if (start == NOT_STARTED) return TrialStatus.NotStarted to null
        if (prefs.getBoolean(expiredKey(feature), false)) return TrialStatus.Expired to null
        val at = now()
        val latestSeen = prefs.getLong(latestSeenKey(feature), start)
        if (at < start || at < latestSeen) return TrialStatus.Expired to null
        val elapsed = at - start
        if (elapsed >= DURATION_MS) {
            prefs.edit().putBoolean(expiredKey(feature), true).putLong(latestSeenKey(feature), at).apply()
            return TrialStatus.Expired to null
        }
        if (at > latestSeen) prefs.edit().putLong(latestSeenKey(feature), at).apply()
        val left = DURATION_MS - elapsed
        // Days left rounds up, so it drops each time [left] reaches a whole day; the last
        // one is reached at the expiry instant.
        return TrialStatus.Active(((left + DAY_MS - 1) / DAY_MS).toInt()) to (left - 1) % DAY_MS + 1
    }

    fun isActive(feature: TrialFeature): Boolean = status(feature) is TrialStatus.Active

    /**
     * Starts [feature]'s trial unless one was ever started, or [isPro] (the resolved
     * entitlement, null while loading) isn't known to be `false`.
     */
    @Synchronized
    fun start(feature: TrialFeature, isPro: Boolean?) {
        if (isPro != false || prefs.contains(feature.name)) return
        prefs.edit().putLong(feature.name, now()).apply()
        recheck()
    }

    /**
     * Makes open gates re-read now, for a screen coming back: the timed re-check in
     * [access] runs on uptime, which stops while the device sleeps.
     */
    fun recheck() = version.update { it + 1 }

    /**
     * [feature]'s access, re-read whenever [isPro] (the resolved entitlement) changes, a
     * trial starts, [recheck] is called, and, while collected, when the days left drop
     * and the moment the trial ends, so an open gate locks again on time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun access(feature: TrialFeature, isPro: Flow<Boolean?>): Flow<FeatureAccess> =
        combine(isPro, version) { pro, _ -> pro }.transformLatest { pro ->
            while (true) {
                val (trial, changesIn) = evaluate(feature)
                emit(FeatureAccess(pro, trial))
                delay(changesIn ?: break)
            }
        }

    companion object {
        const val TRIAL_DAYS = 7
        private const val PREFS_NAME = "feature_trials"
        private const val NOT_STARTED = -1L

        private val DAY_MS = TimeUnit.DAYS.toMillis(1)
        private val DURATION_MS = TimeUnit.DAYS.toMillis(TRIAL_DAYS.toLong())

        /** Set once [feature]'s trial is seen ended, so no later clock reopens it. */
        private fun expiredKey(feature: TrialFeature) = "${feature.name}_expired"

        /**
         * The latest clock reading [feature]'s trial saw; a clock behind it was wound back.
         * Kept per feature, so one trial's history never moves another's start or end.
         */
        private fun latestSeenKey(feature: TrialFeature) = "${feature.name}_latest_seen_ms"
    }
}
