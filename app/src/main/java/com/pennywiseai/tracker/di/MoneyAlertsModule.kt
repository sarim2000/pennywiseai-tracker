package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.data.manager.MoneyAlerts
import com.pennywiseai.tracker.worker.WorkManagerMoneyAlerts
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MoneyAlertsModule {

    @Binds
    abstract fun bindMoneyAlerts(impl: WorkManagerMoneyAlerts): MoneyAlerts
}
