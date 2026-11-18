package com.pennywiseai.tracker.presentation.achievements

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.domain.achievements.Achievement
import com.pennywiseai.tracker.domain.achievements.AchievementLoader
import com.pennywiseai.tracker.domain.achievements.forDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Worked out once per visit: nothing about achievements changes while the screen is open. */
@HiltViewModel
class AchievementsViewModel @Inject constructor(loader: AchievementLoader) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    /**
     * Null while loading. A failure is its own state: an empty list would read as
     * "0 of 0", and a calculation with no history still returns every badge, locked.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val achievements: StateFlow<Result<List<Achievement>>?> = attempt
        .flatMapLatest {
            flow<Result<List<Achievement>>?> {
                emit(null)
                emit(Result.success(loader.load().forDisplay()))
            }.catch { e ->
                Log.e("AchievementsViewModel", "Couldn't work out achievements", e)
                emit(Result.failure(e))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun retry() = attempt.update { it + 1 }
}
