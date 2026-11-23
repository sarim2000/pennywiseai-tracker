package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pennywiseai.tracker.domain.challenges.ChallengeKind
import com.pennywiseai.tracker.domain.challenges.ChallengeProgress
import com.pennywiseai.tracker.domain.challenges.ChallengeRun
import com.pennywiseai.tracker.domain.challenges.ChallengeState
import com.pennywiseai.tracker.domain.challenges.DayStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.temporal.ChronoUnit

/** What the Challenge widget shows. */
@Serializable
enum class ChallengeWidgetStatus {
    /** Nothing running and no result waiting: offer to start one. */
    NONE,
    ACTIVE,

    /** Won, until the user moves on from its result in Plan. */
    WON,

    /** Over without reaching its target, until the user moves on from it. */
    ENDED,
}

/**
 * The Plan challenge as the Challenge widget draws it. Text is built at draw time from
 * these, in the app's language; [categoryLabel] is the one name the worker resolves.
 * No amounts: a challenge counts days, never money.
 */
@Serializable
data class ChallengeWidgetSnapshot(
    val status: ChallengeWidgetStatus = ChallengeWidgetStatus.NONE,
    val kind: ChallengeKind = ChallengeKind.NO_SPEND_DAYS,
    /** The category a "week without" goes without, as the user reads it; null for no-spend days. */
    val categoryLabel: String? = null,
    val target: Int = 0,
    val cleanDays: Int = 0,
    /** Each day of the window, in order; only while [ChallengeWidgetStatus.ACTIVE]. */
    val days: List<DayStatus> = emptyList(),
    /** Days left in the window, today included; 0 once only the last day's grace runs. */
    val daysLeft: Int = 0,
    /** Bank messages can't be read, so no day settles. */
    val paused: Boolean = false,
    /** Some days wait only for the inbox to be read past them, which the widget can't start. */
    val needsInboxRead: Boolean = false,
)

/**
 * The widget's view of [stored], with [progress] worked out for its active run. A run
 * that has just finished shows its result straight away, as Plan does, even before
 * Plan records it; after that, the latest result shows until the user moves on from it.
 */
fun challengeWidgetSnapshot(
    stored: ChallengeState,
    progress: ChallengeProgress?,
    categoryLabel: (String) -> String = { it },
): ChallengeWidgetSnapshot {
    if (stored.active != null && progress != null) {
        val run = progress.run
        val base = ChallengeWidgetSnapshot(
            kind = run.kind,
            categoryLabel = run.labelWith(categoryLabel),
            target = run.target,
            cleanDays = progress.cleanDays,
        )
        return when {
            progress.won -> base.copy(status = ChallengeWidgetStatus.WON)
            progress.finished -> base.copy(status = ChallengeWidgetStatus.ENDED)
            else -> base.copy(
                status = ChallengeWidgetStatus.ACTIVE,
                days = progress.days.map { it.status },
                daysLeft = (ChronoUnit.DAYS.between(progress.on, run.end) + 1).toInt().coerceIn(0, run.length),
                paused = progress.paused,
                needsInboxRead = progress.needsInboxRead,
            )
        }
    }
    val result = stored.unseenResult ?: return ChallengeWidgetSnapshot()
    return ChallengeWidgetSnapshot(
        status = if (result.won) ChallengeWidgetStatus.WON else ChallengeWidgetStatus.ENDED,
        kind = result.run.kind,
        categoryLabel = result.run.labelWith(categoryLabel),
        target = result.run.target,
        cleanDays = result.cleanDays,
    )
}

private fun ChallengeRun.labelWith(label: (String) -> String): String? =
    categoryName?.takeIf { kind == ChallengeKind.CATEGORY_WEEK }?.let(label)

private val Context.challengeWidgetDataStore by preferencesDataStore(name = "widget_challenge_data")

/** The latest [ChallengeWidgetSnapshot], written by [ChallengeWidgetUpdateWorker]. */
object ChallengeWidgetDataStore {
    private val DATA = stringPreferencesKey("challenge_json")
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: ChallengeWidgetSnapshot): String = json.encodeToString(ChallengeWidgetSnapshot.serializer(), snapshot)

    /** Nothing stored, or a snapshot this version can't read, shows the empty state. */
    fun decode(raw: String?): ChallengeWidgetSnapshot =
        raw?.let { runCatching { json.decodeFromString(ChallengeWidgetSnapshot.serializer(), it) }.getOrNull() }
            ?: ChallengeWidgetSnapshot()

    fun getData(context: Context): Flow<ChallengeWidgetSnapshot> =
        context.challengeWidgetDataStore.data.map { decode(it[DATA]) }

    suspend fun update(context: Context, snapshot: ChallengeWidgetSnapshot) {
        context.challengeWidgetDataStore.edit { it[DATA] = encode(snapshot) }
    }
}
