package com.pennywiseai.tracker.backup.folder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pennywiseai.tracker.worker.ScheduledFolderBackupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledFolderBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<ScheduledFolderBackupWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(computeInitialDelayMs(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "scheduled_folder_backup"

        /**
         * Milliseconds until the next [ScheduledFolderBackupConstants.BACKUP_HOUR_OF_DAY]:00
         * in the device's default timezone.
         */
        fun computeInitialDelayMs(
            now: LocalDateTime = LocalDateTime.now(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): Long {
            val targetTime = LocalTime.of(
                ScheduledFolderBackupConstants.BACKUP_HOUR_OF_DAY,
                0
            )
            var nextRun = now.toLocalDate().atTime(targetTime)
            if (!nextRun.isAfter(now)) {
                nextRun = nextRun.plusDays(1)
            }
            return Duration.between(now.atZone(zoneId).toInstant(), nextRun.atZone(zoneId).toInstant())
                .toMillis()
        }
    }
}
