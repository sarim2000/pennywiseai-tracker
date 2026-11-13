package com.pennywiseai.tracker.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.pennywiseai.tracker.data.share.ShareCardConfig
import com.pennywiseai.tracker.data.share.ShareHero
import com.pennywiseai.tracker.data.share.SharePeriod
import com.pennywiseai.tracker.domain.spending.BudgetAlertTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pins what the user_preferences store means for an existing install: every default,
 * every value it reads back, and the format every setter writes. Key names and enum
 * names are literals on purpose, so that renaming one in production fails here.
 *
 * Runs on its own store per test, because the app's `Context.dataStore` delegate is a
 * process-wide singleton that other unit tests write to. Skips the setters that refresh
 * widgets (updateBudgetCycleStartDay, setBalanceHidden, toggleBalanceHidden,
 * updateSelectedProfileId); PreferenceWidgetRefreshTest covers those.
 */
class UserPreferencesStorageGoldenTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: UserPreferencesRepository

    private fun bool(name: String) = booleanPreferencesKey(name)
    private fun int(name: String) = intPreferencesKey(name)
    private fun long(name: String) = longPreferencesKey(name)
    private fun string(name: String) = stringPreferencesKey(name)
    private fun stringSet(name: String) = stringSetPreferencesKey(name)

    private suspend fun stored(): Preferences = store.data.first()

    @Before
    fun setUp() {
        store = PreferenceDataStoreFactory.create(
            migrations = listOf(SmsScanAllTimeMigration),
            scope = scope,
            produceFile = { File(tmp.root, "user_preferences.preferences_pb") }
        )
        repository = UserPreferencesRepository(store) {}
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `empty store reads the pinned defaults`() = runBlocking {
        // The cross-cutting snapshot.
        val snapshot = repository.userPreferences.first()
        assertNull(snapshot.isDarkThemeEnabled)
        assertFalse(snapshot.isDynamicColorEnabled)
        assertEquals("DYNAMIC", snapshot.themeStyle.name)
        assertEquals("SURFACE", snapshot.accentColor.name)
        assertFalse(snapshot.isAmoledMode)
        assertEquals("GOOGLE_SANS_FLEX", snapshot.appFont.name)
        assertFalse(snapshot.hasSkippedSmsPermission)
        assertFalse(snapshot.isDeveloperModeEnabled)
        assertFalse(snapshot.hasShownScanTutorial)
        assertEquals(3, snapshot.smsScanMonths)
        assertTrue(snapshot.smsScanAllTime)
        assertEquals("INR", snapshot.baseCurrency)
        assertFalse(snapshot.unifiedCurrencyMode)
        assertEquals("INR", snapshot.displayCurrency)
        assertTrue(snapshot.blurEffectsEnabled)
        assertEquals("FLOATING", snapshot.navBarStyle.name)
        assertEquals(emptySet<String>(), snapshot.hiddenNavTabs)
        assertEquals("AURORA", snapshot.coverStyle.name)
        assertEquals("User", snapshot.userName)
        assertNull(snapshot.profileImageUri)
        assertEquals(0, snapshot.profileBackgroundColor)
        assertFalse(snapshot.hasCompletedOnboarding)
        assertNull(snapshot.mainAccountKey)

        // Currency and formats.
        assertEquals("INR", repository.baseCurrency.first())
        assertEquals("INR", repository.displayCurrency.first())
        assertFalse(repository.unifiedCurrencyMode.first())
        assertEquals("AUTO", repository.numberFormatStyle.first().name)
        assertEquals("AUTO", repository.dateFormatStyle.first().name)
        assertFalse(repository.countCreditCardAsExpense.first())

        // SMS scan window.
        assertEquals(3, repository.smsScanMonths.first())
        assertEquals(3, repository.getSmsScanMonths())
        assertTrue(repository.smsScanAllTime.first())
        assertTrue(repository.getSmsScanAllTime())
        assertFalse(repository.smsScanUseCustomDate.first())
        assertFalse(repository.getSmsScanUseCustomDate())
        assertNull(repository.smsScanCustomDate.first())
        assertNull(repository.getSmsScanCustomDate())
        assertNull(repository.getLastScanTimestamp().first())
        assertNull(repository.getLastScanPeriod().first())

        // Notifications.
        assertFalse(DAILY_SUMMARY_DEFAULT)
        assertFalse(repository.dailySummaryEnabled.first())
        assertFalse(repository.weeklySummaryEnabled.first())
        assertNull(repository.weeklySummaryLastWeek.first())
        assertFalse(repository.budgetAlertsEnabled.first())
        assertFalse(repository.conscienceEnabled.first())

        // Hidden amounts and App Lock.
        assertTrue(repository.isBalanceHidden.first())
        assertFalse(repository.isAmountHiddenEverywhere.first())
        assertFalse(repository.shakeToHideAmounts.first())
        assertFalse(repository.isAppLockEnabled.first())
        assertEquals(1, repository.appLockTimeoutMinutes.first())
        assertEquals(1, repository.getAppLockTimeoutMinutes())
        assertEquals(0L, repository.getLastAuthTimestamp())
        assertEquals(0L, repository.getLastAuthTimestampFlow().first())

        // Budgets.
        assertEquals(1, repository.budgetCycleStartDay.first())
        assertEquals(1, repository.getBudgetCycleStartDay())
        assertNull(repository.monthlyBudgetLimit.first())
        assertFalse(repository.hasMigratedToBudgetGroups.first())

        // Billing.
        assertFalse(repository.proCachedIsPro.first())
        assertNull(repository.storedLicense.first())
        assertEquals(0L, repository.supportNudgeLastShownDay.first())
        assertNull(repository.lastStatementImportAt.first())
        assertNull(repository.getStatementColumnMappings())

        // Home, share card and recurring suggestions.
        val share = repository.shareCardConfig.first()
        assertEquals("TRANSACTIONS", share.hero.name)
        assertEquals("THIS_MONTH", share.period.name)
        assertEquals(
            listOf(
                "SAFE_TO_SPEND", "BUDGETS", "RECENT_TRANSACTIONS", "ACCOUNTS", "SUBSCRIPTIONS",
                "LOANS", "GOALS", "GROUPS", "MONEY_IN_MOTION", "ACTIVITY"
            ).map { it to true },
            repository.homeSectionLayout.first().map { (section, visible) -> section.name to visible }
        )
        assertEquals(emptySet<String>(), repository.safeToSpendExcludedAccounts.first())
        assertEquals(emptySet<String>(), repository.dismissedRecurringSuggestions.first())
        assertEquals(emptySet<String>(), repository.recurringHomeCardSeen.first())
        assertNull(repository.recurringHomeCardDismissedAt.first())
        assertNull(repository.sharePromptHandledMonth.first())
        assertNull(repository.reviewPromptHandledYear.first())
        assertFalse(repository.proHomeCardHandled.first())
        assertFalse(repository.paydaySuggestionHandled.first())

        // Backup settings.
        assertFalse(repository.scheduledFolderBackupEnabled.first())
        assertFalse(repository.isScheduledFolderBackupEnabled())
        assertNull(repository.scheduledFolderBackupTreeUri.first())
        assertNull(repository.getScheduledFolderBackupTreeUri())
        assertNull(repository.scheduledFolderBackupLastTimestamp.first())
        assertTrue(repository.exportIncludeReceipts.first())
        assertFalse(repository.folderBackupIncludeReceipts.first())

        // Everything else with a read path.
        assertFalse(repository.isDeveloperModeEnabled.first())
        assertNull(repository.upiId.first())
        assertFalse(repository.useContactsForVpa.first())
        assertNull(repository.selectedProfileId.first())
        assertTrue(repository.blurEffectsEnabled.first())
        assertNull(repository.backgroundOemStepConfirmedOn.first())
        assertNull(repository.getSystemPrompt().first())
        assertNull(repository.getActiveDownloadId())
        assertNull(repository.getLastSeenAppVersion())
        assertNull(repository.getAnalyticsChartType().first())
        assertNull(repository.getAnalyticsGroupBy().first())
        assertFalse(repository.hasUsedFullResync.first())
        assertNull(repository.getFirstLaunchTime().first())
        assertFalse(repository.getHasShownReviewPrompt().first())
        assertFalse(repository.hasShownReviewPrompt())
        assertNull(repository.getLastReviewPromptTime().first())
    }

    @Test
    fun `stored values from an old install are read back`() = runBlocking<Unit> {
        store.edit {
            it[bool("dark_theme_enabled")] = true
            it[bool("dynamic_color_enabled")] = true
            it[string("theme_style")] = "BRANDED"
            it[string("accent_color")] = "IRIS"
            it[bool("is_amoled_mode")] = true
            it[string("app_font")] = "SN_PRO"
            it[bool("has_skipped_sms_permission")] = true
            it[bool("developer_mode_enabled")] = true
            it[bool("has_shown_scan_tutorial")] = true
            it[int("sms_scan_months")] = 6
            it[bool("sms_scan_all_time")] = false
            it[string("base_currency")] = "EUR"
            it[bool("unified_currency_mode")] = true
            it[string("display_currency")] = "USD"
            it[bool("blur_effects_enabled")] = false
            it[string("nav_bar_style")] = "NORMAL"
            it[stringSet("hidden_nav_tabs")] = setOf("analytics")
            it[string("cover_style")] = "OCEAN"
            it[string("user_name")] = "Test User"
            it[string("profile_image_uri")] = "avatar://3"
            it[int("profile_background_color")] = 42
            it[bool("has_completed_onboarding")] = true
            it[string("main_account_key")] = "TestBank_0000"

            it[string("number_format_style")] = "INDIAN"
            it[string("date_format_style")] = "ISO"
            it[bool("count_credit_as_expense")] = true

            it[bool("sms_scan_use_custom_date")] = true
            it[long("sms_scan_custom_date")] = 1_000L
            it[long("last_scan_timestamp")] = 2_000L
            it[int("last_scan_period")] = 9

            it[bool("daily_summary_enabled")] = true
            it[bool("weekly_summary_enabled")] = true
            it[string("weekly_summary_last_week")] = "2026-01-05"
            it[bool("budget_alerts_enabled")] = true
            it[bool("conscience_enabled")] = true

            it[bool("balance_hidden")] = false
            it[bool("shake_to_hide_amounts")] = true
            it[bool("app_lock_enabled")] = true
            it[int("app_lock_timeout_minutes")] = 5
            it[long("last_auth_timestamp")] = 3_000L

            it[int("budget_cycle_start_day")] = 25
            it[string("monthly_budget_limit")] = "1234.50"
            it[bool("has_migrated_to_budget_groups")] = true

            it[bool("pro_cached_is_pro")] = true
            it[string("license_key")] = "TEST-KEY"
            it[string("license_instance_id")] = "instance-1"
            it[long("license_validated_at")] = 4_000L
            it[string("license_product_name")] = "Test Product"
            it[long("support_nudge_last_shown_epoch_day")] = 20_000L
            it[long("last_statement_import_at")] = 5_000L
            it[string("statement_column_mappings")] = "{}"

            it[string("share_card_hero")] = "MERCHANTS"
            it[string("share_card_period")] = "ALL_TIME"
            it[string("home_sections")] = "BUDGETS,!GROUPS"
            it[stringSet("safe_to_spend_excluded_accounts")] = setOf("TestBank_0000")
            it[stringSet("dismissed_recurring_suggestions")] = setOf("suggestion-1")
            it[stringSet("recurring_home_card_seen")] = setOf("suggestion-2")
            it[long("recurring_home_card_dismissed_at")] = 6_000L
            it[string("share_prompt_handled_month")] = "2026-07"
            it[string("review_prompt_handled_year")] = "2026"
            it[bool("pro_home_card_handled")] = true
            it[bool("payday_suggestion_handled")] = true

            it[bool("scheduled_folder_backup_enabled")] = true
            it[string("scheduled_folder_backup_tree_uri")] = "content://test/tree"
            it[long("scheduled_folder_backup_last_timestamp")] = 7_000L
            it[bool("export_include_receipts")] = false
            it[bool("folder_backup_include_receipts")] = true

            it[string("upi_id")] = "someone@test"
            it[bool("use_contacts_for_vpa")] = true
            it[long("selected_profile_id")] = 11L
            it[string("background_oem_step_confirmed_on")] = "Test Device"
            it[string("system_prompt")] = "Test prompt"
            it[long("active_download_id")] = 12L
            it[string("last_seen_app_version")] = "1.2.3"
            it[string("analytics_chart_type")] = "BAR"
            it[string("analytics_group_by")] = "WEEK"
            it[bool("has_used_full_resync")] = true
            it[long("first_launch_time")] = 8_000L
            it[bool("has_shown_review_prompt")] = true
            it[long("last_review_prompt_time")] = 9_000L
        }

        val snapshot = repository.userPreferences.first()
        assertEquals(true, snapshot.isDarkThemeEnabled)
        assertTrue(snapshot.isDynamicColorEnabled)
        assertEquals("BRANDED", snapshot.themeStyle.name)
        assertEquals("IRIS", snapshot.accentColor.name)
        assertTrue(snapshot.isAmoledMode)
        assertEquals("SN_PRO", snapshot.appFont.name)
        assertTrue(snapshot.hasSkippedSmsPermission)
        assertTrue(snapshot.isDeveloperModeEnabled)
        assertTrue(snapshot.hasShownScanTutorial)
        assertEquals(6, snapshot.smsScanMonths)
        assertFalse(snapshot.smsScanAllTime)
        assertEquals("EUR", snapshot.baseCurrency)
        assertTrue(snapshot.unifiedCurrencyMode)
        assertEquals("USD", snapshot.displayCurrency)
        assertFalse(snapshot.blurEffectsEnabled)
        assertEquals("NORMAL", snapshot.navBarStyle.name)
        assertEquals(setOf("analytics"), snapshot.hiddenNavTabs)
        assertEquals("OCEAN", snapshot.coverStyle.name)
        assertEquals("Test User", snapshot.userName)
        assertEquals("avatar://3", snapshot.profileImageUri)
        assertEquals(42, snapshot.profileBackgroundColor)
        assertTrue(snapshot.hasCompletedOnboarding)
        assertEquals("TestBank_0000", snapshot.mainAccountKey)

        assertEquals("EUR", repository.baseCurrency.first())
        assertEquals("USD", repository.displayCurrency.first())
        assertTrue(repository.unifiedCurrencyMode.first())
        assertEquals("INDIAN", repository.numberFormatStyle.first().name)
        assertEquals("ISO", repository.dateFormatStyle.first().name)
        assertTrue(repository.countCreditCardAsExpense.first())

        assertEquals(6, repository.smsScanMonths.first())
        assertEquals(6, repository.getSmsScanMonths())
        assertFalse(repository.smsScanAllTime.first())
        assertFalse(repository.getSmsScanAllTime())
        assertTrue(repository.smsScanUseCustomDate.first())
        assertTrue(repository.getSmsScanUseCustomDate())
        assertEquals(1_000L, repository.smsScanCustomDate.first())
        assertEquals(1_000L, repository.getSmsScanCustomDate())
        assertEquals(2_000L, repository.getLastScanTimestamp().first())
        assertEquals(9, repository.getLastScanPeriod().first())

        assertTrue(repository.dailySummaryEnabled.first())
        assertTrue(repository.weeklySummaryEnabled.first())
        assertEquals("2026-01-05", repository.weeklySummaryLastWeek.first())
        assertTrue(repository.budgetAlertsEnabled.first())
        assertTrue(repository.conscienceEnabled.first())

        assertFalse(repository.isBalanceHidden.first())
        assertFalse(repository.isAmountHiddenEverywhere.first())
        assertTrue(repository.shakeToHideAmounts.first())
        assertTrue(repository.isAppLockEnabled.first())
        assertEquals(5, repository.appLockTimeoutMinutes.first())
        assertEquals(5, repository.getAppLockTimeoutMinutes())
        assertEquals(3_000L, repository.getLastAuthTimestamp())
        assertEquals(3_000L, repository.getLastAuthTimestampFlow().first())

        assertEquals(25, repository.budgetCycleStartDay.first())
        assertEquals(25, repository.getBudgetCycleStartDay())
        assertEquals(BigDecimal("1234.50"), repository.monthlyBudgetLimit.first())
        assertTrue(repository.hasMigratedToBudgetGroups.first())

        assertTrue(repository.proCachedIsPro.first())
        assertEquals(
            StoredLicense("TEST-KEY", "instance-1", 4_000L, "Test Product"),
            repository.storedLicense.first()
        )
        assertEquals(20_000L, repository.supportNudgeLastShownDay.first())
        assertEquals(5_000L, repository.lastStatementImportAt.first())
        assertEquals("{}", repository.getStatementColumnMappings())

        val share = repository.shareCardConfig.first()
        assertEquals("MERCHANTS", share.hero.name)
        assertEquals("ALL_TIME", share.period.name)
        val layout = repository.homeSectionLayout.first().associate { (section, visible) -> section.name to visible }
        assertEquals(true, layout["BUDGETS"])
        assertEquals(false, layout["GROUPS"])
        assertEquals(setOf("TestBank_0000"), repository.safeToSpendExcludedAccounts.first())
        assertEquals(setOf("suggestion-1"), repository.dismissedRecurringSuggestions.first())
        assertEquals(setOf("suggestion-2"), repository.recurringHomeCardSeen.first())
        assertEquals(6_000L, repository.recurringHomeCardDismissedAt.first())
        assertEquals("2026-07", repository.sharePromptHandledMonth.first())
        assertEquals("2026", repository.reviewPromptHandledYear.first())
        assertTrue(repository.proHomeCardHandled.first())
        assertTrue(repository.paydaySuggestionHandled.first())

        assertTrue(repository.scheduledFolderBackupEnabled.first())
        assertTrue(repository.isScheduledFolderBackupEnabled())
        assertEquals("content://test/tree", repository.scheduledFolderBackupTreeUri.first())
        assertEquals("content://test/tree", repository.getScheduledFolderBackupTreeUri())
        assertEquals(7_000L, repository.scheduledFolderBackupLastTimestamp.first())
        assertFalse(repository.exportIncludeReceipts.first())
        assertTrue(repository.folderBackupIncludeReceipts.first())

        assertEquals("someone@test", repository.upiId.first())
        assertTrue(repository.useContactsForVpa.first())
        assertEquals(11L, repository.selectedProfileId.first())
        assertEquals("Test Device", repository.backgroundOemStepConfirmedOn.first())
        assertEquals("Test prompt", repository.getSystemPrompt().first())
        assertEquals(12L, repository.getActiveDownloadId())
        assertEquals("1.2.3", repository.getLastSeenAppVersion())
        assertEquals("BAR", repository.getAnalyticsChartType().first())
        assertEquals("WEEK", repository.getAnalyticsGroupBy().first())
        assertTrue(repository.hasUsedFullResync.first())
        assertEquals(8_000L, repository.getFirstLaunchTime().first())
        assertTrue(repository.getHasShownReviewPrompt().first())
        assertTrue(repository.hasShownReviewPrompt())
        assertEquals(9_000L, repository.getLastReviewPromptTime().first())
    }

    @Test
    fun `read fallbacks are unchanged`() = runBlocking {
        store.edit { it[string("base_currency")] = "EUR" }
        assertEquals("EUR", repository.displayCurrency.first())
        assertEquals("EUR", repository.userPreferences.first().displayCurrency)

        store.edit { it[bool("has_completed_onboarding")] = true }
        assertEquals("avatar://0", repository.userPreferences.first().profileImageUri)

        store.edit { it[int("budget_cycle_start_day")] = 45 }
        assertEquals(31, repository.budgetCycleStartDay.first())
        store.edit { it[int("budget_cycle_start_day")] = 0 }
        assertEquals(1, repository.budgetCycleStartDay.first())
        store.edit { it[int("budget_cycle_start_day")] = -1 }
        assertEquals(-1, repository.budgetCycleStartDay.first())

        store.edit { it[string("theme_style")] = "NOPE" }
        assertEquals("DYNAMIC", repository.userPreferences.first().themeStyle.name)

        store.edit { it[string("upi_id")] = "   " }
        assertNull(repository.upiId.first())

        store.edit { it[bool("balance_hidden")] = true }
        assertTrue(repository.isBalanceHidden.first())
        assertTrue(repository.isAmountHiddenEverywhere.first())
    }

    @Test
    fun `setters write the pinned keys`() = runBlocking<Unit> {
        repository.updateThemeStyle(ThemeStyle.BRANDED)
        assertEquals("BRANDED", stored()[string("theme_style")])
        repository.updateAccentColor(AccentColor.PINE)
        assertEquals("PINE", stored()[string("accent_color")])
        repository.updateAppFont(AppFont.SYSTEM)
        assertEquals("SYSTEM", stored()[string("app_font")])
        repository.updateNavBarStyle(NavBarStyle.NORMAL)
        assertEquals("NORMAL", stored()[string("nav_bar_style")])
        repository.updateCoverStyle(CoverStyle.FOREST)
        assertEquals("FOREST", stored()[string("cover_style")])
        repository.updateNumberFormatStyle(NumberFormatStyle.INTERNATIONAL)
        assertEquals("INTERNATIONAL", stored()[string("number_format_style")])
        repository.updateDateFormatStyle(DateFormatStyle.DMY_NUMERIC)
        assertEquals("DMY_NUMERIC", stored()[string("date_format_style")])
        repository.setShareCardConfig(ShareCardConfig(hero = ShareHero.SUBSCRIPTIONS, period = SharePeriod.LAST_MONTH))
        assertEquals("SUBSCRIPTIONS", stored()[string("share_card_hero")])
        assertEquals("LAST_MONTH", stored()[string("share_card_period")])

        repository.updateDarkThemeEnabled(true)
        assertEquals(true, stored()[bool("dark_theme_enabled")])
        repository.updateDarkThemeEnabled(null)
        assertFalse(stored().contains(bool("dark_theme_enabled")))

        repository.setUpiId("  someone@test  ")
        assertEquals("someone@test", stored()[string("upi_id")])
        repository.setUpiId("   ")
        assertFalse(stored().contains(string("upi_id")))

        repository.setStoredLicense(StoredLicense("TEST-KEY", "instance-1", 4_000L, "Test Product"))
        assertEquals("TEST-KEY", stored()[string("license_key")])
        assertEquals("instance-1", stored()[string("license_instance_id")])
        assertEquals(4_000L, stored()[long("license_validated_at")])
        assertEquals("Test Product", stored()[string("license_product_name")])
        repository.setStoredLicense(null)
        listOf("license_key", "license_instance_id", "license_product_name").forEach {
            assertFalse(it, stored().contains(string(it)))
        }
        assertFalse(stored().contains(long("license_validated_at")))

        repository.updateMonthlyBudgetLimit(BigDecimal("1E+3"))
        assertEquals("1000", stored()[string("monthly_budget_limit")])

        repository.updateHomeSectionLayout { layout ->
            layout.map { (section, _) -> section to (section.name != "GROUPS") }
        }
        val homeSections = stored()[string("home_sections")].orEmpty().split(",")
        assertEquals("SAFE_TO_SPEND", homeSections.first())
        assertTrue("!GROUPS" in homeSections)

        repository.updateSmsScanMonths(12)
        assertEquals(12, stored()[int("sms_scan_months")])
        repository.updateSmsScanAllTime(false)
        assertEquals(false, stored()[bool("sms_scan_all_time")])
        repository.updateSmsScanUseCustomDate(true)
        assertEquals(true, stored()[bool("sms_scan_use_custom_date")])
        repository.updateSmsScanCustomDate(1_500L)
        assertEquals(1_500L, stored()[long("sms_scan_custom_date")])
        repository.setLastScanTimestamp(2_500L)
        assertEquals(2_500L, stored()[long("last_scan_timestamp")])
        repository.setLastScanPeriod(4)
        assertEquals(4, stored()[int("last_scan_period")])

        repository.setScheduledFolderBackupEnabled(true)
        assertEquals(true, stored()[bool("scheduled_folder_backup_enabled")])
        repository.setScheduledFolderBackupTreeUri("content://test/tree")
        assertEquals("content://test/tree", stored()[string("scheduled_folder_backup_tree_uri")])
        repository.setScheduledFolderBackupLastTimestamp(7_500L)
        assertEquals(7_500L, stored()[long("scheduled_folder_backup_last_timestamp")])
        repository.setExportIncludeReceipts(false)
        assertEquals(false, stored()[bool("export_include_receipts")])
        repository.setFolderBackupIncludeReceipts(true)
        assertEquals(true, stored()[bool("folder_backup_include_receipts")])

        repository.setDailySummaryEnabled(true)
        assertEquals(true, stored()[bool("daily_summary_enabled")])
        repository.setWeeklySummaryEnabled(true)
        assertEquals(true, stored()[bool("weekly_summary_enabled")])
        repository.setWeeklySummaryLastWeek("2026-02-02")
        assertEquals("2026-02-02", stored()[string("weekly_summary_last_week")])
        repository.setBudgetAlertsEnabled(true)
        assertEquals(true, stored()[bool("budget_alerts_enabled")])
        repository.setConscienceEnabled(true)
        assertEquals(true, stored()[bool("conscience_enabled")])
        repository.retireConscienceLine("line-1")
        assertEquals(setOf("line-1"), stored()[stringSet("conscience_retired_lines")])

        repository.setShakeToHideAmounts(true)
        assertEquals(true, stored()[bool("shake_to_hide_amounts")])
        repository.setAppLockEnabled(true)
        assertEquals(true, stored()[bool("app_lock_enabled")])
        repository.setAppLockTimeoutWithTimestamp(10)
        assertEquals(10, stored()[int("app_lock_timeout_minutes")])
        repository.setLastAuthTimestamp(3_500L)
        assertEquals(3_500L, stored()[long("last_auth_timestamp")])

        repository.setProCachedIsPro(true)
        assertEquals(true, stored()[bool("pro_cached_is_pro")])
        repository.markStatementImported(5_500L)
        assertEquals(5_500L, stored()[long("last_statement_import_at")])

        repository.addDismissedRecurringSuggestions(listOf("a", "b"))
        repository.removeDismissedRecurringSuggestion("a")
        assertEquals(setOf("b"), stored()[stringSet("dismissed_recurring_suggestions")])

        repository.setUnifiedCurrencyMode(true)
        assertEquals(true, stored()[bool("unified_currency_mode")])
        repository.setDisplayCurrency("GBP")
        assertEquals("GBP", stored()[string("display_currency")])
    }

    @Test
    fun `explicit base currency beats the main account`() = runBlocking {
        repository.applyMainAccountCurrency("USD")
        assertEquals("USD", stored()[string("base_currency")])
        assertFalse(stored().contains(bool("base_currency_user_set")))

        repository.updateBaseCurrency("EUR")
        assertEquals("EUR", stored()[string("base_currency")])
        assertEquals(true, stored()[bool("base_currency_user_set")])

        repository.applyMainAccountCurrency("JPY")
        assertEquals("EUR", repository.baseCurrency.first())
    }

    @Test
    fun `per-item alert keys keep their format`() = runBlocking {
        assertTrue(repository.claimBudgetAlert(7, "2026-01", BudgetAlertTier.WARNING))
        assertEquals("2026-01#WARNING", stored()[string("budget_alert_7")])
        assertFalse(repository.claimBudgetAlert(7, "2026-01", BudgetAlertTier.WARNING))

        val told = bool("low_balance_told_TestBank|0000")
        assertTrue(repository.claimLowBalanceAlert("TestBank", "0000", isLow = true))
        assertFalse(repository.claimLowBalanceAlert("TestBank", "0000", isLow = true))
        assertEquals(true, stored()[told])

        repository.moveLowBalanceAlert("TestBank", "0000", "OtherBank")
        assertFalse(stored().contains(told))
        val moved = bool("low_balance_told_OtherBank|0000")
        assertEquals(true, stored()[moved])

        repository.claimLowBalanceAlert("OtherBank", "0000", isLow = false)
        assertFalse(stored().contains(moved))
    }

    @Test
    fun `recurring offers are logged per merchant and day`() = runBlocking {
        val today = LocalDate.of(2026, 1, 2)
        val log = stringSet("recurring_offer_log")

        assertTrue(repository.tryRecordRecurringOffer("merchant-1", today, 30))
        assertEquals(setOf("merchant-1|${today.toEpochDay()}"), stored()[log])
        assertFalse(repository.tryRecordRecurringOffer("merchant-2", today, 30))

        repository.releaseRecurringOffer("merchant-1", today)
        assertEquals(emptySet<String>(), stored()[log])
    }

    @Test
    fun `one-shot claims fire once`() = runBlocking {
        assertTrue(repository.claimShakeToHideHint())
        assertFalse(repository.claimShakeToHideHint())
        assertEquals(true, stored()[bool("shake_to_hide_hint_shown")])

        assertTrue(repository.claimAppLockNoScreenLockNotice())
        assertFalse(repository.claimAppLockNoScreenLockNotice())
        assertEquals(true, stored()[bool("app_lock_no_screen_lock_notice_shown")])

        val dayBefore = LocalDate.now().toEpochDay()
        assertTrue(repository.claimSupportNudge())
        assertFalse(repository.claimSupportNudge())
        val shownOn = stored()[long("support_nudge_last_shown_epoch_day")]
        assertTrue("$shownOn", shownOn != null && shownOn in dayBefore..LocalDate.now().toEpochDay())
    }
}
