package com.pennywiseai.tracker.ui.components.mascot

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.events.TransactionEvent
import com.pennywiseai.tracker.utils.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the Chat tab's owl turns into for a moment: a [shape] and [mood], the [cue] it
 * plays (null for the shape's own), its figure [value] (a jar's fill), and a [chip] shown
 * above the tab while it holds, for [holdMillis] or the tab's usual hold when null. A
 * reaction of a higher [rank] takes over from one showing.
 */
data class MascotReaction(
    val shape: MascotShape,
    val mood: MascotMood,
    val chip: ReactionChip? = null,
    val cue: MascotCue? = null,
    val value: Float = 0f,
    val rank: Int = RANK_SAVE,
    val holdMillis: Long? = null,
)

/** What the chip above the Chat tab says while a reaction holds. Never a name or a message. */
sealed interface ReactionChip

/** The [money] a save of [type] moved; the type signs and colours it. */
data class ReactionAmount(val money: Money, val type: TransactionType) : ReactionChip

/** A short phrase: what just happened. */
data class ReactionLabel(@StringRes val text: Int) : ReactionChip

/** How many rows an action touched, as a [text] plural of [count]. */
data class ReactionCount(@PluralsRes val text: Int, val count: Int) : ReactionChip

/** A plain save: anything else takes over from it. */
internal const val RANK_SAVE = 0

/** A finished action worth a word (a message taught, rules applied): takes over from a save. */
internal const val RANK_ACTION = 1

/** A milestone (a goal reached, a debt paid off): takes over from a save or an action. */
internal const val RANK_MILESTONE = 2

/** The user's own toggle (amounts hidden or shown): answered at once, over anything. */
internal const val RANK_TOGGLE = 3

/** How long the owl covers its eyes, or peeks, before the next reaction may show. */
internal const val TOGGLE_HOLD_MS = 1400L

/** The inbox scan, as the Chat tab sees it. */
sealed interface SmsScanStatus {
    data object Idle : SmsScanStatus
    data object Running : SmsScanStatus

    /** Done, with the rows it saved that weren't there before (0 for a rebuild). */
    data class Finished(val newRows: Int) : SmsScanStatus
}

/** How long a reaction holds before the owl comes back. */
internal const val REACTION_HOLD_MS = 3000L

/** A scan is running: a message bubble, reading. */
internal val SCANNING = MascotReaction(MascotShape.Bubble, MascotMood.Thinking)

/** The scan saved something new: the bubble stays one calm beat. */
internal val SCANNED = MascotReaction(MascotShape.Bubble, MascotMood.Calm)

/**
 * The tab's shape and mood for a save of [type]. A credit-card purchase spends, so it is
 * a receipt like an expense; only income is happy.
 */
internal fun reactionFor(type: TransactionType): MascotReaction = when (type) {
    TransactionType.INCOME -> MascotReaction(MascotShape.Coin, MascotMood.Happy)
    TransactionType.EXPENSE, TransactionType.CREDIT -> MascotReaction(MascotShape.Receipt, MascotMood.Calm)
    TransactionType.TRANSFER -> MascotReaction(MascotShape.Wallet, MascotMood.Calm)
    TransactionType.INVESTMENT -> MascotReaction(MascotShape.Jar, MascotMood.Calm)
}

/**
 * The tab's reaction to [moment]. Only income and the milestones are happy; every chip is
 * a count, a phrase or the one amount the user just entered, never a name.
 */
internal fun reactionFor(moment: MascotMoment): MascotReaction = when (moment) {
    is MascotMoment.GoalContributed -> MascotReaction(
        MascotShape.Jar, MascotMood.Calm,
        chip = moment.money?.let { ReactionAmount(it, TransactionType.INVESTMENT) },
        cue = MascotCue.CoinDrop, value = GOAL_FILL, rank = RANK_ACTION,
    )
    MascotMoment.GoalReached -> MascotReaction(
        MascotShape.Jar, MascotMood.Happy, ReactionLabel(R.string.mascot_chip_goal_reached),
        cue = MascotCue.Brim, value = 1f, rank = RANK_MILESTONE,
    )
    MascotMoment.DebtPaidOff -> MascotReaction(
        MascotShape.Wallet, MascotMood.Happy, ReactionLabel(R.string.mascot_chip_paid_off),
        cue = MascotCue.PaidOff, rank = RANK_MILESTONE,
    )
    MascotMoment.MessageTaught -> MascotReaction(
        MascotShape.Bubble, MascotMood.Calm, ReactionLabel(R.string.mascot_chip_learned),
        cue = MascotCue.Learned, rank = RANK_ACTION,
    )
    is MascotMoment.RulesApplied -> MascotReaction(
        MascotShape.Receipt, MascotMood.Calm, ReactionCount(R.plurals.mascot_chip_rules_applied, moment.count),
        cue = MascotCue.Sorted, rank = RANK_ACTION,
    )
    is MascotMoment.StatementImported -> MascotReaction(
        MascotShape.Receipt, MascotMood.Calm, ReactionCount(R.plurals.mascot_chip_imported, moment.count),
        cue = MascotCue.Read, rank = RANK_ACTION,
    )
    MascotMoment.BackupSaved -> MascotReaction(
        MascotShape.Shield, MascotMood.Calm, ReactionLabel(R.string.mascot_chip_backup_saved),
        cue = MascotCue.Secured, rank = RANK_ACTION,
    )
    MascotMoment.BackupRestored -> MascotReaction(
        MascotShape.Shield, MascotMood.Calm, ReactionLabel(R.string.mascot_chip_restored),
        cue = MascotCue.Secured, rank = RANK_ACTION,
    )
    MascotMoment.BillTracked -> MascotReaction(
        MascotShape.Bell, MascotMood.Calm, ReactionLabel(R.string.mascot_chip_bill_tracked),
        cue = MascotCue.Ring, rank = RANK_ACTION,
    )
    is MascotMoment.BillPaid -> MascotReaction(
        MascotShape.Bell, MascotMood.Calm,
        chip = moment.money?.let { ReactionAmount(it, TransactionType.EXPENSE) },
        cue = MascotCue.Paid, rank = RANK_ACTION,
    )
    MascotMoment.AmountsHidden -> MascotReaction(
        MascotShape.Owl, MascotMood.Calm, cue = MascotCue.CoverEyes, rank = RANK_TOGGLE, holdMillis = TOGGLE_HOLD_MS,
    )
    MascotMoment.AmountsShown -> MascotReaction(
        MascotShape.Owl, MascotMood.Calm, cue = MascotCue.Peek, rank = RANK_TOGGLE, holdMillis = TOGGLE_HOLD_MS,
    )
}

