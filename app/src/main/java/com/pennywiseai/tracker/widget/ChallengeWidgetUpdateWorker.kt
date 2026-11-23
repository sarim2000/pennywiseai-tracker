package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pennywiseai.tracker.data.database.entity.parentNameOf
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.domain.challenges.challengeRows
import com.pennywiseai.tracker.domain.challenges.inboxReadBefore
import com.pennywiseai.tracker.domain.challenges.namedAsNow
import com.pennywiseai.tracker.domain.challenges.progress
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.utils.BackgroundAccessState
import com.pennywiseai.tracker.utils.canReadSms
import com.pennywiseai.tracker.utils.canReceiveSms
import com.pennywiseai.tracker.utils.inAppLanguage
import com.pennywiseai.tracker.utils.isBackgroundRestricted
import com.pennywiseai.tracker.utils.trackingProblem
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * Works out the Plan challenge's days the way Plan does (same rows, same tracking and
 * inbox checks) and redraws the Challenge widget. Enqueued when the stored challenge
 * changes (PennyWiseApplication observes it), with the other widgets via
 * [WidgetRefresher], and on the widget's update period, which also moves it past midnight.
 * It only reads: recording a finished run stays with Plan.
 */
@HiltWorker
class ChallengeWidgetUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferences: UserPreferencesRepository,
    private val loader: PeriodReviewLoader,
    private val categoryRepository: CategoryRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "challenge_widget_update"

        fun enqueueOneShot(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ChallengeWidgetUpdateWorker>().build()
            )
        }
    }

    override suspend fun doWork(): Result = try {
        val ctx = applicationContext
        // Every transaction change asks; with no Challenge widget placed there's nothing to draw.
        if (GlanceAppWidgetManager(ctx).getGlanceIds(ChallengeWidget::class.java).isEmpty()) return Result.success()
        val stored = preferences.challenges.first()
        val categories = categoryRepository.getAllCategories().first()
        val progress = stored.active?.let { run ->
            val today = LocalDate.now()
            // The profile it was started under, whichever is selected now.
            val rows = loader.scopedRows(run.start, maxOf(run.end, today), flowOf(run.profileId)).first()
            val tracking = trackingProblem(
                ctx.canReadSms() && ctx.canReceiveSms(),
                BackgroundAccessState(backgroundRestricted = ctx.isBackgroundRestricted()),
            ) == null
            val readBefore = inboxReadBefore(
                preferences.getLastScanTimestamp().first(),
                preferences.lastScanRecord.first()?.owesFullWindow == true,
            )
            run.namedAsNow(categories)
                .progress(challengeRows(rows, categories.parentNameOf()), today, tracking = tracking, inboxReadBefore = readBefore)
        }
        val language = ctx.inAppLanguage()
        val snapshot = challengeWidgetSnapshot(stored, progress) { name ->
            CategoryMapping.labelRes(name, categories)?.let(language::getString) ?: name
        }
        ChallengeWidgetDataStore.update(ctx, snapshot)
        ChallengeWidget().updateAll(ctx)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
