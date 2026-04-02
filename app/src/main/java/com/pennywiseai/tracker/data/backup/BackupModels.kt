package com.pennywiseai.tracker.data.backup

import com.google.gson.annotations.SerializedName
import com.pennywiseai.tracker.data.database.entity.*
import java.time.LocalDateTime

/**
 * Root container for PennyWise backup data
 */
data class PennyWiseBackup(
    @SerializedName("_format")
    val format: String = "PennyWise Backup v1.0",
    
    @SerializedName("_warning")
    val warning: String = "Contains sensitive financial data. Keep this file secure.",
    
    @SerializedName("_created")
    val created: String = LocalDateTime.now().toString(),
    
    @SerializedName("metadata")
    val metadata: BackupMetadata,
    
    @SerializedName("database")
    val database: DatabaseSnapshot,
    
    @SerializedName("preferences")
    val preferences: PreferencesSnapshot
)

/**
 * Metadata about the backup
 */
data class BackupMetadata(
    @SerializedName("export_id")
    val exportId: String,
    
    @SerializedName("app_version")
    val appVersion: String,
    
    @SerializedName("database_version")
    val databaseVersion: Int,
    
    @SerializedName("device")
    val device: String,
    
    @SerializedName("android_version")
    val androidVersion: Int,
    
    @SerializedName("statistics")
    val statistics: BackupStatistics
)

/**
 * Statistics about the backup content
 */
data class BackupStatistics(
    @SerializedName("total_transactions")
    val totalTransactions: Int,
    
    @SerializedName("total_categories")
    val totalCategories: Int,
    
    @SerializedName("total_cards")
    val totalCards: Int,
    
    @SerializedName("total_subscriptions")
    val totalSubscriptions: Int,
    
    @SerializedName("total_rules")
    val totalRules: Int = 0,
    
    @SerializedName("total_rule_applications")
    val totalRuleApplications: Int = 0,
    
    @SerializedName("total_exchange_rates")
    val totalExchangeRates: Int = 0,
    
    @SerializedName("total_budgets")
    val totalBudgets: Int = 0,
    
    @SerializedName("total_budget_categories")
    val totalBudgetCategories: Int = 0,
    
    @SerializedName("total_transaction_splits")
    val totalTransactionSplits: Int = 0,
    
    @SerializedName("total_bank_notifications")
    val totalBankNotifications: Int = 0,
    
    @SerializedName("date_range")
    val dateRange: DateRange?
)

/**
 * Date range of transactions
 */
data class DateRange(
    @SerializedName("earliest")
    val earliest: String?,
    
    @SerializedName("latest")
    val latest: String?
)

/**
 * Complete database snapshot
 */
data class DatabaseSnapshot(
    @SerializedName("transactions")
    val transactions: List<TransactionEntity>,
    
    @SerializedName("categories")
    val categories: List<CategoryEntity>,
    
    @SerializedName("cards")
    val cards: List<CardEntity>,
    
    @SerializedName("account_balances")
    val accountBalances: List<AccountBalanceEntity>,
    
    @SerializedName("subscriptions")
    val subscriptions: List<SubscriptionEntity>,
    
    @SerializedName("merchant_mappings")
    val merchantMappings: List<MerchantMappingEntity>,
    
    @SerializedName("unrecognized_sms")
    val unrecognizedSms: List<UnrecognizedSmsEntity>,
    
    @SerializedName("chat_messages")
    val chatMessages: List<ChatMessage>,
    
    @SerializedName("rules")
    val rules: List<RuleEntity> = emptyList(),
    
    @SerializedName("rule_applications")
    val ruleApplications: List<RuleApplicationEntity> = emptyList(),
    
    @SerializedName("exchange_rates")
    val exchangeRates: List<ExchangeRateEntity> = emptyList(),
    
    @SerializedName("budgets")
    val budgets: List<BudgetEntity> = emptyList(),
    
    @SerializedName("budget_categories")
    val budgetCategories: List<BudgetCategoryEntity> = emptyList(),
    
    @SerializedName("transaction_splits")
    val transactionSplits: List<TransactionSplitEntity> = emptyList(),
    
    @SerializedName("bank_notifications")
    val bankNotifications: List<BankNotificationEntity> = emptyList()
)

/**
 * User preferences snapshot
 */
data class PreferencesSnapshot(
    @SerializedName("theme")
    val theme: ThemePreferences,
    
    @SerializedName("sms")
    val sms: SmsPreferences,
    
    @SerializedName("developer")
    val developer: DeveloperPreferences,
    
    @SerializedName("app")
    val app: AppPreferences
)

/**
 * Theme-related preferences
 */
data class ThemePreferences(
    @SerializedName("is_dark_theme_enabled")
    val isDarkThemeEnabled: Boolean?,
    
    @SerializedName("is_dynamic_color_enabled")
    val isDynamicColorEnabled: Boolean
)

/**
 * SMS-related preferences
 */
data class SmsPreferences(
    @SerializedName("has_skipped_sms_permission")
    val hasSkippedSmsPermission: Boolean,
    
    @SerializedName("sms_scan_months")
    val smsScanMonths: Int,
    
    @SerializedName("last_scan_timestamp")
    val lastScanTimestamp: Long?,
    
    @SerializedName("last_scan_period")
    val lastScanPeriod: Int?
)

/**
 * Developer mode preferences
 */
data class DeveloperPreferences(
    @SerializedName("is_developer_mode_enabled")
    val isDeveloperModeEnabled: Boolean,
    
    @SerializedName("system_prompt")
    val systemPrompt: String?
)

/**
 * App-related preferences
 */
data class AppPreferences(
    @SerializedName("has_shown_scan_tutorial")
    val hasShownScanTutorial: Boolean,
    
    @SerializedName("first_launch_time")
    val firstLaunchTime: Long?,
    
    @SerializedName("has_shown_review_prompt")
    val hasShownReviewPrompt: Boolean,
    
    @SerializedName("last_review_prompt_time")
    val lastReviewPromptTime: Long?
)

/**
 * Import result
 */
sealed class ImportResult {
    data class Success(
        val importedTransactions: Int,
        val importedCategories: Int,
        val skippedDuplicates: Int
    ) : ImportResult()
    
    data class Error(val message: String) : ImportResult()
}

/**
 * Export result
 */
sealed class ExportResult {
    data class Success(val file: java.io.File) : ExportResult()
    data class Error(val message: String) : ExportResult()
    data class Progress(val current: Int, val total: Int) : ExportResult()
}

/**
 * Import strategy options
 */
enum class ImportStrategy {
    REPLACE_ALL,    // Replace all existing data
    MERGE,          // Merge with existing data (skip duplicates)
    SELECTIVE       // User selects what to import
}

/**
 * Privacy level for export
 */
enum class ExportPrivacy {
    FULL,          // Export everything as-is
    MASKED,        // Mask sensitive data like account numbers
    ANONYMOUS      // Remove merchant names and descriptions
}