/** How full a jar saved into shows: part way, as a goal short of its target is. */
private const val GOAL_FILL = 0.55f

/**
 * The Chat tab's reactions: one shape for its hold per save or moment, the bubble while an
 * inbox scan runs, then the owl again. A burst folds into the reaction already showing,
 * which keeps its first chip: nothing queues behind it and its hold never extends. Only a
 * reaction of a higher rank takes over, with a hold of its own: a goal reached over the
 * save that reached it, the owl covering its eyes over anything (and a later toggle over
 * an earlier one). A scan takes over from a
 * reaction, and the scan's bubble carries no chip.
 *
 * Saves come in through [onSave] while the Chat tab is visible, for as long as [scope]
 * lives (the Chat tab's ViewModel), so one made on a screen pushed over the tabs (Add) is
 * still inside its hold when the bar comes back. The hold runs on [scope], so it ends on
 * time whether or not anyone watches; [clear] drops it early.
 */
internal class ChatTabReactions(
    private val scope: CoroutineScope,
    private val holdMillis: Long = REACTION_HOLD_MS,
) {
    private var flash: MascotReaction? = null
    private var scanning = false
    private var hold: Job? = null
    private val _shown = MutableStateFlow<MascotReaction?>(null)

    /** What the tab shows in place of the owl, or null for the owl. */
    val shown: StateFlow<MascotReaction?> = _shown.asStateFlow()

    /** A save just committed in the foreground (gated at the source). */
    fun onSave(event: TransactionEvent) {
        if (scanning) return
        val amount = event.money?.let { ReactionAmount(it, event.type) }
        react(reactionFor(event.type).copy(chip = amount))
    }

    /** A moment just finished in the foreground (gated at the source). */
    fun onMoment(moment: MascotMoment) {
        if (scanning) return
        react(reactionFor(moment))
    }

    /**
     * The user left the app or hid the tab: the reaction goes now, so there is nothing to
     * show on return. A running scan's bubble belongs to [watchScan], which ends it itself.
     */
    fun clear() {
        clearFlash()
        publish()
    }

    /**
     * Follows the inbox scan until cancelled, then clears everything, so a backgrounded
     * screen keeps nothing to show on return. Only a scan seen running here earns the beat
     * after it, so one that finished while no one watched passes without a word.
     */
    suspend fun watchScan(scan: Flow<SmsScanStatus>) {
        try {
            var sawRunning = false
            scan.collect { status ->
                val running = status == SmsScanStatus.Running
                if (running && !scanning) clearFlash()
                scanning = running
                publish()
                if (status is SmsScanStatus.Finished && sawRunning && status.newRows > 0) react(SCANNED)
                sawRunning = running
            }
        } finally {
            clearFlash()
            scanning = false
            publish()
        }
    }

    private fun react(reaction: MascotReaction) {
        val showing = flash
        // The latest toggle answers for the switch as it is now.
        val takesOver = showing == null || reaction.rank > showing.rank ||
            (reaction.rank == RANK_TOGGLE && showing.rank == RANK_TOGGLE)
        if (!takesOver) return
        hold?.cancel()
        flash = reaction
        publish()
        hold = scope.launch {
            delay(reaction.holdMillis ?: holdMillis)
            flash = null
            publish()
        }
    }

    private fun clearFlash() {
        hold?.cancel()
        hold = null
        flash = null
    }

    private fun publish() {
        _shown.value = if (scanning) SCANNING else flash
    }
}

/**
 * Follows the scan while the screen is STARTED and returns what the tab shows. Call it
 * only while the Chat tab is shown. Leaving composition or going to the background clears
 * the reaction; a save made while a pushed screen covers the tabs still shows on return.
 */
@Composable
internal fun rememberChatTabReaction(reactions: ChatTabReactions, scan: Flow<SmsScanStatus>): MascotReaction? {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reactions, lifecycle, scan) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { reactions.watchScan(scan) }
    }
    return reactions.shown.collectAsStateWithLifecycle().value
}
