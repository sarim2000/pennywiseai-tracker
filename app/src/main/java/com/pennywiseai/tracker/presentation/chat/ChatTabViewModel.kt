package com.pennywiseai.tracker.presentation.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.pennywiseai.tracker.core.AppForegroundState
import com.pennywiseai.tracker.data.events.MascotMoments
import com.pennywiseai.tracker.data.events.TransactionEvents
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.navigation.BottomNavItem
import com.pennywiseai.tracker.ui.components.mascot.ChatTabReactions
import com.pennywiseai.tracker.ui.components.mascot.SmsScanStatus
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the Chat tab's mascot reacts to: saves and moments made in the app, and the inbox scan. It
 * lives with the Home entry, so it hears a save made on a screen pushed over the tabs.
 * It hears them only while the user shows the Chat tab, and leaving the app clears what
 * it shows, whether or not the bar is up to follow the scan.
 */
@HiltViewModel
class ChatTabViewModel @Inject constructor(
    @ApplicationContext context: Context,
    transactionEvents: TransactionEvents,
    appForeground: AppForegroundState,
    userPreferencesRepository: UserPreferencesRepository,
    mascotMoments: MascotMoments,
) : ViewModel() {
    internal val reactions = ChatTabReactions(viewModelScope)

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences
                .map { BottomNavItem.Chat.route !in it.hiddenNavTabs }
                .distinctUntilChanged()
                .collectLatest { visible ->
                    if (visible) {
                        coroutineScope {
                            launch { transactionEvents.saved.collect(reactions::onSave) }
                            mascotMoments.moments.collect(reactions::onMoment)
                        }
                    } else {
                        reactions.clear()
                    }
                }
        }
        viewModelScope.launch { appForeground.backgrounded.collect { reactions.clear() } }
    }

    /** Every inbox scan the user starts (sync, scan again, rebuild, onboarding) runs as this unique work. */
    val scan: Flow<SmsScanStatus> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(OptimizedSmsReaderWorker.WORK_NAME)
        .map(::smsScanStatus)
        .distinctUntilChanged()
}

internal fun smsScanStatus(infos: List<WorkInfo>): SmsScanStatus {
    if (infos.any { !it.state.isFinished }) return SmsScanStatus.Running
    val done = infos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED } ?: return SmsScanStatus.Idle
    // A rebuild deletes and recreates rows, so what it saved isn't new.
    val newRows = if (OptimizedSmsReaderWorker.TAG_REBUILD in done.tags) 0
    else done.outputData.getInt(OptimizedSmsReaderWorker.PROGRESS_SAVED, 0)
    return SmsScanStatus.Finished(newRows)
}
