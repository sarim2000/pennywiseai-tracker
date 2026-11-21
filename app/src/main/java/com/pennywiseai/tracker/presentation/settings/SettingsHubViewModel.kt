package com.pennywiseai.tracker.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.aiaccess.AiSessionState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.WebhookRepository
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.utils.ProfilePhotos
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * What the Settings hub shows about the user at a glance: who they are, and the
 * counts under its shortcut tiles. Kept apart from [SettingsViewModel], which every
 * Settings page builds for itself, so a page doesn't start these queries.
 */
@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
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
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsHubState())

    // Imports and saves take turns, so a save's sweep can't delete a photo a newer
    // editor has just imported.
    private val photoLock = Mutex()

    suspend fun importPhoto(source: Uri): String? = photoLock.withLock { ProfilePhotos.import(context, source) }

    fun saveProfile(name: String, imageUri: String?, backgroundColor: Int) {
        viewModelScope.launch {
            photoLock.withLock {
                userPreferencesRepository.updateProfile(name.trim(), imageUri, backgroundColor)
                // ponytail: photos picked then abandoned are swept on the next save, not on cancel.
                withContext(Dispatchers.IO) { ProfilePhotos.sweep(context.filesDir, imageUri) }
            }
        }
    }
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
    /** A webhook or AI access is on, so some data does leave the phone. Null until known. */
    val isSharing: Boolean? = null,
    /** False until the stored profile has been read; editing waits for it. */
    val isLoaded: Boolean = false,
)
