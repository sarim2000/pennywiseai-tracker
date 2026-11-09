package com.pennywiseai.tracker.ui.viewmodel

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.preferences.AccentColor
import com.pennywiseai.tracker.data.preferences.AppFont
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.data.preferences.ThemeStyle
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val themeUiState: StateFlow<ThemeUiState> = userPreferencesRepository.userPreferences
        .map { preferences ->
            ThemeUiState(
                isLoading = false,
                isDarkTheme = preferences.isDarkThemeEnabled,
                isDynamicColorEnabled = preferences.isDynamicColorEnabled,
                themeStyle = preferences.themeStyle,
                accentColor = preferences.accentColor,
                isAmoledMode = preferences.isAmoledMode,
                appFont = preferences.appFont,
                hasSkippedSmsPermission = preferences.hasSkippedSmsPermission,
                blurEffectsEnabled = preferences.blurEffectsEnabled,
                navBarStyle = preferences.navBarStyle,
                hiddenNavTabs = preferences.hiddenNavTabs,
                coverStyle = preferences.coverStyle,
                hasCompletedOnboarding = preferences.hasCompletedOnboarding,
                userName = preferences.userName,
                profileImageUri = preferences.profileImageUri,
                profileBackgroundColor = preferences.profileBackgroundColor
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeUiState()
        )

    fun updateDarkTheme(enabled: Boolean?) {
        viewModelScope.launch {
            userPreferencesRepository.updateDarkThemeEnabled(enabled)
        }
    }

    fun updateDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateDynamicColorEnabled(enabled)
        }
    }

    fun updateThemeStyle(themeStyle: ThemeStyle) {
        viewModelScope.launch {
            userPreferencesRepository.updateThemeStyle(themeStyle)
        }
    }

    fun updateAccentColor(accentColor: AccentColor) {
        viewModelScope.launch {
            userPreferencesRepository.updateAccentColor(accentColor)
        }
    }

    fun updateAmoledMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateAmoledMode(enabled)
        }
    }

    fun updateAppFont(appFont: AppFont) {
        viewModelScope.launch {
            userPreferencesRepository.updateAppFont(appFont)
        }
    }

    fun updateBlurEffects(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateBlurEffectsEnabled(enabled)
        }
    }

    fun updateNavBarStyle(style: NavBarStyle) {
        viewModelScope.launch {
            userPreferencesRepository.updateNavBarStyle(style)
        }
    }

    fun setNavTabVisible(route: String, visible: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNavTabHidden(route, hidden = !visible)
        }
    }

    fun updateCoverStyle(style: CoverStyle) {
        viewModelScope.launch {
            userPreferencesRepository.updateCoverStyle(style)
        }
    }

    fun markOnboardingCompleted() {
        viewModelScope.launch {
            userPreferencesRepository.updateHasCompletedOnboarding(true)
        }
    }
}

data class ThemeUiState(
    val isLoading: Boolean = true,
    val isDarkTheme: Boolean? = null, // null = follow system
    val isDynamicColorEnabled: Boolean = false,
    val themeStyle: ThemeStyle = ThemeStyle.DYNAMIC,
    val accentColor: AccentColor = AccentColor.SURFACE,
    val isAmoledMode: Boolean = false,
    val appFont: AppFont = AppFont.DEFAULT,
    val hasSkippedSmsPermission: Boolean = false,
    val blurEffectsEnabled: Boolean = true,
    val navBarStyle: NavBarStyle = NavBarStyle.NORMAL,
    val hiddenNavTabs: Set<String> = emptySet(),
    val coverStyle: CoverStyle = CoverStyle.AURORA,
    val hasCompletedOnboarding: Boolean = false,
    val userName: String = "User",
    val profileImageUri: String? = null,
    val profileBackgroundColor: Int = 0
)

/**
 * [PennyWiseTheme] wearing the user's saved appearance (theme, colours, font) — for
 * activities outside the main app shell (Quick Add, the category picker, widget setup).
 * Draws nothing until preferences load, so a saved font never flashes the default first.
 */
@Composable
fun PennyWiseUserTheme(content: @Composable () -> Unit) {
    val state by hiltViewModel<ThemeViewModel>().themeUiState.collectAsStateWithLifecycle()
    if (state.isLoading) return
    PennyWiseTheme(
        darkTheme = state.isDarkTheme ?: isSystemInDarkTheme(),
        dynamicColor = state.isDynamicColorEnabled,
        themeStyle = state.themeStyle,
        accentColor = state.accentColor,
        isAmoledMode = state.isAmoledMode,
        appFont = state.appFont,
        blurEffects = state.blurEffectsEnabled,
        content = content,
    )
}
