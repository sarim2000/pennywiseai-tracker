package com.pennywiseai.tracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pennywiseai.tracker.domain.spending.BudgetAlertTier
import com.pennywiseai.tracker.domain.spending.MoneyAlertRules
import com.pennywiseai.tracker.data.share.ShareCardConfig
import com.pennywiseai.tracker.data.share.ShareHero
import com.pennywiseai.tracker.data.share.SharePeriod
import com.pennywiseai.tracker.data.preferences.HomeSection
import com.pennywiseai.tracker.data.preferences.HomeSectionLayout
import com.pennywiseai.tracker.domain.conscience.ConscienceCadence
import com.pennywiseai.tracker.domain.conscience.ConscienceCandidate
import com.pennywiseai.tracker.domain.conscience.ConscienceChoice
import com.pennywiseai.tracker.widget.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** The one DataStore file behind [UserPreferencesRepository]. Renaming it loses every setting. */
internal const val USER_PREFERENCES_STORE_NAME = "user_preferences"

/** Users who onboarded before all-time scanning existed keep the scan window they had. */
internal object SmsScanAllTimeMigration : androidx.datastore.core.DataMigration<Preferences> {
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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCES_STORE_NAME,
    produceMigrations = { _ -> listOf(SmsScanAllTimeMigration) }
)

/**
 * Whether the evening recap is on for someone who never touched its switch:
 * off, an opt-in from Settings → Notifications. The single source of that
 * default: the pref, the Settings switch and the scheduler all read it.
 */
const val DAILY_SUMMARY_DEFAULT = false

