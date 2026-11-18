package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.data.preferences.BackupPreferences
import com.pennywiseai.tracker.data.preferences.BillingPreferences
import com.pennywiseai.tracker.data.preferences.BudgetPreferences
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.data.preferences.NotificationPreferences
import com.pennywiseai.tracker.data.preferences.PrivacyPreferences
import com.pennywiseai.tracker.data.preferences.RecurringSuggestionPreferences
import com.pennywiseai.tracker.data.preferences.ScanPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Every focused preference contract resolves to the one [UserPreferencesRepository] singleton. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindCurrencyPreferences(impl: UserPreferencesRepository): CurrencyPreferences

    @Binds
    @Singleton
    abstract fun bindBudgetPreferences(impl: UserPreferencesRepository): BudgetPreferences

    @Binds
    @Singleton
    abstract fun bindScanPreferences(impl: UserPreferencesRepository): ScanPreferences

    @Binds
    @Singleton
    abstract fun bindPrivacyPreferences(impl: UserPreferencesRepository): PrivacyPreferences

    @Binds
    @Singleton
    abstract fun bindNotificationPreferences(impl: UserPreferencesRepository): NotificationPreferences

    @Binds
    @Singleton
    abstract fun bindBackupPreferences(impl: UserPreferencesRepository): BackupPreferences

    @Binds
    @Singleton
    abstract fun bindBillingPreferences(impl: UserPreferencesRepository): BillingPreferences

    @Binds
    @Singleton
    abstract fun bindRecurringSuggestionPreferences(impl: UserPreferencesRepository): RecurringSuggestionPreferences
}
