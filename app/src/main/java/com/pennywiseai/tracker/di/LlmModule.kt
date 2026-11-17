package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.data.service.LlmServiceImpl
import com.pennywiseai.tracker.domain.service.LlmService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmService(
        llmServiceImpl: LlmServiceImpl
    ): LlmService
}
