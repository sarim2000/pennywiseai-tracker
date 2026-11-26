package com.pennywiseai.tracker.data.events

import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.Money
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Where a save came from. */
enum class TransactionOrigin {
    /** A live SMS, a bank's app notification, or a message the user taught. */
    BANK_MESSAGE,
    MANUAL,
    CHAT,
}

/**
 * [count] rows of [type] were just saved from [origin], and for a single row the [money] it
 * moved, which the Chat tab echoes on screen. Nothing else: no merchant, no description.
 * The amount is never logged or persisted.
 */
data class TransactionEvent(
    val type: TransactionType,
    val count: Int,
    val origin: TransactionOrigin,
    val money: Money? = null,
)

/**
 * Saves the user just made while the app is in the foreground, for the Chat tab's mascot
 * to react to. Only the live save paths announce one, and only after the row committed:
 * the SMS and bank-notification processor, a manual add or transfer, and a chat Add (once
 * per card). Imports, backup restore, recurring rows, autopay and loans never do
 * (`TransactionEventsTest`). Developer mode's mascot playground announces saves it never
 * makes, to play the reactions on the real tab.
 *
 * Nothing is kept: a save made in the background, or with no one listening, is gone, so
 * it is never replayed when the user comes back. Not persisted, so backups are untouched.
 */
@Singleton
class TransactionEvents @Inject constructor(private val appForeground: AppForegroundState) {
    // A slow collector drops the oldest instead of holding up a save.
    private val events = MutableSharedFlow<TransactionEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val saved: SharedFlow<TransactionEvent> = events.asSharedFlow()

    /**
     * Call once the insert committed (its row id isn't -1). Never suspends. [money] is the
     * saved row's amount; it is dropped for a batch, which has no one amount to show.
     */
    fun onSaved(type: TransactionType, origin: TransactionOrigin, count: Int = 1, money: Money? = null) {
        if (appForeground.isInForeground()) {
            events.tryEmit(TransactionEvent(type, count, origin, money.takeIf { count == 1 }))
        }
    }
}
