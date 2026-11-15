package com.pennywiseai.tracker.di

import android.content.Context
import com.pennywiseai.tracker.PennyWiseApplication
import com.pennywiseai.tracker.core.AppForegroundState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppForegroundModule {

    /** Reads [PennyWiseApplication.isAppInForeground] on every call; false under any other Application, such as a test one. */
    @Provides
    fun provideAppForegroundState(@ApplicationContext context: Context): AppForegroundState =
        AppForegroundState { (context.applicationContext as? PennyWiseApplication)?.isAppInForeground == true }
}
