package com.pennywiseai.tracker.presentation.add

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/** What a save attempt came to. Only [Saved] may close the form; anything else keeps the draft. */
sealed interface SaveOutcome {
    data object Saved : SaveOutcome

    /** The database refused the row (-1): an identical one is already there. */
    data object Refused : SaveOutcome

    data class Failed(val error: Throwable) : SaveOutcome

    /** Another save is running, or this form already saved its row: the tap is ignored. */
    data object Busy : SaveOutcome
}

/**
 * One save at a time, and only a real row counts. A repeat tap while the insert runs is
 * [SaveOutcome.Busy]; so is any tap after a success (one form saves one row). A refusal or a
 * throw opens the gate again, so the user can fix the draft and save it.
 */
class SaveGate {
    private val busy = AtomicBoolean(false)

    suspend fun save(insert: suspend () -> Long): SaveOutcome {
        if (!busy.compareAndSet(false, true)) return SaveOutcome.Busy
        val outcome = try {
            if (insert() == -1L) SaveOutcome.Refused else SaveOutcome.Saved
        } catch (e: CancellationException) {
            busy.set(false)
            throw e
        } catch (e: Exception) {
            SaveOutcome.Failed(e)
        }
        if (outcome != SaveOutcome.Saved) busy.set(false)
        return outcome
    }
}
