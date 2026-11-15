package com.pennywiseai.tracker.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.preferences.NeedsAnswerSnoozes
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** "Keep it as it is" and "Ask me later" for a transaction from "Needs an answer" (#1049). */
@HiltViewModel
class NeedsAnswerViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    fun keep(transactionHash: String, onDone: () -> Unit) = snooze(transactionHash, NeedsAnswerSnoozes.FOREVER, onDone)

    fun askLater(transactionHash: String, onDone: () -> Unit) =
        snooze(transactionHash, LocalDate.now().plusDays(LATER_DAYS - 1).toEpochDay(), onDone)

    private fun snooze(hash: String, untilEpochDay: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.snoozeNeedsAnswer(hash, untilEpochDay, LocalDate.now())
            onDone()
        }
    }

    private companion object {
        /** Back in the queue on the 7th day from today. */
        const val LATER_DAYS = 7L
    }
}
