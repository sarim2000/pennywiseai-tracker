package com.pennywiseai.tracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
    produceMigrations = { _ ->
        listOf(
            object : androidx.datastore.core.DataMigration<Preferences> {
                override suspend fun cleanUp() {}
                
                override suspend fun migrate(currentData: Preferences): Preferences {
                    val prefs = currentData.toMutablePreferences()
                    val hasCompletedOnboarding = prefs[booleanPreferencesKey("has_completed_onboarding")] == true
                    val hasAllTimeSet = prefs.contains(booleanPreferencesKey("sms_scan_all_time"))
                    
                    if (hasCompletedOnboarding && !hasAllTimeSet) {
                        prefs[booleanPreferencesKey("sms_scan_all_time")] = false
                    }
                    return prefs
                }
                
                override suspend fun shouldMigrate(currentData: Preferences): Boolean {
                    val hasCompletedOnboarding = currentData[booleanPreferencesKey("has_completed_onboarding")] == true
                    val hasAllTimeSet = currentData.contains(booleanPreferencesKey("sms_scan_all_time"))
                    return hasCompletedOnboarding && !hasAllTimeSet
                }
            }
        )
    }
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val IS_AMOLED_MODE = booleanPreferencesKey("is_amoled_mode")
        val APP_FONT = stringPreferencesKey("app_font")
        val HAS_SKIPPED_SMS_PERMISSION = booleanPreferencesKey("has_skipped_sms_permission")
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val HAS_SHOWN_SCAN_TUTORIAL = booleanPreferencesKey("has_shown_scan_tutorial")
        val ACTIVE_DOWNLOAD_ID = longPreferencesKey("active_download_id")
        val SMS_SCAN_MONTHS = intPreferencesKey("sms_scan_months")
        val SMS_SCAN_ALL_TIME = booleanPreferencesKey("sms_scan_all_time")
        val SMS_SCAN_USE_CUSTOM_DATE = booleanPreferencesKey("sms_scan_use_custom_date")
        val SMS_SCAN_CUSTOM_DATE = longPreferencesKey("sms_scan_custom_date")
        val LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
        val LAST_SCAN_PERIOD = intPreferencesKey("last_scan_period")
        val BASE_CURRENCY = stringPreferencesKey("base_currency")

        // App Lock preferences
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_TIMEOUT_MINUTES = intPreferencesKey("app_lock_timeout_minutes")
        val LAST_AUTH_TIMESTAMP = longPreferencesKey("last_auth_timestamp")

        // In-App Review preferences
        val FIRST_LAUNCH_TIME = longPreferencesKey("first_launch_time")
        val HAS_SHOWN_REVIEW_PROMPT = booleanPreferencesKey("has_shown_review_prompt")
        val LAST_REVIEW_PROMPT_TIME = longPreferencesKey("last_review_prompt_time")

        // Feature discovery
        val HAS_USED_FULL_RESYNC = booleanPreferencesKey("has_used_full_resync")

        // Pro tier — UI-cached entitlement to avoid first-frame flicker
        // while the BillingClient connects.
        val PRO_CACHED_IS_PRO = booleanPreferencesKey("pro_cached_is_pro")

        // Pro tier — statement-import monthly quota tracking.
        val LAST_STATEMENT_IMPORT_AT = longPreferencesKey("last_statement_import_at")

        // What's New feature
        val LAST_SEEN_APP_VERSION = stringPreferencesKey("last_seen_app_version")

        // Monthly Budget
        val MONTHLY_BUDGET_LIMIT = stringPreferencesKey("monthly_budget_limit")

        // Unified Currency Mode
        val UNIFIED_CURRENCY_MODE = booleanPreferencesKey("unified_currency_mode")
        val DISPLAY_CURRENCY = stringPreferencesKey("display_currency")

        // Budget Groups Migration
        val HAS_MIGRATED_TO_BUDGET_GROUPS = booleanPreferencesKey("has_migrated_to_budget_groups")

        // Balance Visibility
        val BALANCE_HIDDEN = booleanPreferencesKey("balance_hidden")

        // Selected Profile
        val SELECTED_PROFILE_ID = longPreferencesKey("selected_profile_id")

        // Blur Effects
        val BLUR_EFFECTS_ENABLED = booleanPreferencesKey("blur_effects_enabled")

        // Navigation Bar Style
        val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")

        // Analytics Chart Type
        val ANALYTICS_CHART_TYPE = stringPreferencesKey("analytics_chart_type")

        // Cover Style
        val COVER_STYLE = stringPreferencesKey("cover_style")

        // Profile & Onboarding
        val USER_NAME = stringPreferencesKey("user_name")
        val PROFILE_IMAGE_URI = stringPreferencesKey("profile_image_uri")
        val PROFILE_BACKGROUND_COLOR = intPreferencesKey("profile_background_color")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val MAIN_ACCOUNT_KEY = stringPreferencesKey("main_account_key")
        // True once the user explicitly picks a currency in the Settings selector.
        // While true, the main-account-derived currency must not override their choice.
        val BASE_CURRENCY_USER_SET = booleanPreferencesKey("base_currency_user_set")

        // UPI VPA → contact name lookup (opt-in; gated by READ_CONTACTS).
        val USE_CONTACTS_FOR_VPA = booleanPreferencesKey("use_contacts_for_vpa")

        // Scheduled folder backup (SAF tree URI; shared by standard and F-Droid).
        val SCHEDULED_FOLDER_BACKUP_ENABLED = booleanPreferencesKey("scheduled_folder_backup_enabled")
        val SCHEDULED_FOLDER_BACKUP_TREE_URI = stringPreferencesKey("scheduled_folder_backup_tree_uri")
        val SCHEDULED_FOLDER_BACKUP_LAST_TIMESTAMP = longPreferencesKey("scheduled_folder_backup_last_timestamp")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            UserPreferences(
                isDarkThemeEnabled = preferences[PreferencesKeys.DARK_THEME_ENABLED],
                isDynamicColorEnabled = preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED] ?: false,
                themeStyle = preferences[PreferencesKeys.THEME_STYLE]?.let {
                    try { ThemeStyle.valueOf(it) } catch (_: Exception) { ThemeStyle.DYNAMIC }
                } ?: ThemeStyle.DYNAMIC,
                accentColor = preferences[PreferencesKeys.ACCENT_COLOR]?.let {
                    try { AccentColor.valueOf(it) } catch (_: Exception) { AccentColor.SURFACE }
                } ?: AccentColor.SURFACE,
                isAmoledMode = preferences[PreferencesKeys.IS_AMOLED_MODE] ?: false,
                appFont = preferences[PreferencesKeys.APP_FONT]?.let {
                    try { AppFont.valueOf(it) } catch (_: Exception) { AppFont.SYSTEM }
                } ?: AppFont.SYSTEM,
                hasSkippedSmsPermission = preferences[PreferencesKeys.HAS_SKIPPED_SMS_PERMISSION] ?: false,
                isDeveloperModeEnabled = preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false,
                hasShownScanTutorial = preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] ?: false,
                smsScanMonths = preferences[PreferencesKeys.SMS_SCAN_MONTHS] ?: 3,
                smsScanAllTime = preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] ?: true,
                baseCurrency = preferences[PreferencesKeys.BASE_CURRENCY] ?: "INR",
                unifiedCurrencyMode = preferences[PreferencesKeys.UNIFIED_CURRENCY_MODE] ?: false,
                displayCurrency = preferences[PreferencesKeys.DISPLAY_CURRENCY]
                    ?: preferences[PreferencesKeys.BASE_CURRENCY] ?: "INR",
                blurEffectsEnabled = preferences[PreferencesKeys.BLUR_EFFECTS_ENABLED] ?: true,
                navBarStyle = preferences[PreferencesKeys.NAV_BAR_STYLE]?.let {
                    try { NavBarStyle.valueOf(it) } catch (_: Exception) { NavBarStyle.FLOATING }
                } ?: NavBarStyle.FLOATING,
                coverStyle = preferences[PreferencesKeys.COVER_STYLE]?.let {
                    try { CoverStyle.valueOf(it) } catch (_: Exception) { CoverStyle.AURORA }
                } ?: CoverStyle.AURORA,
                userName = preferences[PreferencesKeys.USER_NAME] ?: "User",
                profileImageUri = preferences[PreferencesKeys.PROFILE_IMAGE_URI]
                    ?: if (preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] == true) "avatar://0" else null,
                profileBackgroundColor = preferences[PreferencesKeys.PROFILE_BACKGROUND_COLOR] ?: 0,
                hasCompletedOnboarding = preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false,
                mainAccountKey = preferences[PreferencesKeys.MAIN_ACCOUNT_KEY]
            )
        }

    val baseCurrency: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BASE_CURRENCY] ?: "INR"
        }

    val unifiedCurrencyMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.UNIFIED_CURRENCY_MODE] ?: false
        }

    val displayCurrency: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DISPLAY_CURRENCY]
                ?: preferences[PreferencesKeys.BASE_CURRENCY] ?: "INR"
        }

    val isDeveloperModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false
        }

    suspend fun updateDarkThemeEnabled(enabled: Boolean?) {
        context.dataStore.edit { preferences ->
            if (enabled == null) {
                preferences.remove(PreferencesKeys.DARK_THEME_ENABLED)
            } else {
                preferences[PreferencesKeys.DARK_THEME_ENABLED] = enabled
            }
        }
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    suspend fun updateThemeStyle(themeStyle: ThemeStyle) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_STYLE] = themeStyle.name
        }
    }

    suspend fun updateAccentColor(accentColor: AccentColor) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = accentColor.name
        }
    }

    suspend fun updateAmoledMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_AMOLED_MODE] = enabled
        }
    }

    suspend fun updateAppFont(appFont: AppFont) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_FONT] = appFont.name
        }
    }

    suspend fun updateSkippedSmsPermission(skipped: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SKIPPED_SMS_PERMISSION] = skipped
        }
    }
    
    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] = enabled
        }
    }
    
    suspend fun updateSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT] = prompt
        }
    }
    
    fun getSystemPrompt(): Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT]
        }
    
    suspend fun markScanTutorialShown() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] = true
        }
    }
    
    suspend fun saveActiveDownloadId(id: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_DOWNLOAD_ID] = id
        }
    }
    
    suspend fun getActiveDownloadId(): Long? {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.ACTIVE_DOWNLOAD_ID] }
            .first()
    }
    
    suspend fun clearActiveDownloadId() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ACTIVE_DOWNLOAD_ID)
        }
    }
    
    val smsScanMonths: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_MONTHS] ?: 3 // Default to 3 months
        }
    
    suspend fun updateSmsScanMonths(months: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_MONTHS] = months
        }
    }

    suspend fun getSmsScanMonths(): Int {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_MONTHS] ?: 3 }
            .first()
    }

    val smsScanAllTime: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] ?: true
        }

    suspend fun updateSmsScanAllTime(allTime: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] = allTime
        }
    }

    suspend fun getSmsScanAllTime(): Boolean {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] ?: true }
            .first()
    }

    val smsScanUseCustomDate: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_USE_CUSTOM_DATE] ?: false
        }

    suspend fun updateSmsScanUseCustomDate(useCustomDate: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_USE_CUSTOM_DATE] = useCustomDate
        }
    }

    suspend fun getSmsScanUseCustomDate(): Boolean {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_USE_CUSTOM_DATE] ?: false }
            .first()
    }

    val smsScanCustomDate: Flow<Long?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_CUSTOM_DATE] }

    suspend fun updateSmsScanCustomDate(dateMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_CUSTOM_DATE] = dateMillis
        }
    }

    suspend fun getSmsScanCustomDate(): Long? {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_CUSTOM_DATE] }
            .first()
    }
    
    suspend fun setLastScanTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIMESTAMP] = timestamp
        }
    }
    
    suspend fun setLastScanPeriod(period: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_PERIOD] = period
        }
    }
    
    suspend fun setFirstLaunchTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIRST_LAUNCH_TIME] = timestamp
        }
    }
    
    suspend fun hasShownReviewPrompt(): Boolean {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] ?: false }
            .first()
    }
    
    suspend fun markReviewPromptShown() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] = true
            preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] = System.currentTimeMillis()
        }
    }
    
    // Flow methods for backup/restore
    fun getLastScanTimestamp(): Flow<Long?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.LAST_SCAN_TIMESTAMP] }
    
    fun getLastScanPeriod(): Flow<Int?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.LAST_SCAN_PERIOD] }
    
    fun getFirstLaunchTime(): Flow<Long?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.FIRST_LAUNCH_TIME] }
    
    fun getHasShownReviewPrompt(): Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] ?: false }
    
    fun getLastReviewPromptTime(): Flow<Long?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] }
    
    // Update methods for import
    suspend fun updateDarkTheme(enabled: Boolean?) {
        updateDarkThemeEnabled(enabled)
    }
    
    suspend fun updateDynamicColor(enabled: Boolean) {
        updateDynamicColorEnabled(enabled)
    }
    
    suspend fun updateHasSkippedSmsPermission(skipped: Boolean) {
        updateSkippedSmsPermission(skipped)
    }
    
    suspend fun updateDeveloperMode(enabled: Boolean) {
        setDeveloperModeEnabled(enabled)
    }
    
    suspend fun updateLastScanTimestamp(timestamp: Long) {
        setLastScanTimestamp(timestamp)
    }
    
    suspend fun updateLastScanPeriod(period: Int) {
        setLastScanPeriod(period)
    }
    
    suspend fun updateFirstLaunchTime(timestamp: Long) {
        setFirstLaunchTime(timestamp)
    }
    
    suspend fun updateHasShownScanTutorial(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] = shown
        }
    }
    
    suspend fun updateHasShownReviewPrompt(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] = shown
        }
    }
    
    suspend fun updateLastReviewPromptTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] = timestamp
        }
    }

    // App Lock methods
    val isAppLockEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false
        }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
        }
    }

    /**
     * Atomically updates both app lock enabled state and authentication timestamp.
     * This prevents race conditions where the flow sees enabled=true but timestamp=0.
     */
    suspend fun setAppLockEnabledWithTimestamp(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
            if (enabled) {
                preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
    }

    val appLockTimeoutMinutes: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] ?: 1 // Default to 1 minute
        }

    suspend fun setAppLockTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] = minutes
        }
    }

    /**
     * Atomically updates timeout and authentication timestamp.
     * This prevents immediate lock when changing timeout by resetting the auth time.
     */
    suspend fun setAppLockTimeoutWithTimestamp(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] = minutes
            preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun getAppLockTimeoutMinutes(): Int {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] ?: 1 }
            .first()
    }

    suspend fun setLastAuthTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = timestamp
        }
    }

    suspend fun getLastAuthTimestamp(): Long {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] ?: 0L }
            .first()
    }

    fun getLastAuthTimestampFlow(): Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] ?: 0L
        }

    // Feature discovery - Full resync hint
    val hasUsedFullResync: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HAS_USED_FULL_RESYNC] ?: false
        }

    suspend fun markFullResyncUsed() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_USED_FULL_RESYNC] = true
        }
    }

    // Pro tier — UI-cached entitlement so first frame after cold start
    // doesn't flicker `false → true` while BillingClient connects. Real
    // source of truth is Play; this is a hint only.
    val proCachedIsPro: Flow<Boolean> = context.dataStore.data
        .map { it[PreferencesKeys.PRO_CACHED_IS_PRO] ?: false }

    suspend fun setProCachedIsPro(isPro: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRO_CACHED_IS_PRO] = isPro
        }
    }

    /**
     * Epoch-millis of the last successful PDF statement import, or null if
     * the user has never imported. Consumed by the statement-import gate —
     * free users get one import per calendar month, so this single
     * timestamp is enough (no per-month counter needed since the limit is 1).
     */
    val lastStatementImportAt: Flow<Long?> = context.dataStore.data
        .map { it[PreferencesKeys.LAST_STATEMENT_IMPORT_AT] }

    suspend fun markStatementImported(epochMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_STATEMENT_IMPORT_AT] = epochMillis
        }
    }

    // What's New feature
    suspend fun getLastSeenAppVersion(): String? {
        return context.dataStore.data
            .map { preferences -> preferences[PreferencesKeys.LAST_SEEN_APP_VERSION] }
            .first()
    }

    suspend fun setLastSeenAppVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SEEN_APP_VERSION] = version
        }
    }

    // Unified Currency Mode
    suspend fun setUnifiedCurrencyMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNIFIED_CURRENCY_MODE] = enabled
        }
    }

    suspend fun setDisplayCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISPLAY_CURRENCY] = currency
        }
    }

    // Budget Groups Migration
    val hasMigratedToBudgetGroups: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HAS_MIGRATED_TO_BUDGET_GROUPS] ?: false
        }

    suspend fun setHasMigratedToBudgetGroups(migrated: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_MIGRATED_TO_BUDGET_GROUPS] = migrated
        }
    }

    // Monthly Budget
    val monthlyBudgetLimit: Flow<java.math.BigDecimal?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MONTHLY_BUDGET_LIMIT]?.let { java.math.BigDecimal(it) }
        }

    suspend fun updateMonthlyBudgetLimit(amount: java.math.BigDecimal?) {
        context.dataStore.edit { preferences ->
            if (amount == null) {
                preferences.remove(PreferencesKeys.MONTHLY_BUDGET_LIMIT)
            } else {
                preferences[PreferencesKeys.MONTHLY_BUDGET_LIMIT] = amount.toPlainString()
            }
        }
    }

    /**
     * Explicit base-currency choice from the Settings currency selector. Marks the
     * currency as user-set so the main account can no longer override it.
     */
    suspend fun updateBaseCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASE_CURRENCY] = currency
            preferences[PreferencesKeys.BASE_CURRENCY_USER_SET] = true
        }
    }

    /**
     * Sets the base currency derived from the user's main account, but only if the
     * user hasn't explicitly chosen one via the Settings currency selector. The
     * explicit selector always wins.
     */
    suspend fun applyMainAccountCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            if (preferences[PreferencesKeys.BASE_CURRENCY_USER_SET] != true) {
                preferences[PreferencesKeys.BASE_CURRENCY] = currency
            }
        }
    }

    // Balance Visibility
    val isBalanceHidden: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BALANCE_HIDDEN] ?: false
        }

    suspend fun setBalanceHidden(hidden: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BALANCE_HIDDEN] = hidden
        }
    }

    // Replace UPI VPAs with matching contact names at display time.
    // Off by default — turning on prompts for READ_CONTACTS.
    val useContactsForVpa: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USE_CONTACTS_FOR_VPA] ?: false
        }

    suspend fun setUseContactsForVpa(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_CONTACTS_FOR_VPA] = enabled
        }
    }

    // Selected Profile
    val selectedProfileId: Flow<Long?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.SELECTED_PROFILE_ID] }

    suspend fun updateSelectedProfileId(profileId: Long?) {
        context.dataStore.edit { preferences ->
            if (profileId == null) {
                preferences.remove(PreferencesKeys.SELECTED_PROFILE_ID)
            } else {
                preferences[PreferencesKeys.SELECTED_PROFILE_ID] = profileId
            }
        }
    }

    // Blur Effects
    val blurEffectsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BLUR_EFFECTS_ENABLED] ?: true
        }

    suspend fun updateBlurEffectsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLUR_EFFECTS_ENABLED] = enabled
        }
    }

    // Navigation Bar Style
    suspend fun updateNavBarStyle(style: NavBarStyle) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAV_BAR_STYLE] = style.name
        }
    }

    // Analytics Chart Type
    suspend fun saveAnalyticsChartType(chartType: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANALYTICS_CHART_TYPE] = chartType
        }
    }

    fun getAnalyticsChartType(): Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.ANALYTICS_CHART_TYPE] }

    // Cover Style
    suspend fun updateCoverStyle(style: CoverStyle) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COVER_STYLE] = style.name
        }
    }

    // Profile & Onboarding
    suspend fun updateUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_NAME] = name
        }
    }

    suspend fun updateProfileImageUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(PreferencesKeys.PROFILE_IMAGE_URI)
            } else {
                preferences[PreferencesKeys.PROFILE_IMAGE_URI] = uri
            }
        }
    }

    suspend fun updateProfileBackgroundColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROFILE_BACKGROUND_COLOR] = color
        }
    }

    suspend fun updateHasCompletedOnboarding(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    suspend fun updateMainAccountKey(accountKey: String?) {
        context.dataStore.edit { preferences ->
            if (accountKey == null) {
                preferences.remove(PreferencesKeys.MAIN_ACCOUNT_KEY)
            } else {
                preferences[PreferencesKeys.MAIN_ACCOUNT_KEY] = accountKey
            }
        }
    }

    val scheduledFolderBackupEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_ENABLED] ?: false
        }

    val scheduledFolderBackupTreeUri: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_TREE_URI]
        }

    val scheduledFolderBackupLastTimestamp: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_LAST_TIMESTAMP]
        }

    suspend fun isScheduledFolderBackupEnabled(): Boolean {
        return scheduledFolderBackupEnabled.first()
    }

    suspend fun getScheduledFolderBackupTreeUri(): String? {
        return scheduledFolderBackupTreeUri.first()
    }

    suspend fun setScheduledFolderBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setScheduledFolderBackupTreeUri(treeUri: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_TREE_URI] = treeUri
        }
    }

    suspend fun setScheduledFolderBackupLastTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_LAST_TIMESTAMP] = timestamp
        }
    }

    suspend fun clearScheduledFolderBackupState() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.SCHEDULED_FOLDER_BACKUP_ENABLED)
            preferences.remove(PreferencesKeys.SCHEDULED_FOLDER_BACKUP_TREE_URI)
            preferences.remove(PreferencesKeys.SCHEDULED_FOLDER_BACKUP_LAST_TIMESTAMP)
        }
    }
}

data class UserPreferences(
    val isDarkThemeEnabled: Boolean? = null, // null means follow system
    val isDynamicColorEnabled: Boolean = false, // Default to custom brand colors
    val themeStyle: ThemeStyle = ThemeStyle.DYNAMIC,
    val accentColor: AccentColor = AccentColor.SURFACE,
    val isAmoledMode: Boolean = false,
    val appFont: AppFont = AppFont.SYSTEM,
    val hasSkippedSmsPermission: Boolean = false,
    val isDeveloperModeEnabled: Boolean = false,
    val hasShownScanTutorial: Boolean = false,
    val smsScanMonths: Int = 3,
    val smsScanAllTime: Boolean = true,
    val baseCurrency: String = "INR",
    val unifiedCurrencyMode: Boolean = false,
    val displayCurrency: String = "INR",
    val blurEffectsEnabled: Boolean = true,
    val navBarStyle: NavBarStyle = NavBarStyle.FLOATING,
    val coverStyle: CoverStyle = CoverStyle.AURORA,
    val userName: String = "User",
    val profileImageUri: String? = null,
    val profileBackgroundColor: Int = 0,
    val hasCompletedOnboarding: Boolean = false,
    val mainAccountKey: String? = null,
    val selectedProfileId: Long? = null
)