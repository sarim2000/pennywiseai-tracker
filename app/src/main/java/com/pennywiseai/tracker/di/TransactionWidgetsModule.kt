package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.core.TransactionWidgets
import com.pennywiseai.tracker.widget.WorkManagerTransactionWidgets
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionWidgetsModule {

    @Binds
    abstract fun bindTransactionWidgets(impl: WorkManagerTransactionWidgets): TransactionWidgets
}
