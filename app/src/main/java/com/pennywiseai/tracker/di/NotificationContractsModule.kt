package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.data.manager.ConscienceNotificationActions
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsAlerts
import com.pennywiseai.tracker.receiver.ReceiverConscienceNotificationActions
import com.pennywiseai.tracker.receiver.ReceiverUnrecognizedSmsAlerts
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the notification contracts `data` declares to the receivers that own those notifications. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationContractsModule {

    @Binds
    abstract fun bindConscienceNotificationActions(
        impl: ReceiverConscienceNotificationActions
    ): ConscienceNotificationActions

    @Binds
    abstract fun bindUnrecognizedSmsAlerts(impl: ReceiverUnrecognizedSmsAlerts): UnrecognizedSmsAlerts
}
