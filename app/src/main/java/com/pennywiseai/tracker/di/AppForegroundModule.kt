package com.pennywiseai.tracker.di

import android.content.Context
import com.pennywiseai.tracker.PennyWiseApplication
import com.pennywiseai.tracker.core.AppForegroundState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Module
@InstallIn(SingletonComponent::class)
object AppForegroundModule {

    /**
     * Reads [PennyWiseApplication.isAppInForeground] on every call and passes on its
     * [PennyWiseApplication.backgrounded]; false and silent under any other Application, such as a test one.
     */
    @Provides
    fun provideAppForegroundState(@ApplicationContext context: Context): AppForegroundState = object : AppForegroundState {
        private val app get() = context.applicationContext as? PennyWiseApplication
        override fun isInForeground(): Boolean = app?.isAppInForeground == true
        override val backgrounded: Flow<Unit> get() = app?.backgrounded ?: emptyFlow()
    }
}
