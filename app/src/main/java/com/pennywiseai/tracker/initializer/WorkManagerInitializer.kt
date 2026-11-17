package com.pennywiseai.tracker.initializer

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializes WorkManager with Hilt support.
 */
@Singleton
class WorkManagerInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workerFactory: HiltWorkerFactory
) {

    /**
     * Initializes WorkManager with custom configuration.
     * This must be called before any work is enqueued.
     */
    fun initialize() {
        val config = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

        WorkManager.initialize(context, config)
    }
}
