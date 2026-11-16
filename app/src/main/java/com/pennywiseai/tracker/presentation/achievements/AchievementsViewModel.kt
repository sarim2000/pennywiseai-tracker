package com.pennywiseai.tracker.presentation.achievements

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.domain.achievements.Achievement
import com.pennywiseai.tracker.domain.achievements.AchievementLoader
import com.pennywiseai.tracker.domain.achievements.forDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Worked out once per visit: nothing about achievements changes while the screen is open. */
@HiltViewModel
class AchievementsViewModel @Inject constructor(loader: AchievementLoader) : ViewModel() {

    /** Null while loading. */
    val achievements: StateFlow<List<Achievement>?> = flow<List<Achievement>?> { emit(loader.load().forDisplay()) }
        .catch { e ->
            Log.e("AchievementsViewModel", "Couldn't work out achievements", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