@Singleton
open class UserPreferencesRepository internal constructor(
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    // Production always takes the delegate: a second DataStore on the same file throws.
    // The internal constructor exists so tests can give each case its own empty file.
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, context.dataStore)

    internal object PreferencesKeys {
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val IS_AMOLED_MODE = booleanPreferencesKey("is_amoled_mode")
        val APP_FONT = stringPreferencesKey("app_font")
        val HAS_SKIPPED_SMS_PERMISSION = booleanPreferencesKey("has_skipped_sms_permission")
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val COUNT_CREDIT_AS_EXPENSE = booleanPreferencesKey("count_credit_as_expense")
        val DAILY_SUMMARY_ENABLED = booleanPreferencesKey("daily_summary_enabled")
        val BACKGROUND_OEM_STEP_CONFIRMED_ON = stringPreferencesKey("background_oem_step_confirmed_on")
        val BUDGET_ALERTS_ENABLED = booleanPreferencesKey("budget_alerts_enabled")
        val WEEKLY_SUMMARY_ENABLED = booleanPreferencesKey("weekly_summary_enabled")
        val WEEKLY_SUMMARY_LAST_WEEK = stringPreferencesKey("weekly_summary_last_week")
        val REVIEW_PROMPT_HANDLED_YEAR = stringPreferencesKey("review_prompt_handled_year")
        val CONSCIENCE_ENABLED = booleanPreferencesKey("conscience_enabled")
        val CONSCIENCE_LOG = stringSetPreferencesKey("conscience_log")
        val CONSCIENCE_RETIRED_LINES = stringSetPreferencesKey("conscience_retired_lines")
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

        // Share card — which single figure the card leads with, and over what window
        val SHARE_CARD_HERO = stringPreferencesKey("share_card_hero")
        val SHARE_CARD_PERIOD = stringPreferencesKey("share_card_period")
        // Calendar month ("2026-07") the monthly share prompt was last acted on or
        // dismissed. A month string rather than a day count: "30 days" drifts off the
        // month boundary this prompt is entirely about.
        val SHARE_PROMPT_HANDLED_MONTH = stringPreferencesKey("share_prompt_handled_month")
        // Device-local on purpose: kept out of the backup's PreferencesSnapshot (#945).
        val PRO_HOME_CARD_HANDLED = booleanPreferencesKey("pro_home_card_handled")
        val PAYDAY_SUGGESTION_HANDLED = booleanPreferencesKey("payday_suggestion_handled")
        // Recurring-bill suggestions the user marked "Not recurring", as
        // RecurringSuggestions.keyFor keys. Backed up (AppPreferences).
        val DISMISSED_RECURRING_SUGGESTIONS = stringSetPreferencesKey("dismissed_recurring_suggestions")
        // The Home "possible recurring bills" card: the suggestions it showed when
        // last dismissed, and when. It comes back only for a new suggestion, and
        // not within 30 days. Device-local.
        val RECURRING_HOME_CARD_SEEN = stringSetPreferencesKey("recurring_home_card_seen")
        val RECURRING_HOME_CARD_DISMISSED_AT = longPreferencesKey("recurring_home_card_dismissed_at")
        // "Track as … bill" on transaction alerts: "key|epochDay" per merchant
        // offered, to cap how often it's offered. Device-local.
        val RECURRING_OFFER_LOG = stringSetPreferencesKey("recurring_offer_log")

        // App Lock preferences
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_TIMEOUT_MINUTES = intPreferencesKey("app_lock_timeout_minutes")
        val LAST_AUTH_TIMESTAMP = longPreferencesKey("last_auth_timestamp")
        // Device-local: whether the "App Lock needs a screen lock" notice was shown.
        val APP_LOCK_NO_SCREEN_LOCK_NOTICE_SHOWN = booleanPreferencesKey("app_lock_no_screen_lock_notice_shown")

        // In-App Review preferences
        val FIRST_LAUNCH_TIME = longPreferencesKey("first_launch_time")
        val HAS_SHOWN_REVIEW_PROMPT = booleanPreferencesKey("has_shown_review_prompt")
        val LAST_REVIEW_PROMPT_TIME = longPreferencesKey("last_review_prompt_time")

        // Feature discovery
        val HAS_USED_FULL_RESYNC = booleanPreferencesKey("has_used_full_resync")

        // Pro tier — UI-cached entitlement to avoid first-frame flicker
        // while the BillingClient connects.
        val PRO_CACHED_IS_PRO = booleanPreferencesKey("pro_cached_is_pro")

        // Website-sold Pro (Dodo license key). Key + activation instance are
        // what Dodo needs to validate; validated_at drives the offline policy.
        val LICENSE_KEY = stringPreferencesKey("license_key")
        val LICENSE_INSTANCE_ID = stringPreferencesKey("license_instance_id")
        val LICENSE_VALIDATED_AT = longPreferencesKey("license_validated_at")
        val LICENSE_PRODUCT_NAME = stringPreferencesKey("license_product_name")

        // F-Droid support nudge — epoch-day of the last contextual "Support
        // development" prompt, so it stays frequency-capped.
        val SUPPORT_NUDGE_LAST_SHOWN_DAY = longPreferencesKey("support_nudge_last_shown_epoch_day")

        // Pro tier — statement-import monthly quota tracking.
        val LAST_STATEMENT_IMPORT_AT = longPreferencesKey("last_statement_import_at")
        val STATEMENT_COLUMN_MAPPINGS = stringPreferencesKey("statement_column_mappings")

        // What's New feature
        val LAST_SEEN_APP_VERSION = stringPreferencesKey("last_seen_app_version")

        // Monthly Budget
        val MONTHLY_BUDGET_LIMIT = stringPreferencesKey("monthly_budget_limit")

        // Budget Cycle Start Day (1..31, default 1 = calendar month)
        val BUDGET_CYCLE_START_DAY = intPreferencesKey("budget_cycle_start_day")

        // Unified Currency Mode
        val UNIFIED_CURRENCY_MODE = booleanPreferencesKey("unified_currency_mode")
        val DISPLAY_CURRENCY = stringPreferencesKey("display_currency")

        // Budget Groups Migration
        val HAS_MIGRATED_TO_BUDGET_GROUPS = booleanPreferencesKey("has_migrated_to_budget_groups")

        // Balance Visibility
        val BALANCE_HIDDEN = booleanPreferencesKey("balance_hidden")
        val SHAKE_TO_HIDE_AMOUNTS = booleanPreferencesKey("shake_to_hide_amounts")
        val SHAKE_TO_HIDE_HINT_SHOWN = booleanPreferencesKey("shake_to_hide_hint_shown")

        // Selected Profile
        val SELECTED_PROFILE_ID = longPreferencesKey("selected_profile_id")

        // Blur Effects
        val BLUR_EFFECTS_ENABLED = booleanPreferencesKey("blur_effects_enabled")

        // Navigation Bar Style
        val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")

        // Routes of the bottom-bar tabs the user hid (#912)
        val HIDDEN_NAV_TABS = stringSetPreferencesKey("hidden_nav_tabs")

        // Number Format Style (digit grouping: Auto / Indian / International)
        val NUMBER_FORMAT_STYLE = stringPreferencesKey("number_format_style")
        val DATE_FORMAT_STYLE = stringPreferencesKey("date_format_style")

        // Analytics Chart Type
        val ANALYTICS_CHART_TYPE = stringPreferencesKey("analytics_chart_type")
        val ANALYTICS_GROUP_BY = stringPreferencesKey("analytics_group_by")

        // Cover Style
        val COVER_STYLE = stringPreferencesKey("cover_style")

        // Home screen section order / visibility (#770) — see HomeSectionLayout
        val HOME_SECTIONS = stringPreferencesKey("home_sections")

        // Accounts left out of Home's "Safe to spend", as "bankName_last4" keys
        val SAFE_TO_SPEND_EXCLUDED = stringSetPreferencesKey("safe_to_spend_excluded_accounts")

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

        // The user's own UPI ID, for asking people to pay their share of a split bill (#346).
        val UPI_ID = stringPreferencesKey("upi_id")

        // Scheduled folder backup (SAF tree URI; shared by standard and F-Droid).
        val SCHEDULED_FOLDER_BACKUP_ENABLED = booleanPreferencesKey("scheduled_folder_backup_enabled")
        val SCHEDULED_FOLDER_BACKUP_TREE_URI = stringPreferencesKey("scheduled_folder_backup_tree_uri")
        val SCHEDULED_FOLDER_BACKUP_LAST_TIMESTAMP = longPreferencesKey("scheduled_folder_backup_last_timestamp")

        // Embed receipt photos in backups (#839). Manual export defaults ON;
        // the daily folder backup defaults OFF (photos make every copy large).
        val EXPORT_INCLUDE_RECEIPTS = booleanPreferencesKey("export_include_receipts")
        val FOLDER_BACKUP_INCLUDE_RECEIPTS = booleanPreferencesKey("folder_backup_include_receipts")
    }

    private companion object {
        // F-Droid support nudge: at most one contextual prompt per this many days.
        const val SUPPORT_NUDGE_COOLDOWN_DAYS = 30L
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data
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
                // Absent until the user picks a font, so "never chose" follows the default.
                appFont = preferences[PreferencesKeys.APP_FONT]?.let {
                    try { AppFont.valueOf(it) } catch (_: Exception) { AppFont.DEFAULT }
                } ?: AppFont.DEFAULT,
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
                hiddenNavTabs = preferences[PreferencesKeys.HIDDEN_NAV_TABS] ?: emptySet(),
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

    val baseCurrency: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BASE_CURRENCY] ?: "INR"
        }

    val unifiedCurrencyMode: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.UNIFIED_CURRENCY_MODE] ?: false
        }

    val displayCurrency: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DISPLAY_CURRENCY]
                ?: preferences[PreferencesKeys.BASE_CURRENCY] ?: "INR"
        }

    val isDeveloperModeEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false
        }

    /**
     * When true, credit-card spend (TransactionType.CREDIT) is folded into the
     * fixed "expenses / spent" totals (Home card) instead
     * of being kept as a separate figure. Default off. The filter-driven
     * Analytics screen is unaffected — it already lets users select the Credit
     * type to view card spend. (#705)
     */
    val countCreditCardAsExpense: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.COUNT_CREDIT_AS_EXPENSE] ?: false
        }

    val numberFormatStyle: Flow<NumberFormatStyle> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NUMBER_FORMAT_STYLE]?.let {
                try {
                    NumberFormatStyle.valueOf(it)
                } catch (_: Exception) {
                    NumberFormatStyle.AUTO
                }
            } ?: NumberFormatStyle.AUTO
        }

    val dateFormatStyle: Flow<DateFormatStyle> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DATE_FORMAT_STYLE]
                ?.let { name -> DateFormatStyle.entries.firstOrNull { it.name == name } }
                ?: DateFormatStyle.AUTO
        }

    suspend fun updateDarkThemeEnabled(enabled: Boolean?) {
        dataStore.edit { preferences ->
            if (enabled == null) {
                preferences.remove(PreferencesKeys.DARK_THEME_ENABLED)
            } else {
                preferences[PreferencesKeys.DARK_THEME_ENABLED] = enabled
            }
        }
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    suspend fun updateThemeStyle(themeStyle: ThemeStyle) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_STYLE] = themeStyle.name
        }
    }

    suspend fun updateAccentColor(accentColor: AccentColor) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = accentColor.name
        }
    }

    suspend fun updateAmoledMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_AMOLED_MODE] = enabled
        }
    }

    suspend fun updateAppFont(appFont: AppFont) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_FONT] = appFont.name
        }
    }

    suspend fun updateSkippedSmsPermission(skipped: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SKIPPED_SMS_PERMISSION] = skipped
        }
    }
    
    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] = enabled
        }
    }

    suspend fun setCountCreditCardAsExpense(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COUNT_CREDIT_AS_EXPENSE] = enabled
        }
    }

    /** The 9 PM recap notification (DailySummaryWorker); default in [DAILY_SUMMARY_DEFAULT]. */
    val dailySummaryEnabled: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.DAILY_SUMMARY_ENABLED] ?: DAILY_SUMMARY_DEFAULT }

    suspend fun setDailySummaryEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_SUMMARY_ENABLED] = enabled
        }
    }

    /**
     * The device the user said the manufacturer step is done on: the setting lives
     * on that phone, so a phone transfer must not carry the answer over. Not in backups.
     */
    val backgroundOemStepConfirmedOn: Flow<String?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.BACKGROUND_OEM_STEP_CONFIRMED_ON] }

    suspend fun setBackgroundOemStepConfirmedOn(device: String?) {
        dataStore.edit { preferences ->
            if (device != null) {
                preferences[PreferencesKeys.BACKGROUND_OEM_STEP_CONFIRMED_ON] = device
            } else {
                preferences.remove(PreferencesKeys.BACKGROUND_OEM_STEP_CONFIRMED_ON)
            }
        }
    }

    /** Budget notifications (MoneyAlertWorker); an opt-in from Settings → Notifications. */
    val budgetAlertsEnabled: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.BUDGET_ALERTS_ENABLED] ?: false }

    suspend fun setBudgetAlertsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUDGET_ALERTS_ENABLED] = enabled
        }
    }

    /**
     * Atomically records that this budget's [tier] alert is due for [window].
     * True at most once, across concurrent checks: see [MoneyAlertRules.nextBudgetLatch].
     * Stays on this device, outside backups: a restore can only cost one more true alert.
     */
    // ponytail: a deleted budget leaves its short key behind; sweep if budgets ever churn.
    suspend fun claimBudgetAlert(budgetId: Long, window: String, tier: BudgetAlertTier): Boolean {
        val key = stringPreferencesKey("budget_alert_$budgetId")
        var claimed = false
        dataStore.edit { preferences ->
            MoneyAlertRules.nextBudgetLatch(preferences[key], window, tier)?.let {
                preferences[key] = it
                claimed = true
            }
        }
        return claimed
    }

    /** True once per dip of this account under its threshold; [isLow] false rearms it. */
    suspend fun claimLowBalanceAlert(bankName: String, accountLast4: String, isLow: Boolean): Boolean {
        val key = lowBalanceToldKey(bankName, accountLast4)
        var claimed = false
        dataStore.edit { preferences ->
            if (!isLow) {
                preferences.remove(key)
            } else if (preferences[key] != true) {
                preferences[key] = true
                claimed = true
            }
        }
        return claimed
    }

    /** A rename is the same account in the same dip, so what it was told goes with it. */
    suspend fun moveLowBalanceAlert(oldBankName: String, accountLast4: String, newBankName: String) {
        val oldKey = lowBalanceToldKey(oldBankName, accountLast4)
        dataStore.edit { preferences ->
            if (preferences[oldKey] == true) {
                preferences.remove(oldKey)
                preferences[lowBalanceToldKey(newBankName, accountLast4)] = true
            }
        }
    }

    private fun lowBalanceToldKey(bankName: String, accountLast4: String) =
        booleanPreferencesKey("low_balance_told_$bankName|$accountLast4")
    
    val conscienceEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.CONSCIENCE_ENABLED] ?: false }

    suspend fun setConscienceEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CONSCIENCE_ENABLED] = enabled }
    }

    /** "Not this one": the line never comes back. */
    suspend fun retireConscienceLine(lineKey: String) {
        dataStore.edit {
            it[PreferencesKeys.CONSCIENCE_RETIRED_LINES] = it[PreferencesKeys.CONSCIENCE_RETIRED_LINES].orEmpty() + lineKey
        }
    }

    /**
     * Spends a slot and picks the comment in one edit: the SMS receiver and the
     * notification listener can race, and only one may get the slot. The switch is
     * re-read here, since it can change while the history is read. Null when the
     * cadence says no or no candidate has a line left.
     */
    suspend fun tryClaimConscience(
        candidates: List<ConscienceCandidate>,
        contentLocale: String,
        nowMillis: Long,
        todayEpochDay: Long,
        random: Random,
    ): ConscienceChoice? {
        var claimed: ConscienceChoice? = null
        dataStore.edit { prefs ->
            claimed = null
            if (prefs[PreferencesKeys.CONSCIENCE_ENABLED] != true) return@edit
            val log = ConscienceCadence.decode(prefs[PreferencesKeys.CONSCIENCE_LOG] ?: emptySet())
            val choice = ConscienceCadence.choose(
                candidates, log, prefs[PreferencesKeys.CONSCIENCE_RETIRED_LINES].orEmpty(),
                contentLocale, nowMillis, todayEpochDay, random
            ) ?: return@edit
            prefs[PreferencesKeys.CONSCIENCE_LOG] =
                ConscienceCadence.encode(ConscienceCadence.record(log, choice, nowMillis, todayEpochDay))
            claimed = choice
        }
        return claimed
    }

    suspend fun updateSystemPrompt(prompt: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT] = prompt
        }
    }
    
    fun getSystemPrompt(): Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT]
        }
    
    suspend fun markScanTutorialShown() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] = true
        }
    }
    
    suspend fun saveActiveDownloadId(id: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_DOWNLOAD_ID] = id
        }
    }
    
    suspend fun getActiveDownloadId(): Long? {
        return dataStore.data
            .map { preferences -> preferences[PreferencesKeys.ACTIVE_DOWNLOAD_ID] }
            .first()
    }
    
    suspend fun clearActiveDownloadId() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ACTIVE_DOWNLOAD_ID)
        }
    }
    
    val smsScanMonths: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_MONTHS] ?: 3 // Default to 3 months
        }
    
    suspend fun updateSmsScanMonths(months: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_MONTHS] = months
        }
    }

    suspend fun getSmsScanMonths(): Int {
        return dataStore.data
            .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_MONTHS] ?: 3 }
            .first()
    }

    val smsScanAllTime: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] ?: true
        }

    suspend fun updateSmsScanAllTime(allTime: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] = allTime
        }
    }

    suspend fun getSmsScanAllTime(): Boolean {
        return dataStore.data
            .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] ?: true }
            .first()
    }

    val smsScanUseCustomDate: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_USE_CUSTOM_DATE] ?: false
        }

    suspend fun updateSmsScanUseCustomDate(useCustomDate: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_USE_CUSTOM_DATE] = useCustomDate
        }
    }

    suspend fun getSmsScanUseCustomDate(): Boolean {
        return dataStore.data
            .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_USE_CUSTOM_DATE] ?: false }
            .first()
    }

    val smsScanCustomDate: Flow<Long?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_CUSTOM_DATE] }

    suspend fun updateSmsScanCustomDate(dateMillis: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_CUSTOM_DATE] = dateMillis
        }
    }

    suspend fun getSmsScanCustomDate(): Long? {
        return dataStore.data
            .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_CUSTOM_DATE] }
            .first()
    }
    
    suspend fun setLastScanTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIMESTAMP] = timestamp
        }
    }
    
    suspend fun setLastScanPeriod(period: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_PERIOD] = period
        }
    }

    /**
     * The user's saved share-card layout. Defaults have every section on: the preview
     * shows exactly what will be sent, so an opinionated default is safe and means the
     * first share is two taps rather than a configuration exercise.
     */
    val shareCardConfig: Flow<ShareCardConfig> = dataStore.data
        .map { preferences ->
            ShareCardConfig(
                hero = ShareHero.fromName(preferences[PreferencesKeys.SHARE_CARD_HERO]),
                period = SharePeriod.fromName(preferences[PreferencesKeys.SHARE_CARD_PERIOD]),
            )
        }

    /**
     * The month ("2026-07") whose share prompt the user has already dismissed or acted
     * on, or null if they never have.
     */
    val sharePromptHandledMonth: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.SHARE_PROMPT_HANDLED_MONTH] }

    /**
     * Records [month] as handled so the Home banner doesn't come back until the next one.
     *
     * Deliberately not the claim-on-decide shape used by [claimSupportNudge]: that nudge
     * is contextual and fires once at a moment, whereas this banner should persist across
     * launches until the user actually engages with it. Marking on dismissal rather than
     * on display means an app kill doesn't silently burn the month's only prompt.
     */
    suspend fun markSharePromptHandled(month: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHARE_PROMPT_HANDLED_MONTH] = month
        }
    }

    /** The year ("2026") whose January review banner was opened or dismissed. */
    val reviewPromptHandledYear: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.REVIEW_PROMPT_HANDLED_YEAR] }

    /** Marked on the user's action, not on display — same reasoning as [markSharePromptHandled]. */
    suspend fun markReviewPromptHandledYear(year: String) {
        dataStore.edit { it[PreferencesKeys.REVIEW_PROMPT_HANDLED_YEAR] = year }
    }

    /** The opt-in weekly recap (WeeklySummaryWorker). */
    val weeklySummaryEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.WEEKLY_SUMMARY_ENABLED] ?: false }

    suspend fun setWeeklySummaryEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.WEEKLY_SUMMARY_ENABLED] = enabled }
    }

    /** The first day of the last week the weekly recap has dealt with. */
    val weeklySummaryLastWeek: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.WEEKLY_SUMMARY_LAST_WEEK] }

    suspend fun setWeeklySummaryLastWeek(weekStart: String) {
        dataStore.edit { it[PreferencesKeys.WEEKLY_SUMMARY_LAST_WEEK] = weekStart }
    }

    /** Whether the one-time Home Pro card (#945) has been tapped or dismissed. */
    val proHomeCardHandled: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.PRO_HOME_CARD_HANDLED] ?: false }

    /** Marked on the user's action, not on display — same reasoning as [markSharePromptHandled]. */
    suspend fun markProHomeCardHandled() {
        dataStore.edit { it[PreferencesKeys.PRO_HOME_CARD_HANDLED] = true }
    }

    /** Whether the one-time "start budgets on payday?" Home card was accepted or dismissed. */
    val paydaySuggestionHandled: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.PAYDAY_SUGGESTION_HANDLED] ?: false }

    /** Marked on the user's action, not on display — same reasoning as [markSharePromptHandled]. */
    suspend fun markPaydaySuggestionHandled() {
        dataStore.edit { it[PreferencesKeys.PAYDAY_SUGGESTION_HANDLED] = true }
    }

    /** Recurring-bill suggestions marked "Not recurring"; they never come back. */
    val dismissedRecurringSuggestions: Flow<Set<String>> = dataStore.data
        .map { it[PreferencesKeys.DISMISSED_RECURRING_SUGGESTIONS] ?: emptySet() }

    suspend fun addDismissedRecurringSuggestions(keys: Collection<String>) {
        if (keys.isEmpty()) return
        dataStore.edit {
            it[PreferencesKeys.DISMISSED_RECURRING_SUGGESTIONS] =
                (it[PreferencesKeys.DISMISSED_RECURRING_SUGGESTIONS] ?: emptySet()) + keys
        }
    }

    suspend fun removeDismissedRecurringSuggestion(key: String) {
        dataStore.edit {
            it[PreferencesKeys.DISMISSED_RECURRING_SUGGESTIONS] =
                (it[PreferencesKeys.DISMISSED_RECURRING_SUGGESTIONS] ?: emptySet()) - key
        }
    }

    /** Suggestion keys the Home card showed when it was last dismissed. */
    val recurringHomeCardSeen: Flow<Set<String>> = dataStore.data
        .map { it[PreferencesKeys.RECURRING_HOME_CARD_SEEN] ?: emptySet() }

    /** When the Home card was last dismissed (epoch millis), or null. */
    val recurringHomeCardDismissedAt: Flow<Long?> = dataStore.data
        .map { it[PreferencesKeys.RECURRING_HOME_CARD_DISMISSED_AT] }

    suspend fun dismissRecurringHomeCard(shownKeys: Collection<String>, nowMillis: Long) {
        dataStore.edit {
            it[PreferencesKeys.RECURRING_HOME_CARD_SEEN] =
                (it[PreferencesKeys.RECURRING_HOME_CARD_SEEN] ?: emptySet()) + shownKeys
            it[PreferencesKeys.RECURRING_HOME_CARD_DISMISSED_AT] = nowMillis
        }
    }

    /**
     * Records a "Track as … bill" offer for [key] on [today] if the caps allow
     * it: one per merchant per [perMerchantDays], one a day overall. Returns
     * whether the offer may be shown. Entries older than [perMerchantDays] are
     * dropped as it goes.
     */
    suspend fun tryRecordRecurringOffer(key: String, today: LocalDate, perMerchantDays: Long): Boolean {
        var allowed = false
        dataStore.edit { prefs ->
            val day = today.toEpochDay()
            val log = (prefs[PreferencesKeys.RECURRING_OFFER_LOG] ?: emptySet())
                .mapNotNull { entry ->
                    val at = entry.lastIndexOf('|')
                    entry.substring(at + 1).toLongOrNull()?.let { entry.substring(0, at) to it }
                }
                .filter { (_, offeredOn) -> day - offeredOn < perMerchantDays }
            allowed = log.none { (k, _) -> k == key } && log.none { (_, offeredOn) -> offeredOn == day }
            val kept = log.map { (k, offeredOn) -> "$k|$offeredOn" }.toSet()
            prefs[PreferencesKeys.RECURRING_OFFER_LOG] = if (allowed) kept + "$key|$day" else kept
        }
        return allowed
    }

    /** Undoes [tryRecordRecurringOffer] for an offer that was never shown. */
    suspend fun releaseRecurringOffer(key: String, today: LocalDate) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.RECURRING_OFFER_LOG] =
                (prefs[PreferencesKeys.RECURRING_OFFER_LOG] ?: emptySet()) - "$key|${today.toEpochDay()}"
        }
    }

    suspend fun setShareCardConfig(config: ShareCardConfig) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHARE_CARD_HERO] = config.hero.name
            preferences[PreferencesKeys.SHARE_CARD_PERIOD] = config.period.name
        }
    }

    suspend fun setFirstLaunchTime(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIRST_LAUNCH_TIME] = timestamp
        }
    }
    
    suspend fun hasShownReviewPrompt(): Boolean {
        return dataStore.data
            .map { preferences -> preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] ?: false }
            .first()
    }
    
    suspend fun markReviewPromptShown() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] = true
            preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] = System.currentTimeMillis()
        }
    }
    
    // Flow methods for backup/restore
    fun getLastScanTimestamp(): Flow<Long?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.LAST_SCAN_TIMESTAMP] }
    
    fun getLastScanPeriod(): Flow<Int?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.LAST_SCAN_PERIOD] }
    
    fun getFirstLaunchTime(): Flow<Long?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.FIRST_LAUNCH_TIME] }
    
    fun getHasShownReviewPrompt(): Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] ?: false }
    
    fun getLastReviewPromptTime(): Flow<Long?> = dataStore.data
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
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] = shown
        }
    }
    
    suspend fun updateHasShownReviewPrompt(shown: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] = shown
        }
    }
    
    suspend fun updateLastReviewPromptTime(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] = timestamp
        }
    }

    // App Lock methods
    val isAppLockEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false
        }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
        }
    }

    /**
     * Atomically updates both app lock enabled state and authentication timestamp.
     * This prevents race conditions where the flow sees enabled=true but timestamp=0.
     */
    suspend fun setAppLockEnabledWithTimestamp(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
            if (enabled) {
                preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
    }

    val appLockTimeoutMinutes: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] ?: 1 // Default to 1 minute
        }

    suspend fun setAppLockTimeoutMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] = minutes
        }
    }

    /**
     * Atomically updates timeout and authentication timestamp.
     * This prevents immediate lock when changing timeout by resetting the auth time.
     */
    suspend fun setAppLockTimeoutWithTimestamp(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] = minutes
            preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun getAppLockTimeoutMinutes(): Int {
        return dataStore.data
            .map { preferences -> preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] ?: 1 }
            .first()
    }

    suspend fun setLastAuthTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = timestamp
        }
    }

    suspend fun getLastAuthTimestamp(): Long {
        return dataStore.data
            .map { preferences -> preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] ?: 0L }
            .first()
    }

    /** True only the first time it's called: the no-screen-lock notice is shown once. */
    suspend fun claimAppLockNoScreenLockNotice(): Boolean {
        var first = false
        dataStore.edit { preferences ->
            first = preferences[PreferencesKeys.APP_LOCK_NO_SCREEN_LOCK_NOTICE_SHOWN] != true
            preferences[PreferencesKeys.APP_LOCK_NO_SCREEN_LOCK_NOTICE_SHOWN] = true
        }
        return first
    }

    fun getLastAuthTimestampFlow(): Flow<Long> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] ?: 0L
        }

    // Feature discovery - Full resync hint
    val hasUsedFullResync: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HAS_USED_FULL_RESYNC] ?: false
        }

    suspend fun markFullResyncUsed() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_USED_FULL_RESYNC] = true
        }
    }

    // Pro tier — UI-cached entitlement so first frame after cold start
    // doesn't flicker `false → true` while BillingClient connects. Real
    // source of truth is Play; this is a hint only.
    val proCachedIsPro: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.PRO_CACHED_IS_PRO] ?: false }

    suspend fun setProCachedIsPro(isPro: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRO_CACHED_IS_PRO] = isPro
        }
    }

    /** Stored website license (Dodo). Null [StoredLicense.key] means none. */
    val storedLicense: Flow<StoredLicense?> = dataStore.data.map { p ->
        p[PreferencesKeys.LICENSE_KEY]?.let { key ->
            StoredLicense(
                key = key,
                instanceId = p[PreferencesKeys.LICENSE_INSTANCE_ID],
                validatedAt = p[PreferencesKeys.LICENSE_VALIDATED_AT] ?: 0L,
                productName = p[PreferencesKeys.LICENSE_PRODUCT_NAME],
            )
        }
    }

    suspend fun setStoredLicense(license: StoredLicense?) {
        dataStore.edit { p ->
            if (license == null) {
                p.remove(PreferencesKeys.LICENSE_KEY)
                p.remove(PreferencesKeys.LICENSE_INSTANCE_ID)
                p.remove(PreferencesKeys.LICENSE_VALIDATED_AT)
                p.remove(PreferencesKeys.LICENSE_PRODUCT_NAME)
            } else {
                p[PreferencesKeys.LICENSE_KEY] = license.key
                license.instanceId?.let { p[PreferencesKeys.LICENSE_INSTANCE_ID] = it }
                    ?: p.remove(PreferencesKeys.LICENSE_INSTANCE_ID)
                p[PreferencesKeys.LICENSE_VALIDATED_AT] = license.validatedAt
                license.productName?.let { p[PreferencesKeys.LICENSE_PRODUCT_NAME] = it }
                    ?: p.remove(PreferencesKeys.LICENSE_PRODUCT_NAME)
            }
        }
    }

    /** Epoch-day the contextual F-Droid support nudge was last shown (0 = never). */
    val supportNudgeLastShownDay: Flow<Long> = dataStore.data
        .map { it[PreferencesKeys.SUPPORT_NUDGE_LAST_SHOWN_DAY] ?: 0L }

    /**
     * Atomically decides whether to show the contextual F-Droid "Support
     * development" nudge and, if so, records it as shown before returning — so
     * back-to-back power-feature moments can't show it twice. Returns true at
     * most once per [SUPPORT_NUDGE_COOLDOWN_DAYS] days, across every trigger
     * site (a single global timestamp). Callers gate on the F-Droid flavor.
     */
    suspend fun claimSupportNudge(): Boolean {
        val today = LocalDate.now().toEpochDay()
        // Check-and-set inside a single edit{} so the read and write are one
        // atomic transaction — DataStore serializes edit blocks, so two
        // concurrent claims can't both observe the stale timestamp and win.
        var claimed = false
        dataStore.edit { prefs ->
            val last = prefs[PreferencesKeys.SUPPORT_NUDGE_LAST_SHOWN_DAY] ?: 0L
            if (today - last >= SUPPORT_NUDGE_COOLDOWN_DAYS) {
                prefs[PreferencesKeys.SUPPORT_NUDGE_LAST_SHOWN_DAY] = today
                claimed = true
            }
        }
        return claimed
    }

    /**
     * Epoch-millis of the last successful PDF statement import, or null if
     * the user has never imported. Consumed by the statement-import gate —
     * free users get one import per calendar month, so this single
     * timestamp is enough (no per-month counter needed since the limit is 1).
     */
    val lastStatementImportAt: Flow<Long?> = dataStore.data
        .map { it[PreferencesKeys.LAST_STATEMENT_IMPORT_AT] }

    suspend fun markStatementImported(epochMillis: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_STATEMENT_IMPORT_AT] = epochMillis
        }
    }

    /**
     * Spreadsheet statement column mappings (#13), JSON keyed by header
     * signature — see StatementTableParser.encodeMappings. Device-local, not
     * backed up: a lost mapping costs one re-map.
     */
    suspend fun getStatementColumnMappings(): String? =
        dataStore.data.first()[PreferencesKeys.STATEMENT_COLUMN_MAPPINGS]

    suspend fun setStatementColumnMappings(json: String) {
        dataStore.edit { it[PreferencesKeys.STATEMENT_COLUMN_MAPPINGS] = json }
    }

    // What's New feature
    suspend fun getLastSeenAppVersion(): String? {
        return dataStore.data
            .map { preferences -> preferences[PreferencesKeys.LAST_SEEN_APP_VERSION] }
            .first()
    }

    suspend fun setLastSeenAppVersion(version: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SEEN_APP_VERSION] = version
        }
    }

    // Unified Currency Mode
    suspend fun setUnifiedCurrencyMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNIFIED_CURRENCY_MODE] = enabled
        }
    }

    suspend fun setDisplayCurrency(currency: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISPLAY_CURRENCY] = currency
        }
    }

    // Budget Groups Migration
    val hasMigratedToBudgetGroups: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HAS_MIGRATED_TO_BUDGET_GROUPS] ?: false
        }

    suspend fun setHasMigratedToBudgetGroups(migrated: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_MIGRATED_TO_BUDGET_GROUPS] = migrated
        }
    }

    // Monthly Budget
    val monthlyBudgetLimit: Flow<java.math.BigDecimal?> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MONTHLY_BUDGET_LIMIT]?.let { java.math.BigDecimal(it) }
        }

    suspend fun updateMonthlyBudgetLimit(amount: java.math.BigDecimal?) {
        dataStore.edit { preferences ->
            if (amount == null) {
                preferences.remove(PreferencesKeys.MONTHLY_BUDGET_LIMIT)
            } else {
                preferences[PreferencesKeys.MONTHLY_BUDGET_LIMIT] = amount.toPlainString()
            }
        }
    }

    /**
     * Day of the month the user's budget cycle starts on. 1 = first of the month
     * (the calendar-month default); up to 31. Stored as an Int 1..31 — out-of-range
     * values from older builds or migrations are clamped at read time.
     */
    val budgetCycleStartDay: Flow<Int> = dataStore.data
        .map { preferences ->
            val raw = preferences[PreferencesKeys.BUDGET_CYCLE_START_DAY] ?: 1
            if (raw == com.pennywiseai.tracker.domain.model.BudgetCycle.LAST_WORKING_DAY) raw else raw.coerceIn(1, 31)
        }

    suspend fun getBudgetCycleStartDay(): Int = budgetCycleStartDay.first()

    suspend fun updateBudgetCycleStartDay(day: Int) {
        val clamped = if (day == com.pennywiseai.tracker.domain.model.BudgetCycle.LAST_WORKING_DAY) day else day.coerceIn(1, 31)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUDGET_CYCLE_START_DAY] = clamped
        }
        // Every widget computes "this cycle" from this day.
        WidgetRefresher.refreshTransactionWidgets(context)
    }

    /**
     * Explicit base-currency choice from the Settings currency selector. Marks the
     * currency as user-set so the main account can no longer override it.
     */
    suspend fun updateBaseCurrency(currency: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASE_CURRENCY] = currency
            preferences[PreferencesKeys.BASE_CURRENCY_USER_SET] = true
        }
    }

    suspend fun updateNumberFormatStyle(style: NumberFormatStyle) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NUMBER_FORMAT_STYLE] = style.name
        }
    }

    suspend fun updateDateFormatStyle(style: DateFormatStyle) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DATE_FORMAT_STYLE] = style.name
        }
    }

    /**
     * Sets the base currency derived from the user's main account, but only if the
     * user hasn't explicitly chosen one via the Settings currency selector. The
     * explicit selector always wins.
     */
    suspend fun applyMainAccountCurrency(currency: String) {
        dataStore.edit { preferences ->
            if (preferences[PreferencesKeys.BASE_CURRENCY_USER_SET] != true) {
                preferences[PreferencesKeys.BASE_CURRENCY] = currency
            }
        }
    }

    // Balance Visibility
    val isBalanceHidden: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BALANCE_HIDDEN] ?: true // hidden until the user reveals it
        }

    /**
     * Whether every screen masks amounts, not just Home. Only a saved choice counts:
     * until the user taps the eye, Home stays hidden ([isBalanceHidden]) and the rest
     * of the app shows its numbers, so a fresh install doesn't look empty.
     */
    val isAmountHiddenEverywhere: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.BALANCE_HIDDEN] ?: false }

    suspend fun setBalanceHidden(hidden: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BALANCE_HIDDEN] = hidden
        }
        // The widgets' totals follow the same eye.
        WidgetRefresher.refreshTransactionWidgets(context)
    }

    /**
     * Flips the saved hide choice in one read-modify-write, so the eye and a shake
     * can't race. Returns the new value. [hiddenWhenUnset] is what the caller sees
     * before any choice: Home's eye sees hidden ([isBalanceHidden]), a shake sees
     * the rest of the app shown ([isAmountHiddenEverywhere]), so it hides.
     */
    suspend fun toggleBalanceHidden(hiddenWhenUnset: Boolean = true): Boolean {
        var hidden = true
        dataStore.edit { preferences ->
            hidden = !(preferences[PreferencesKeys.BALANCE_HIDDEN] ?: hiddenWhenUnset)
            preferences[PreferencesKeys.BALANCE_HIDDEN] = hidden
        }
        WidgetRefresher.refreshTransactionWidgets(context)
        return hidden
    }

    /** Shake the phone to hide / show amounts. Off by default: a jolt in a pocket shouldn't reveal them. */
    val shakeToHideAmounts: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.SHAKE_TO_HIDE_AMOUNTS] ?: false }

    suspend fun setShakeToHideAmounts(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHAKE_TO_HIDE_AMOUNTS] = enabled
        }
    }

    /** True only the first time it's called: the first shake explains the gesture. */
    suspend fun claimShakeToHideHint(): Boolean {
        var first = false
        dataStore.edit { preferences ->
            first = preferences[PreferencesKeys.SHAKE_TO_HIDE_HINT_SHOWN] != true
            preferences[PreferencesKeys.SHAKE_TO_HIDE_HINT_SHOWN] = true
        }
        return first
    }

    // Replace UPI VPAs with matching contact names at display time.
    // Off by default — turning on prompts for READ_CONTACTS.
    val useContactsForVpa: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USE_CONTACTS_FOR_VPA] ?: false
        }

    suspend fun setUseContactsForVpa(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_CONTACTS_FOR_VPA] = enabled
        }
    }

    /** The user's own UPI ID (#346), or null until they enter one. */
    val upiId: Flow<String?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.UPI_ID]?.takeIf { it.isNotBlank() } }

    /** Blank or null clears it. */
    suspend fun setUpiId(id: String?) {
        dataStore.edit { preferences ->
            val trimmed = id?.trim().orEmpty()
            if (trimmed.isEmpty()) preferences.remove(PreferencesKeys.UPI_ID) else preferences[PreferencesKeys.UPI_ID] = trimmed
        }
    }

    // Selected Profile
    val selectedProfileId: Flow<Long?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.SELECTED_PROFILE_ID] }

    suspend fun updateSelectedProfileId(profileId: Long?) {
        dataStore.edit { preferences ->
            if (profileId == null) {
                preferences.remove(PreferencesKeys.SELECTED_PROFILE_ID)
            } else {
                preferences[PreferencesKeys.SELECTED_PROFILE_ID] = profileId
            }
        }
        // The spend widgets are scoped to the selected profile.
        WidgetRefresher.refreshTransactionWidgets(context)
    }

    // Blur Effects
    val blurEffectsEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BLUR_EFFECTS_ENABLED] ?: true
        }

    suspend fun updateBlurEffectsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLUR_EFFECTS_ENABLED] = enabled
        }
    }

    // Navigation Bar Style
    suspend fun updateNavBarStyle(style: NavBarStyle) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAV_BAR_STYLE] = style.name
        }
    }

    // Bottom-bar tabs (#912). Read-modify-write inside one edit so quick
    // toggles of two tabs can't overwrite each other.
    suspend fun setNavTabHidden(route: String, hidden: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.HIDDEN_NAV_TABS] ?: emptySet()
            preferences[PreferencesKeys.HIDDEN_NAV_TABS] =
                if (hidden) current + route else current - route
        }
    }

    suspend fun updateHiddenNavTabs(routes: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDDEN_NAV_TABS] = routes
        }
    }

    // Analytics Chart Type
    suspend fun saveAnalyticsChartType(chartType: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANALYTICS_CHART_TYPE] = chartType
        }
    }

    fun getAnalyticsChartType(): Flow<String?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.ANALYTICS_CHART_TYPE] }

    suspend fun saveAnalyticsGroupBy(groupBy: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANALYTICS_GROUP_BY] = groupBy
        }
    }

    fun getAnalyticsGroupBy(): Flow<String?> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.ANALYTICS_GROUP_BY] }

    // Cover Style
    suspend fun updateCoverStyle(style: CoverStyle) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COVER_STYLE] = style.name
        }
    }

    // Home sections (#770)
    val homeSectionLayout: Flow<List<Pair<HomeSection, Boolean>>> = dataStore.data
        .map { preferences -> HomeSectionLayout.decode(preferences[PreferencesKeys.HOME_SECTIONS]) }

    /** Read-modify-write inside one `edit` so rapid taps can't clobber each other. */
    suspend fun updateHomeSectionLayout(
        transform: (List<Pair<HomeSection, Boolean>>) -> List<Pair<HomeSection, Boolean>>
    ) {
        dataStore.edit { preferences ->
            val current = HomeSectionLayout.decode(preferences[PreferencesKeys.HOME_SECTIONS])
            preferences[PreferencesKeys.HOME_SECTIONS] = HomeSectionLayout.encode(transform(current))
        }
    }

    /** Accounts left out of "Safe to spend" ("bankName_last4"); empty = every spendable account counts. */
    val safeToSpendExcludedAccounts: Flow<Set<String>> = dataStore.data
        .map { preferences -> preferences[PreferencesKeys.SAFE_TO_SPEND_EXCLUDED] ?: emptySet() }

    suspend fun setSafeToSpendAccountIncluded(key: String, included: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.SAFE_TO_SPEND_EXCLUDED] ?: emptySet()
            preferences[PreferencesKeys.SAFE_TO_SPEND_EXCLUDED] = if (included) current - key else current + key
        }
    }

    // Profile & Onboarding
    suspend fun updateUserName(name: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_NAME] = name
        }
    }

    suspend fun updateProfileImageUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(PreferencesKeys.PROFILE_IMAGE_URI)
            } else {
                preferences[PreferencesKeys.PROFILE_IMAGE_URI] = uri
            }
        }
    }

    suspend fun updateProfileBackgroundColor(color: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROFILE_BACKGROUND_COLOR] = color
        }
    }

    suspend fun updateHasCompletedOnboarding(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    suspend fun updateMainAccountKey(accountKey: String?) {
        dataStore.edit { preferences ->
            if (accountKey == null) {
                preferences.remove(PreferencesKeys.MAIN_ACCOUNT_KEY)
            } else {
                preferences[PreferencesKeys.MAIN_ACCOUNT_KEY] = accountKey
            }
        }
    }

    val scheduledFolderBackupEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_ENABLED] ?: false
        }

    val scheduledFolderBackupTreeUri: Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_TREE_URI]
        }

    val scheduledFolderBackupLastTimestamp: Flow<Long?> = dataStore.data
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
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setScheduledFolderBackupTreeUri(treeUri: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_TREE_URI] = treeUri
        }
    }

    suspend fun setScheduledFolderBackupLastTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCHEDULED_FOLDER_BACKUP_LAST_TIMESTAMP] = timestamp
        }
    }

    val exportIncludeReceipts: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.EXPORT_INCLUDE_RECEIPTS] ?: true }

    val folderBackupIncludeReceipts: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.FOLDER_BACKUP_INCLUDE_RECEIPTS] ?: false }

    suspend fun setExportIncludeReceipts(include: Boolean) {
        dataStore.edit { it[PreferencesKeys.EXPORT_INCLUDE_RECEIPTS] = include }
    }

    suspend fun setFolderBackupIncludeReceipts(include: Boolean) {
        dataStore.edit { it[PreferencesKeys.FOLDER_BACKUP_INCLUDE_RECEIPTS] = include }
    }
}

data class UserPreferences(
    val isDarkThemeEnabled: Boolean? = null, // null means follow system
    val isDynamicColorEnabled: Boolean = false, // Default to custom brand colors
    val themeStyle: ThemeStyle = ThemeStyle.DYNAMIC,
    val accentColor: AccentColor = AccentColor.SURFACE,
    val isAmoledMode: Boolean = false,
    val appFont: AppFont = AppFont.DEFAULT,
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
    val hiddenNavTabs: Set<String> = emptySet(),
    val coverStyle: CoverStyle = CoverStyle.AURORA,
    val userName: String = "User",
    val profileImageUri: String? = null,
    val profileBackgroundColor: Int = 0,
    val hasCompletedOnboarding: Boolean = false,
    val mainAccountKey: String? = null,
    val selectedProfileId: Long? = null,
    /** Day of the month (1..31) the budget cycle starts on. 1 = calendar month. */
    val budgetCycleStartDay: Int = 1
)

/** A website-sold Pro license as persisted in DataStore. */
data class StoredLicense(
    val key: String,
    val instanceId: String?,
    val validatedAt: Long,
    val productName: String?,
)
