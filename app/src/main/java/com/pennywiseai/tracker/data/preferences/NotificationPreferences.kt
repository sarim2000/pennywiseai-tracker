package com.pennywiseai.tracker.data.preferences

import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.domain.conscience.ConscienceCandidate
import com.pennywiseai.tracker.domain.conscience.ConscienceChoice
import com.pennywiseai.tracker.domain.conscience.ConscienceFeedback
import com.pennywiseai.tracker.domain.conscience.ConscienceFrequency
import com.pennywiseai.tracker.domain.conscience.ConscienceKind
import com.pennywiseai.tracker.domain.conscience.ConscienceReaction
import com.pennywiseai.tracker.domain.conscience.ConscienceRemark
import com.pennywiseai.tracker.domain.spending.BudgetAlertTier
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Which notifications are on, and what each has already told the user. Backed by the single
 * user_preferences DataStore through [UserPreferencesRepository]; keys and defaults are pinned
 * by the UserPreferences golden tests.
 */
interface NotificationPreferences {
    val dailySummaryEnabled: Flow<Boolean>

    suspend fun setDailySummaryEnabled(enabled: Boolean)

    val weeklySummaryEnabled: Flow<Boolean>

    suspend fun setWeeklySummaryEnabled(enabled: Boolean)

    /** The first day of the last week the weekly recap has dealt with. */
    val weeklySummaryLastWeek: Flow<String?>

    suspend fun setWeeklySummaryLastWeek(weekStart: String)

    val budgetAlertsEnabled: Flow<Boolean>

    suspend fun setBudgetAlertsEnabled(enabled: Boolean)

    /** True at most once for this budget, [window] and [tier], across concurrent checks. */
    suspend fun claimBudgetAlert(budgetId: Long, window: String, tier: BudgetAlertTier): Boolean

    /** True once per dip of this account under its threshold; [isLow] false rearms it. */
    suspend fun claimLowBalanceAlert(bankName: String, accountLast4: String, isLow: Boolean): Boolean

    /** A rename is the same account in the same dip, so what it was told goes with it. */
    suspend fun moveLowBalanceAlert(oldBankName: String, accountLast4: String, newBankName: String)

    /** True at most once while [trip] is open-ended, 14 days on and not yet asked; records that it was. */
    suspend fun claimTripNudge(trip: TransactionGroupEntity, now: LocalDateTime): Boolean

    /** "Keep going": asks again 14 days from [now]; false when [trip] is no longer open-ended and running. */
    suspend fun keepTripGoing(trip: TransactionGroupEntity, now: LocalDateTime): Boolean

    suspend fun clearTripNudge(tripId: Long)

    val conscienceEnabled: Flow<Boolean>

    suspend fun setConscienceEnabled(enabled: Boolean)

    val conscienceFrequency: Flow<ConscienceFrequency>

    suspend fun setConscienceFrequency(frequency: ConscienceFrequency)

    /** Kinds of comment the user switched off on the Conscience screen. */
    val conscienceMutedKinds: Flow<Set<ConscienceKind>>

    suspend fun setConscienceKindEnabled(kind: ConscienceKind, enabled: Boolean)

    val conscienceFeedback: Flow<ConscienceFeedback>

    val conscienceRemarks: Flow<List<ConscienceRemark>>

    /** "Liked it" or "Not this one" on a remark; true when the line's standing changed. */
    suspend fun reactToConscience(remarkId: Long, lineKey: String, reaction: ConscienceReaction): Boolean

    /** Undoes the likes and retirements of the lines written in [contentLocale]. */
    suspend fun resetConscienceFeedback(contentLocale: String)

    /**
     * Spends a slot, picks the comment and stores its remark in one edit; null when the cadence
     * says no or no line is left. A remark only saved overnight (not notified) spends no slot.
     */
    suspend fun tryClaimConscience(
        candidates: List<ConscienceCandidate>,
        contentLocale: String,
        nowMillis: Long,
        todayEpochDay: Long,
        random: Random,
        remarkFor: (ConscienceChoice) -> ConscienceRemark?,
    ): ConscienceRemark?
}
