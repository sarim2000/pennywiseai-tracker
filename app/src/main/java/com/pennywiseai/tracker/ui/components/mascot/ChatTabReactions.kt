package com.pennywiseai.tracker.ui.components.mascot

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * beside the tab while it holds, for [holdMillis] or the tab's usual hold when null. A
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

/** What the chip beside the Chat tab says while a reaction holds. Never a name or a message. */
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

/**
 * How long a reaction made while the bar is away (a goal reached on its own screen, a
 * backup in Settings) waits for the bar to come back before it's dropped as stale.
 */
internal const val REACTION_WAIT_MS = 90_000L

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
 * inbox scan runs, then the owl again. Nothing queues: the latest reaction of the same or
 * a higher rank takes over, with a hold of its own, so a second save shows itself rather
 * than the first one's amount; a lower one folds into what's showing. So a goal reached
 * stays over a save that follows it, and the owl covering its eyes over anything. A scan
 * takes over from a reaction, all but a toggle, which shows over the bubble for its hold;
 * the scan's bubble carries no chip.
 *
 * Saves and moments come in through [onSave] and [onMoment] while the Chat tab is
 * visible, for as long as [scope] lives (the Chat tab's ViewModel), so one made on a screen
 * pushed over the tabs still shows when the bar comes back. Its hold starts once the bar
 * says it is up and past its entrance ([onBarShown]): made while the bar is away, it
 * waits up to [waitMillis] for it, then goes unseen. A toggle never waits for the bar: it
 * answers the switch now or not at all, though a bar on screen in its entrance beat counts
 * as shown. The timers run on [scope], so they end on time whether or not anyone
 * watches; [clear] drops the reaction early.
 */
internal class ChatTabReactions(
    private val scope: CoroutineScope,
    private val holdMillis: Long = REACTION_HOLD_MS,
    private val waitMillis: Long = REACTION_WAIT_MS,
) {
    private var flash: MascotReaction? = null
    private var scanning = false
    private var hold: Job? = null

    /** Whether the bar is up and past its entrance, so a hold can start. */
    private var barShown = true

    /** Whether the bar is on screen but still in its entrance beat. */
    private var barArriving = false

    /** Whether [flash]'s hold has started; until then it waits for the bar. */
    private var holding = false
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
        val reaction = reactionFor(moment)
        // The user's own toggle still answers during a scan; anything else waits it out unseen.
        if (scanning && reaction.rank < RANK_TOGGLE) return
        // Made with the bar away (a shake on Settings), a toggle would cover the owl's eyes
        // long after the switch was flipped, so it goes unanswered. A bar in its entrance beat
        // is on screen (Home's eye tapped just back from a pushed screen): that one answers.
        if (reaction.rank == RANK_TOGGLE && !barShown && !barArriving) return
        react(reaction)
    }

    /**
     * The bar went away, or is up and past its entrance: the owl it brought back has
     * turned, so a reaction waiting for it starts its hold now and none of it is spent on
     * the owl. The bar times that beat itself, so the hold and what the user sees share one
     * clock. One already holding runs out on time. [arriving] says a bar not yet shown is on
     * screen in that beat.
     */
    fun onBarShown(shown: Boolean, arriving: Boolean = false) {
        barShown = shown
        barArriving = !shown && arriving
        if (shown && flash != null && !holding) startHold()
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
                // A scan takes over from a reaction, all but a toggle, which keeps its hold.
                if (running && !scanning && flash?.rank != RANK_TOGGLE) clearFlash()
                scanning = running
                publish()
                // The beat belongs to the scan that just ended: it runs out on time, bar or not.
                if (status is SmsScanStatus.Finished && sawRunning && status.newRows > 0) react(SCANNED, waitsForBar = false)
                sawRunning = running
            }
        } finally {
            clearFlash()
            scanning = false
            publish()
        }
    }

    private fun react(reaction: MascotReaction, waitsForBar: Boolean = true) {
        // The latest says what just happened: a later save shows its own amount, and a later
        // toggle answers for the switch as it is now.
        val showing = flash
        if (showing != null && reaction.rank < showing.rank) return
        flash = reaction
        publish()
        if (barShown || !waitsForBar) {
            startHold()
        } else {
            holding = false
            // A toggle waits out only the bar's entrance beat, never for the bar to come back.
            endAfter(if (reaction.rank == RANK_TOGGLE) TOGGLE_HOLD_MS else waitMillis)
        }
    }

    private fun startHold() {
        holding = true
        endAfter(flash?.holdMillis ?: holdMillis)
    }

    /** The owl comes back after [millis], whatever timer ran before. */
    private fun endAfter(millis: Long) {
        hold?.cancel()
        hold = scope.launch {
            delay(millis)
            flash = null
            holding = false
            publish()
        }
    }

    private fun clearFlash() {
        hold?.cancel()
        hold = null
        flash = null
        holding = false
    }

    private fun publish() {
        _shown.value = if (scanning && flash?.rank != RANK_TOGGLE) SCANNING else flash
    }
}

/**
 * Follows the scan while the screen is STARTED and returns what the tab shows. Call it
 * only while the Chat tab is shown, with whether the bar is up and past its entrance
 * ([barShown], as PennyWiseBottomNavigation reports it) and whether it is on screen at all
 * ([barVisible]); leaving composition counts as the bar going away. Going to the background clears the reaction; one made while a screen
 * covers the tabs waits for them and shows on return.
 */
@Composable
internal fun rememberChatTabReaction(
    reactions: ChatTabReactions,
    scan: Flow<SmsScanStatus>,
    barShown: Boolean,
    barVisible: Boolean,
): MascotReaction? {
    // A root screen (a goal, Settings) covers the tabs, bar and all: gone counts as hidden.
    DisposableEffect(reactions, barShown, barVisible) {
        reactions.onBarShown(barShown, arriving = barVisible)
        onDispose { reactions.onBarShown(false) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reactions, lifecycle, scan) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { reactions.watchScan(scan) }
    }
    return reactions.shown.collectAsStateWithLifecycle().value
}
