package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.data.repository.GoalRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Rebuilds the goal widgets' snapshot (#914) and redraws both. Enqueued when
 * goals or contributions change (PennyWiseApplication observes them), with the
 * other widgets via [WidgetRefresher], and on each widget's update period.
 */
@HiltWorker
class GoalWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val goalRepository: GoalRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "goal_widget_update"

        fun enqueueOneShot(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<GoalWidgetUpdateWorker>().build()
            )
        }
    }

    override suspend fun doWork(): Result = try {
        val items = goalRepository.observeActiveGoals().first().map { it.toWidgetItem(applicationContext) }
        GoalWidgetDataStore.update(applicationContext, items)
        GoalWidget().updateAll(applicationContext)
        GoalsListWidget().updateAll(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
