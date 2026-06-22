package com.pennywiseai.tracker.data.backup

import com.pennywiseai.tracker.data.database.entity.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * Backup format models.
 *
 * ## Compatibility contract (see docs/backup-format.md)
 *
 * Serialized with [backupJson] (kotlinx.serialization), which is configured to
 * be tolerant in both directions:
 *  - **Backward** (old backup → new app): every field here has a Kotlin
 *    default, so a key missing from an older backup falls back to that default
 *    instead of crashing. THIS is the property that fixes the "can't restore an
 *    old backup" bug — never remove a default from a field that ships in a
 *    release.
 *  - **Forward** (new backup → old app): `ignoreUnknownKeys = true` means a
 *    field added in a newer version is silently ignored by an older app.
 *
 * Invariant for maintainers: **every property in this file, and every backup-
 * serialized entity, must have a default value.** A `BackupSchemaGuardTest`
 * enforces this in CI.
 */
@Serializable
data class PennyWiseBackup(
    @SerialName("_format")
    val format: String = CURRENT_FORMAT,

    @SerialName("_warning")
    val warning: String = "Contains sensitive financial data. Keep this file secure.",

    @SerialName("_created")
    val created: String = LocalDateTime.now().toString(),

    @SerialName("metadata")
    val metadata: BackupMetadata = BackupMetadata(),

    @SerialName("database")
    val database: DatabaseSnapshot = DatabaseSnapshot(),

    @SerialName("preferences")
    val preferences: PreferencesSnapshot = PreferencesSnapshot()
) {
    companion object {
        /** Current format string written by this app version. */
        const val CURRENT_FORMAT = "PennyWise Backup v1.2"

        /** Prefix accepted on import — any `v1.x` backup is compatible. */
        const val COMPATIBLE_PREFIX = "PennyWise Backup v1"
    }
}

/**
 * Metadata about the backup. Informational only — never block an import on it,
 * so every field is defaulted and a malformed/absent metadata block still lets
 * the database restore.
 */
@Serializable
data class BackupMetadata(
    @SerialName("export_id")
    val exportId: String = "",

    @SerialName("app_version")
    val appVersion: String = "",

    @SerialName("database_version")
    val databaseVersion: Int = 0,

    @SerialName("device")
    val device: String = "",

    @SerialName("android_version")
    val androidVersion: Int = 0,

    @SerialName("statistics")
    val statistics: BackupStatistics = BackupStatistics()
)

/**
 * Statistics about the backup content.
 */
@Serializable
data class BackupStatistics(
    @SerialName("total_transactions")
    val totalTransactions: Int = 0,

    @SerialName("total_categories")
    val totalCategories: Int = 0,

    @SerialName("total_cards")
    val totalCards: Int = 0,

    @SerialName("total_subscriptions")
    val totalSubscriptions: Int = 0,

    @SerialName("total_rules")
    val totalRules: Int = 0,

    @SerialName("total_rule_applications")
    val totalRuleApplications: Int = 0,

    @SerialName("total_exchange_rates")
    val totalExchangeRates: Int = 0,

    @SerialName("total_budgets")
    val totalBudgets: Int = 0,

    @SerialName("total_budget_categories")
    val totalBudgetCategories: Int = 0,

    @SerialName("total_transaction_splits")
    val totalTransactionSplits: Int = 0,

    @SerialName("total_bank_notifications")
    val totalBankNotifications: Int = 0,

    @SerialName("total_loans")
    val totalLoans: Int = 0,

    @SerialName("total_transaction_groups")
    val totalTransactionGroups: Int = 0,

    @SerialName("total_profiles")
    val totalProfiles: Int = 0,

    @SerialName("total_budget_month_snapshots")
    val totalBudgetMonthSnapshots: Int = 0,

    @SerialName("total_budget_category_month_snapshots")
    val totalBudgetCategoryMonthSnapshots: Int = 0,

    @SerialName("date_range")
    val dateRange: DateRange? = null
)

/**
 * Date range of transactions.
 */
@Serializable
data class DateRange(
    @SerialName("earliest")
    val earliest: String? = null,

    @SerialName("latest")
    val latest: String? = null
)

/**
 * Complete database snapshot.
 *
 * Every list defaults to `emptyList()`, so a backup that omits a whole table
 * (older formats, or a newer table an older app never knew about) imports
 * cleanly with that table simply empty.
 */
