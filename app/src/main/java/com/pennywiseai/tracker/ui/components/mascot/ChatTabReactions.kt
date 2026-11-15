package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.events.TransactionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the Chat tab's owl turns into for a moment. */
data class MascotReaction(val shape: MascotShape, val mood: MascotMood)

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
 * The Chat tab's reactions: one shape for [holdMillis] per save, the bubble while an inbox
 * scan runs, then the owl again. A burst folds into the reaction already showing: nothing
 * queues behind it and its hold never extends. A scan takes over from a reaction.
 *
 * Saves come in through [onSave] for as long as [scope] lives (the Chat tab's ViewModel),
 * so one made on a screen pushed over the tabs (Add) is still inside its hold when the bar
 * comes back. The hold runs on [scope], so it ends on time whether or not anyone watches.
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
        if (!scanning) react(reactionFor(event.type))
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
        if (flash != null) return
        flash = reaction
        publish()
        hold = scope.launch {
            delay(holdMillis)
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
