package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.presentation.goals.GoalTone
import com.pennywiseai.tracker.presentation.goals.fraction
import com.pennywiseai.tracker.presentation.goals.percent
import com.pennywiseai.tracker.presentation.goals.shortLine
import com.pennywiseai.tracker.presentation.goals.statusLine
import com.pennywiseai.tracker.presentation.goals.tone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One goal as the goal widgets draw it (#914). Text is pre-built by the worker
 * (currency-tagged, translated) so the widgets never touch money math.
 */
@Serializable
data class GoalWidgetItem(
    val id: Long,
    val name: String,
    /** The goal's `#RRGGBB`; the widget falls back to the theme primary if malformed. */
    val color: String,
    val percent: Int,
    val fraction: Float,
    /** List row: "₹1,920/mo · 5 months left", "Behind ₹2,000", "Reached", "Missed by ₹X". */
    val line: String,
    /** 2×2: "₹9,600 left · 5 mo", or Reached / Missed by. */
    val shortLine: String,
    val tone: GoalTone = GoalTone.NEUTRAL,
)

fun GoalProgress.toWidgetItem(context: Context) = GoalWidgetItem(
    id = goal.id,
    name = goal.name,
    color = goal.color,
    percent = percent(),
    fraction = fraction(),
    line = statusLine(context),
    shortLine = shortLine(context),
    tone = tone(),
)

/** What the 2×2 single-goal widget shows. */
sealed interface SingleGoalState {
    data class Shown(val item: GoalWidgetItem) : SingleGoalState
    /** The chosen goal was deleted or archived. */
    data object Removed : SingleGoalState
    data object NoGoals : SingleGoalState
}

/** Goals the 4×2 widget lists: the snapshot is already soonest due first, Reached last. */
const val GOALS_WIDGET_MAX = 3

/**
 * [items] are the non-archived goals in display order; [chosenId] is the goal
 * picked when the widget was added (null if it never was: show the first).
 */
fun singleGoalState(items: List<GoalWidgetItem>, chosenId: Long?): SingleGoalState = when {
    chosenId != null -> items.firstOrNull { it.id == chosenId }?.let { SingleGoalState.Shown(it) } ?: SingleGoalState.Removed
    items.isEmpty() -> SingleGoalState.NoGoals
    else -> SingleGoalState.Shown(items.first())
}

private val Context.goalWidgetDataStore by preferencesDataStore(name = "widget_goals_data")

/** Snapshot of every non-archived goal, shared by both goal widgets. */
object GoalWidgetDataStore {
    private val DATA = stringPreferencesKey("goals_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(GoalWidgetItem.serializer())

    fun encode(items: List<GoalWidgetItem>): String = json.encodeToString(serializer, items)
    fun decode(raw: String?): List<GoalWidgetItem> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    fun getData(context: Context): Flow<List<GoalWidgetItem>> =
        context.goalWidgetDataStore.data.map { decode(it[DATA]) }

    suspend fun update(context: Context, items: List<GoalWidgetItem>) {
        context.goalWidgetDataStore.edit { it[DATA] = encode(items) }
    }
}
