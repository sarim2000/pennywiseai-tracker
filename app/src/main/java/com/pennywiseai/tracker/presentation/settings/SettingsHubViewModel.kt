package com.pennywiseai.tracker.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.aiaccess.AiSessionState
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.WebhookRepository
import com.pennywiseai.tracker.domain.repository.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsHubState())

    /**
     * Copies a picked photo into app storage (the picker's grant doesn't outlive the
     * process) and returns its file URI, or null if it can't be read. Each pick gets
     * its own file name so the image loader doesn't serve the previous photo from cache.
     */
    suspend fun importPhoto(source: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, "$PHOTO_PREFIX${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            file.toUri().toString()
        } catch (_: Exception) {
            null
        }
    }

    fun saveProfile(name: String, imageUri: String?, backgroundColor: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateUserName(name.trim())
            userPreferencesRepository.updateProfileImageUri(imageUri)
            userPreferencesRepository.updateProfileBackgroundColor(backgroundColor)
            // ponytail: photos picked then abandoned are swept on the next save, not on cancel.
            withContext(Dispatchers.IO) {
                context.filesDir.listFiles { f -> f.name == ONBOARDING_PHOTO || f.name.startsWith(PHOTO_PREFIX) }
                    ?.filter { it.toUri().toString() != imageUri }
                    ?.forEach { it.delete() }
            }
        }
    }

    private companion object {
        const val PHOTO_PREFIX = "profile_photo_"

        /** Where onboarding saves its pick. */
        const val ONBOARDING_PHOTO = "profile_image.jpg"
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
)
