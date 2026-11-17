package com.pennywiseai.tracker.data.events

import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.utils.Money
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Something the user just finished in the app that's worth more than a plain save: progress
 * (a goal), closure (a debt), learning (a message taught) or care for their records. Each
 * carries only what the Chat tab's chip shows: a count or one save's [Money], never a name,
 * a sender, a message or an account.
 */
sealed interface MascotMoment {
    /** Money was put into a goal by hand, short of its target. */
    data class GoalContributed(val money: Money? = null) : MascotMoment

    /** A contribution took a goal from short of its target to reaching it. */
    data object GoalReached : MascotMoment

    /** A repayment took a debt from owed to settled. */
    data object DebtPaidOff : MascotMoment

    /** An unrecognised message was taught, and booked a transaction. */
    data object MessageTaught : MascotMoment

    /** The user applied rules to past transactions, and [count] changed. */
    data class RulesApplied(val count: Int) : MascotMoment

    /** A statement import added or filled in [count] transactions. */
    data class StatementImported(val count: Int) : MascotMoment

    /** The user wrote a backup, and it was saved. */
    data object BackupSaved : MascotMoment

    /** A backup the user picked was restored. */
    data object BackupRestored : MascotMoment

    /** The user started tracking a bill or subscription. */
    data object BillTracked : MascotMoment

    /** The user marked a bill paid, recording [money]. */
    data class BillPaid(val money: Money? = null) : MascotMoment

    /** The user hid amounts. */
    data object AmountsHidden : MascotMoment

    /** The user showed amounts again. */
    data object AmountsShown : MascotMoment
}

/**
 * Moments the user just made while the app is in the foreground, for the Chat tab's mascot
 * to react to (see [TransactionEvents] for plain saves). Announce one only after what it
 * describes committed, only from the user's own action, and once per action: never from a
 * worker, a restore's replay of old rows, or a screen first observing a state. Developer
 * mode's mascot playground announces moments that never happened, to play them on the
 * real tab.
 *
 * Nothing is kept: a moment in the background, or with no one listening, is gone, so it
 * is never replayed when the user comes back. Not persisted, so backups are untouched.
 */
@Singleton
class MascotMoments @Inject constructor(private val appForeground: AppForegroundState) {
    // A slow collector drops the oldest instead of holding up the caller.
    private val events = MutableSharedFlow<MascotMoment>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val moments: SharedFlow<MascotMoment> = events.asSharedFlow()

    /** Call once the action committed. Never suspends. */
    fun announce(moment: MascotMoment) {
        if (appForeground.isInForeground()) events.tryEmit(moment)
    }
}
