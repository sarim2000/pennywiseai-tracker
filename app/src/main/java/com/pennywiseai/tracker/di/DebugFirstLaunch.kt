package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.DebugFirstLaunch
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugFirstLaunchModule {
    @BindsOptionalOf
    abstract fun optionalDebugFirstLaunch(): DebugFirstLaunch
}
