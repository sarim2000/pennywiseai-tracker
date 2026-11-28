package com.pennywiseai.tracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.trip.nextTripBoundary
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/**
 * The current time for trip status, refreshed when the app resumes and when the next start or
 * end of any of [groups] passes, so a Scheduled/Running/Ended label never goes stale on screen.
 */
@Composable
fun rememberTripNow(groups: List<TransactionGroupEntity>): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LifecycleResumeEffect(Unit) {
        now = LocalDateTime.now()
        onPauseOrDispose {}
    }
    val next = nextTripBoundary(groups, now)
    LaunchedEffect(next, now) {
        if (next != null) {
            delay(Duration.between(LocalDateTime.now(), next).toMillis().coerceAtLeast(0) + 50)
            now = LocalDateTime.now()
        }
    }
    return now
}
