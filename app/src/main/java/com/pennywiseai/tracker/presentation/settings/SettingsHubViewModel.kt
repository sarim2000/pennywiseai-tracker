package com.pennywiseai.tracker.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.aiaccess.AiSessionState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.WebhookRepository
import com.pennywiseai.tracker.domain.repository.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What the Settings hub shows about the user at a glance: who they are, and the
 * counts under its shortcut tiles. Kept apart from [SettingsViewModel], which every
 * Settings page builds for itself, so a page doesn't start these queries.
 */
@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
    accountBalanceRepository: AccountBalanceRepository,
    ruleRepository: RuleRepository,
    categoryRepository: CategoryRepository,
    webhookRepository: WebhookRepository,
    aiSessionState: AiSessionState,
) : ViewModel() {

    // Data leaves the phone only through an enabled webhook or a running AI access session.
    private val isSharing = combine(
        webhookRepository.observeProfiles().map { profiles -> profiles.any { it.enabled } },
        aiSessionState.session.map { it != null },
    ) { webhooks, ai -> webhooks || ai }

    val state: StateFlow<SettingsHubState> = combine(
        userPreferencesRepository.userPreferences,
        accountBalanceRepository.getAllLatestBalances().map { it.size },
        ruleRepository.getAllRules().map { rules -> rules.count { it.isActive } },
        categoryRepository.getVisibleCategories().map { it.size },
        isSharing,
    ) { prefs, accounts, activeRules, categories, sharing ->
        SettingsHubState(
            name = prefs.userName,
            imageUri = prefs.profileImageUri,
            backgroundColor = prefs.profileBackgroundColor,
            isDarkTheme = prefs.isDarkThemeEnabled,
            baseCurrency = prefs.baseCurrency,
            accountCount = accounts,
            activeRuleCount = activeRules,
            categoryCount = categories,
            isSharing = sharing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsHubState())
}

/** Null counts are still loading; the hub leaves their line out until they arrive. */
data class SettingsHubState(
    val name: String = "",
    val imageUri: String? = null,
    val backgroundColor: Int = 0,
    /** Null follows the system. */
    val isDarkTheme: Boolean? = null,
    val baseCurrency: String? = null,
    val accountCount: Int? = null,
    val activeRuleCount: Int? = null,
    val categoryCount: Int? = null,
    /** A webhook or AI access is on, so some data does leave the phone. */
    val isSharing: Boolean = false,
)
