package com.pennywiseai.tracker.core.entitlement

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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

    /** [daysLeft] counts a part day as a whole one, so a fresh trial reads 7. */
    data class Active(val daysLeft: Int) : TrialStatus

    data object Expired : TrialStatus
}

/** Whether a gated feature is open: Pro, or its trial running. */
data class FeatureAccess(val isPro: Boolean, val trial: TrialStatus) {
    val unlocked: Boolean get() = isPro || trial is TrialStatus.Active

    /** The gate offers "Try free" only to a free user who never tried this feature. */
    val canStartTrial: Boolean get() = !isPro && trial == TrialStatus.NotStarted

    /** Days left to show, or null when there's no trial to show (Pro, or none running). */
    val trialDaysLeft: Int? get() = if (isPro) null else (trial as? TrialStatus.Active)?.daysLeft
}

/**
 * One 7-day trial per [TrialFeature], started by the user from the feature's gate.
 * The start time lives in its own device-only prefs file: no backup carries it and
 * starting again never restarts it, so each feature has exactly one trial per install.
 * Once a trial is seen ended it stays ended, and a clock behind the start or behind
 * the latest time seen reads as expired, so winding the date back doesn't reopen one.
 */
@Singleton
class FeatureTrials internal constructor(
    private val prefs: SharedPreferences,
    private val now: () -> Long,
) {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), System::currentTimeMillis)

    /** Bumped on every start, so open gates re-read. */
    private val version = MutableStateFlow(0)

    @Synchronized
    fun status(feature: TrialFeature): TrialStatus {
        val start = prefs.getLong(feature.name, NOT_STARTED)
        if (start == NOT_STARTED) return TrialStatus.NotStarted
        if (prefs.getBoolean(expiredKey(feature), false)) return TrialStatus.Expired
        val at = now()
        val latestSeen = prefs.getLong(LATEST_SEEN, start)
        if (at < start || at < latestSeen) return TrialStatus.Expired
        val elapsed = at - start
        if (elapsed >= DURATION_MS) {
            prefs.edit().putBoolean(expiredKey(feature), true).putLong(LATEST_SEEN, at).apply()
            return TrialStatus.Expired
        }
        if (at > latestSeen) prefs.edit().putLong(LATEST_SEEN, at).apply()
        val left = DURATION_MS - elapsed
        return TrialStatus.Active(((left + DAY_MS - 1) / DAY_MS).toInt())
    }

    fun isActive(feature: TrialFeature): Boolean = status(feature) is TrialStatus.Active

    /** Starts [feature]'s trial unless one was ever started. */
    @Synchronized
    fun start(feature: TrialFeature) {
        if (prefs.contains(feature.name)) return
        prefs.edit().putLong(feature.name, now()).apply()
        version.value++
    }

    /** [feature]'s access, re-read whenever [isPro] changes or a trial starts. */
    fun access(feature: TrialFeature, isPro: Flow<Boolean>): Flow<FeatureAccess> =
        combine(isPro, version) { pro, _ -> FeatureAccess(pro, status(feature)) }

    companion object {
        const val TRIAL_DAYS = 7
        private const val PREFS_NAME = "feature_trials"
        private const val NOT_STARTED = -1L

        /** The latest clock reading any trial saw; a clock behind it was wound back. */
        private const val LATEST_SEEN = "latest_seen_ms"

        /** Set once [feature]'s trial is seen ended, so no later clock reopens it. */
        private fun expiredKey(feature: TrialFeature) = "${feature.name}_expired"
        private val DAY_MS = TimeUnit.DAYS.toMillis(1)
        private val DURATION_MS = TimeUnit.DAYS.toMillis(TRIAL_DAYS.toLong())
    }
}
