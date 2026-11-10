package com.pennywiseai.tracker.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.navigation.SettingsSection

/**
 * One page of Settings: a line on what the page is for, then its rows. Each page
 * owns its state, launchers and dialogs, so opening one doesn't start the others'.
 */
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    onNavigateBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, intro) = when (section) {
        SettingsSection.TRACKING -> R.string.settings_tracking_title to R.string.settings_tracking_intro
        SettingsSection.ACCOUNTS -> R.string.settings_accounts_categories_title to R.string.settings_accounts_categories_intro
        SettingsSection.PLANNING -> R.string.settings_planning_title to R.string.settings_planning_intro
        SettingsSection.CURRENCY -> R.string.settings_currency_section to R.string.settings_currency_intro
        SettingsSection.FORMATS -> R.string.settings_formats_title to R.string.settings_formats_intro
        SettingsSection.NOTIFICATIONS -> R.string.settings_notifications_section to R.string.settings_notifications_intro
        SettingsSection.SECURITY -> R.string.settings_security_section to R.string.settings_security_intro
        SettingsSection.DATA -> R.string.settings_data_section to R.string.settings_data_intro
        SettingsSection.AI -> R.string.settings_ai_section to R.string.settings_ai_intro
        SettingsSection.DEVELOPER -> R.string.settings_developer_section to R.string.settings_developer_intro
    }
    SettingsPageScaffold(
        title = stringResource(title),
        intro = stringResource(intro),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        when (section) {
            SettingsSection.TRACKING -> TrackingPage(onNavigate)
            SettingsSection.ACCOUNTS -> AccountsPage(onNavigate)
            SettingsSection.PLANNING -> PlanningPage(onNavigate)
            SettingsSection.CURRENCY -> CurrencyPage(onNavigate)
            SettingsSection.FORMATS -> FormatsPage()
            SettingsSection.NOTIFICATIONS -> NotificationsPage()
            SettingsSection.SECURITY -> SecurityPage()
            SettingsSection.DATA -> DataPage(onNavigate)
            SettingsSection.AI -> AiPage(onNavigate)
            SettingsSection.DEVELOPER -> DeveloperPage(onNavigate)
        }
    }
}
