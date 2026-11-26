package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.core.SmsScanRunning
import com.pennywiseai.tracker.worker.SmsScanManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SmsScanRunningModule {

    @Binds
    abstract fun bindSmsScanRunning(impl: SmsScanManager): SmsScanRunning
}
