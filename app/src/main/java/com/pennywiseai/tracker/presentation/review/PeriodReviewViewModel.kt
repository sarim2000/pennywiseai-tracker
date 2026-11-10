package com.pennywiseai.tracker.presentation.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.defaultReviewOffset
import com.pennywiseai.tracker.domain.review.latestReviewOffset
import com.pennywiseai.tracker.domain.review.localFirstDayOfWeek
import com.pennywiseai.tracker.domain.review.weekOffsetOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** [offset] counts back from [today], the day it was chosen: a period can't turn into another while the screen waits. */
data class ReviewSelection(val kind: ReviewKind, val offset: Int, val today: LocalDate)

@HiltViewModel
class PeriodReviewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val loader: PeriodReviewLoader,
) : ViewModel() {

    // The route says which tab to open on; once chosen, the choice survives process death.
    private val _selection = MutableStateFlow(
        savedStateHandle.get<Int>(KEY_OFFSET)?.let { offset ->
            ReviewSelection(
                ReviewKind.fromName(savedStateHandle.get<String>(KEY_KIND)),
                offset,
                savedStateHandle.get<Long>(KEY_TODAY)?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
            )
        } ?: routeSelection()
    )
    val selection: StateFlow<ReviewSelection> = _selection.asStateFlow()

    /** Null while loading, so a period's label never sits over the previous period's numbers. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val review: StateFlow<PeriodReview?> = _selection
        .flatMapLatest { (kind, offset, today) ->
            loader.observe(kind, offset, today).onStart<PeriodReview?> { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectKind(kind: ReviewKind) = select(selectionFor(kind))

    fun older() = select(_selection.value.let { it.copy(offset = it.offset - 1) })

    fun newer() = select(
        _selection.value.let { it.copy(offset = (it.offset + 1).coerceAtMost(latestReviewOffset(it.kind))) }
    )

    private fun select(selection: ReviewSelection) {
        _selection.value = selection
        savedStateHandle[KEY_KIND] = selection.kind.name
        savedStateHandle[KEY_OFFSET] = selection.offset
        savedStateHandle[KEY_TODAY] = selection.today.toEpochDay()
    }

    private fun routeSelection(): ReviewSelection {
        val kind = ReviewKind.fromName(savedStateHandle.get<String>("kind"))
        // MainActivity is exported, so this date isn't necessarily one the recap wrote.
        val week = savedStateHandle.get<String>("weekStart")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val today = LocalDate.now()
        return if (kind == ReviewKind.WEEK && week != null) {
            ReviewSelection(kind, weekOffsetOf(week, today, localFirstDayOfWeek()), today)
        } else {
            selectionFor(kind)
        }
    }

    private fun selectionFor(kind: ReviewKind): ReviewSelection {
        val today = LocalDate.now()
        return ReviewSelection(kind, defaultReviewOffset(kind, today), today)
    }

    private companion object {
        const val KEY_KIND = "selectedKind"
        const val KEY_OFFSET = "selectedOffset"
        const val KEY_TODAY = "selectedToday"
    }
}
