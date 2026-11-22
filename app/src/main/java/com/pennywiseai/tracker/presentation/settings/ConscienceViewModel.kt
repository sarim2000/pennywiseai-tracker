package com.pennywiseai.tracker.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.manager.ConscienceNotificationActions
import com.pennywiseai.tracker.data.manager.ConscienceNotifier
import com.pennywiseai.tracker.data.manager.ConscienceText
import com.pennywiseai.tracker.data.preferences.NotificationPreferences
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.conscience.ConscienceFeedback
import com.pennywiseai.tracker.domain.conscience.ConscienceFrequency
import com.pennywiseai.tracker.domain.conscience.ConscienceKind
import com.pennywiseai.tracker.domain.conscience.ConscienceLines
import com.pennywiseai.tracker.domain.conscience.ConscienceReaction
import com.pennywiseai.tracker.domain.conscience.ConscienceRemark
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class ConscienceStatus { OFF, BLOCKED, LEARNING, LISTENING }

data class ConscienceUiState(
    val enabled: Boolean = false,
    val status: ConscienceStatus = ConscienceStatus.OFF,
    val frequency: ConscienceFrequency = ConscienceFrequency.NORMAL,
    val mutedKinds: Set<ConscienceKind> = emptySet(),
    val example: String = "",
    val exampleKind: ConscienceKind = ConscienceKind.COMEBACK,
    val remarks: List<ConscienceRemark> = emptyList(),
    val likedCount: Int = 0,
    val retiredCount: Int = 0,
)

private data class Settings(val enabled: Boolean, val frequency: ConscienceFrequency, val mutedKinds: Set<ConscienceKind>)

private data class Activity(val remarks: List<ConscienceRemark>, val feedback: ConscienceFeedback)

private data class Device(val canPost: Boolean, val learning: Boolean = false, val exampleIndex: Int = 0, val locales: String = "")

/** The Conscience screen, which only a language with the full set opens ([ConscienceText.full]). */
@HiltViewModel
class ConscienceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: NotificationPreferences,
    private val transactionRepository: TransactionRepository,
    private val notificationActions: ConscienceNotificationActions,
) : ViewModel() {

    private val device = MutableStateFlow(Device(canPost = ConscienceNotifier.canPost(context)))

    private val settings = combine(
        preferences.conscienceEnabled,
        preferences.conscienceFrequency,
        preferences.conscienceMutedKinds,
        ::Settings
    )

    private val activity = combine(preferences.conscienceRemarks, preferences.conscienceFeedback, ::Activity)

    val state: StateFlow<ConscienceUiState> = combine(settings, activity, device) { settings, activity, device ->
        val text = ConscienceText(context)
        val (fact, line) = ConscienceLines.sample(device.exampleIndex)
        ConscienceUiState(
            enabled = settings.enabled,
            status = when {
                !settings.enabled -> ConscienceStatus.OFF
                !device.canPost -> ConscienceStatus.BLOCKED
                device.learning -> ConscienceStatus.LEARNING
                else -> ConscienceStatus.LISTENING
            },
            frequency = settings.frequency,
            mutedKinds = settings.mutedKinds,
            example = text.render(fact, line).orEmpty(),
            exampleKind = fact.kind,
            remarks = activity.remarks,
            likedCount = activity.feedback.liked.size,
            retiredCount = activity.feedback.retired.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConscienceUiState())

    init {
        viewModelScope.launch {
            val oldest = transactionRepository.getOldestTransactionDateTime()?.toLocalDate()
            val learning = oldest == null || oldest.isAfter(LocalDate.now().minusDays(LEARNING_DAYS))
            device.update { it.copy(learning = learning) }
        }
    }

    /** The language can change under a living ViewModel (it survives the recreate), so everything shown is re-rendered. */
    fun onLocalesChanged(locales: String) = device.update { it.copy(locales = locales) }

    /** Notifications can be switched on in system settings while the screen is away. */
    fun refreshPermission() = device.update { it.copy(canPost = ConscienceNotifier.canPost(context)) }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setConscienceEnabled(enabled) }
    }

    fun setFrequency(frequency: ConscienceFrequency) {
        viewModelScope.launch { preferences.setConscienceFrequency(frequency) }
    }

    fun setKindEnabled(kind: ConscienceKind, enabled: Boolean) {
        viewModelScope.launch { preferences.setConscienceKindEnabled(kind, enabled) }
    }

    fun nextExample() = device.update { it.copy(exampleIndex = it.exampleIndex + 1) }

    fun react(remark: ConscienceRemark, reaction: ConscienceReaction) {
        viewModelScope.launch { preferences.reactToConscience(remark.id, remark.lineKey, reaction) }
    }

    fun resetFeedback() {
        viewModelScope.launch { preferences.resetConscienceFeedback() }
    }

    /** Posts the example on screen; false when notifications are blocked, so the screen can say why nothing came. */
    fun tryIt(): Boolean {
        refreshPermission()
        if (!ConscienceNotifier.canPost(context)) return false
        val example = state.value.example.ifEmpty { return false }
        ConscienceNotifier.postSample(context, ConscienceText(context), example, notificationActions)
        return true
    }

    private companion object {
        // Until the ledger is this old, the history-based remarks (spikes, wins, rituals) can't speak yet.
        const val LEARNING_DAYS = 42L
    }
}
