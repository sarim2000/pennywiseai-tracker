package com.pennywiseai.tracker.data.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.ParameterizedType

/**
 * These names are what's on the user's disk. Renaming one, or changing its value type,
 * silently resets that setting on every existing install, so a failure here is a signal
 * to stop rather than to update the table. Adding a key is the only routine change.
 */
class UserPreferencesKeysGoldenTest {

    private val onboarded = booleanPreferencesKey("has_completed_onboarding")
    private val scanAllTime = booleanPreferencesKey("sms_scan_all_time")

    private fun declaredKeys(): List<Pair<String, String>> =
        UserPreferencesRepository.PreferencesKeys::class.java.declaredFields
            .filter { Preferences.Key::class.java.isAssignableFrom(it.type) }
            .map { field ->
                field.isAccessible = true
                val key = field.get(null) as Preferences.Key<*>
                val valueType = (field.genericType as ParameterizedType).actualTypeArguments.single().typeName
                key.name to VALUE_TYPES.getValue(valueType)
            }

    @Test
    fun `static keys keep their on-disk names and value types`() {
        assertEquals(GOLDEN_KEYS, declaredKeys().toMap())
    }

    @Test
    fun `no two constants share an on-disk name`() {
        val names = declaredKeys().map { it.first }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `store keeps its file name`() {
        assertEquals("user_preferences", USER_PREFERENCES_STORE_NAME)
    }

    @Test
    fun `migration turns all-time scan off for onboarded users who never chose`() = runBlocking {
        val theme = stringPreferencesKey("theme_style")
        val old = preferencesOf(onboarded to true, theme to "BRANDED")

        assertTrue(SmsScanAllTimeMigration.shouldMigrate(old))
        assertEquals(
            preferencesOf(onboarded to true, theme to "BRANDED", scanAllTime to false),
            SmsScanAllTimeMigration.migrate(old)
        )
    }

    @Test
    fun `migration leaves an explicit all-time choice alone`() = runBlocking {
        assertFalse(SmsScanAllTimeMigration.shouldMigrate(preferencesOf(onboarded to true, scanAllTime to true)))
        assertFalse(SmsScanAllTimeMigration.shouldMigrate(preferencesOf(onboarded to true, scanAllTime to false)))
    }

    @Test
    fun `migration skips users who have not finished onboarding`() = runBlocking {
        assertFalse(SmsScanAllTimeMigration.shouldMigrate(preferencesOf()))
        assertFalse(SmsScanAllTimeMigration.shouldMigrate(preferencesOf(onboarded to false)))
    }

    private companion object {
        val VALUE_TYPES = mapOf(
            "java.lang.Boolean" to "Boolean",
            "java.lang.Integer" to "Int",
            "java.lang.Long" to "Long",
            "java.lang.String" to "String",
            "java.util.Set<java.lang.String>" to "Set<String>",
        )

        val GOLDEN_KEYS = mapOf(
            "accent_color" to "String",
            "active_download_id" to "Long",
            "analytics_chart_type" to "String",
            "analytics_group_by" to "String",
            "app_font" to "String",
            "app_lock_enabled" to "Boolean",
            "app_lock_no_screen_lock_notice_shown" to "Boolean",
            "app_lock_timeout_minutes" to "Int",
            "atm_cash_to_wallet" to "Boolean",
            "background_oem_step_confirmed_on" to "String",
            "balance_hidden" to "Boolean",
            "base_currency" to "String",
            "base_currency_user_set" to "Boolean",
            "blur_effects_enabled" to "Boolean",
            "budget_alerts_enabled" to "Boolean",
            "budget_cycle_start_day" to "Int",
            "conscience_enabled" to "Boolean",
            "conscience_log" to "Set<String>",
            "conscience_retired_lines" to "Set<String>",
            "count_credit_as_expense" to "Boolean",
            "cover_style" to "String",
            "daily_summary_enabled" to "Boolean",
            "dark_theme_enabled" to "Boolean",
            "date_format_style" to "String",
            "developer_mode_enabled" to "Boolean",
            "dismissed_recurring_suggestions" to "Set<String>",
            "display_currency" to "String",
            "dynamic_color_enabled" to "Boolean",
            "export_include_receipts" to "Boolean",
            "first_launch_time" to "Long",
            "folder_backup_include_receipts" to "Boolean",
            "has_completed_onboarding" to "Boolean",
            "has_migrated_to_budget_groups" to "Boolean",
            "has_shown_review_prompt" to "Boolean",
            "has_shown_scan_tutorial" to "Boolean",
            "has_skipped_sms_permission" to "Boolean",
            "has_used_full_resync" to "Boolean",
            "hidden_nav_tabs" to "Set<String>",
            "home_sections" to "String",
            "is_amoled_mode" to "Boolean",
            "last_auth_timestamp" to "Long",
            "last_review_prompt_time" to "Long",
            "last_scan_period" to "Int",
            "last_scan_timestamp" to "Long",
            "last_seen_app_version" to "String",
            "last_statement_import_at" to "Long",
            "license_instance_id" to "String",
            "license_key" to "String",
            "license_product_name" to "String",
            "license_validated_at" to "Long",
            "main_account_key" to "String",
            "monthly_budget_limit" to "String",
            "nav_bar_style" to "String",
            "number_format_style" to "String",
            "payday_suggestion_handled" to "Boolean",
            "pro_cached_is_pro" to "Boolean",
            "pro_home_card_handled" to "Boolean",
            "profile_background_color" to "Int",
            "profile_image_uri" to "String",
            "recurring_home_card_dismissed_at" to "Long",
            "recurring_home_card_seen" to "Set<String>",
            "recurring_offer_log" to "Set<String>",
            "review_prompt_handled_year" to "String",
            "safe_to_spend_excluded_accounts" to "Set<String>",
            "scheduled_folder_backup_enabled" to "Boolean",
            "scheduled_folder_backup_last_timestamp" to "Long",
            "scheduled_folder_backup_tree_uri" to "String",
            "selected_profile_id" to "Long",
            "shake_to_hide_amounts" to "Boolean",
            "shake_to_hide_hint_shown" to "Boolean",
            "share_card_hero" to "String",
            "share_card_period" to "String",
            "share_prompt_handled_month" to "String",
            "sms_scan_all_time" to "Boolean",
            "sms_scan_custom_date" to "Long",
            "sms_scan_months" to "Int",
            "sms_scan_use_custom_date" to "Boolean",
            "statement_column_mappings" to "String",
            "support_nudge_last_shown_epoch_day" to "Long",
            "system_prompt" to "String",
            "theme_style" to "String",
            "tracking_card_snoozed_until" to "Long",
            "unified_currency_mode" to "Boolean",
            "upi_id" to "String",
            "use_contacts_for_vpa" to "Boolean",
            "user_name" to "String",
            "weekly_summary_enabled" to "Boolean",
            "weekly_summary_last_week" to "String",
        )
    }
}