@Serializable
data class DatabaseSnapshot(
    @SerialName("transactions")
    val transactions: List<TransactionEntity> = emptyList(),

    @SerialName("categories")
    val categories: List<CategoryEntity> = emptyList(),

    @SerialName("cards")
    val cards: List<CardEntity> = emptyList(),

    @SerialName("account_balances")
    val accountBalances: List<AccountBalanceEntity> = emptyList(),

    @SerialName("subscriptions")
    val subscriptions: List<SubscriptionEntity> = emptyList(),

    @SerialName("merchant_mappings")
    val merchantMappings: List<MerchantMappingEntity> = emptyList(),

    @SerialName("unrecognized_sms")
    val unrecognizedSms: List<UnrecognizedSmsEntity> = emptyList(),

    @SerialName("chat_messages")
    val chatMessages: List<ChatMessage> = emptyList(),

    @SerialName("rules")
    val rules: List<RuleEntity> = emptyList(),

    @SerialName("rule_applications")
    val ruleApplications: List<RuleApplicationEntity> = emptyList(),

    @SerialName("exchange_rates")
    val exchangeRates: List<ExchangeRateEntity> = emptyList(),

    @SerialName("budgets")
    val budgets: List<BudgetEntity> = emptyList(),

    @SerialName("budget_categories")
    val budgetCategories: List<BudgetCategoryEntity> = emptyList(),

    @SerialName("transaction_splits")
    val transactionSplits: List<TransactionSplitEntity> = emptyList(),

    @SerialName("bank_notifications")
    val bankNotifications: List<BankNotificationEntity> = emptyList(),

    @SerialName("loans")
    val loans: List<LoanEntity> = emptyList(),

    @SerialName("transaction_groups")
    val transactionGroups: List<TransactionGroupEntity> = emptyList(),

    @SerialName("profiles")
    val profiles: List<ProfileEntity> = emptyList(),

    @SerialName("budget_month_snapshots")
    val budgetMonthSnapshots: List<BudgetMonthSnapshotEntity> = emptyList(),

    @SerialName("budget_category_month_snapshots")
    val budgetCategoryMonthSnapshots: List<BudgetCategoryMonthSnapshotEntity> = emptyList()
)

/**
 * User preferences snapshot. Every section is defaulted so a backup missing a
 * whole section (or written by an app that didn't have it yet) still imports.
 */
@Serializable
data class PreferencesSnapshot(
    @SerialName("theme")
    val theme: ThemePreferences = ThemePreferences(),

    @SerialName("sms")
    val sms: SmsPreferences = SmsPreferences(),

    @SerialName("developer")
    val developer: DeveloperPreferences = DeveloperPreferences(),

    @SerialName("app")
    val app: AppPreferences = AppPreferences()
)

@Serializable
data class ThemePreferences(
    @SerialName("is_dark_theme_enabled")
    val isDarkThemeEnabled: Boolean? = null,

    @SerialName("is_dynamic_color_enabled")
    val isDynamicColorEnabled: Boolean = false
)

@Serializable
data class SmsPreferences(
    @SerialName("has_skipped_sms_permission")
    val hasSkippedSmsPermission: Boolean = false,

    @SerialName("sms_scan_months")
    val smsScanMonths: Int = 6,

    @SerialName("last_scan_timestamp")
    val lastScanTimestamp: Long? = null,

    @SerialName("last_scan_period")
    val lastScanPeriod: Int? = null,

    @SerialName("sms_scan_use_custom_date")
    val smsScanUseCustomDate: Boolean = false,

    @SerialName("sms_scan_custom_date")
    val smsScanCustomDate: Long? = null
)

@Serializable
data class DeveloperPreferences(
    @SerialName("is_developer_mode_enabled")
    val isDeveloperModeEnabled: Boolean = false,

    @SerialName("system_prompt")
    val systemPrompt: String? = null
)

@Serializable
data class AppPreferences(
    @SerialName("has_shown_scan_tutorial")
    val hasShownScanTutorial: Boolean = false,

    @SerialName("first_launch_time")
    val firstLaunchTime: Long? = null,

    @SerialName("has_shown_review_prompt")
    val hasShownReviewPrompt: Boolean = false,

    @SerialName("last_review_prompt_time")
    val lastReviewPromptTime: Long? = null
)

/**
 * Import result. [skippedRows] counts entity rows that failed to insert and
 * were skipped so a single bad row never aborts the whole restore.
 */
sealed class ImportResult {
    data class Success(
        val importedTransactions: Int,
        val importedCategories: Int,
        val skippedDuplicates: Int,
        val skippedRows: Int = 0
    ) : ImportResult()

    data class Error(val message: String) : ImportResult()
}

/**
 * Export result.
 */
sealed class ExportResult {
    data class Success(val file: java.io.File) : ExportResult()
    data class Error(val message: String) : ExportResult()
    data class Progress(val current: Int, val total: Int) : ExportResult()
}

/**
 * Import strategy options.
 */
enum class ImportStrategy {
    REPLACE_ALL,    // Replace all existing data
    MERGE,          // Merge with existing data (skip duplicates)
    SELECTIVE       // User selects what to import
}

/**
 * Privacy level for export.
 */
enum class ExportPrivacy {
    FULL,          // Export everything as-is
    MASKED,        // Mask sensitive data like account numbers
    ANONYMOUS      // Remove merchant names and descriptions
}
