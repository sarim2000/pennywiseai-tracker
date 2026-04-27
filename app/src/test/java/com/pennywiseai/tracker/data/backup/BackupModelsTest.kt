package com.pennywiseai.tracker.data.backup

import com.google.gson.GsonBuilder
import com.pennywiseai.tracker.data.database.entity.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class BackupModelsTest {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
        .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
        .registerTypeAdapter(BigDecimal::class.java, BigDecimalTypeAdapter())
        .create()

    @Test
    fun backupSerializationRoundtripWithAllFields() {
        val backup = PennyWiseBackup(
            metadata = BackupMetadata(
                exportId = "test-export-id",
                appVersion = "2.15.50",
                databaseVersion = 20,
                device = "Test Device",
                androidVersion = 30,
                statistics = BackupStatistics(
                    totalTransactions = 1,
                    totalCategories = 1,
                    totalCards = 0,
                    totalSubscriptions = 0,
                    totalRules = 1,
                    totalRuleApplications = 1,
                    totalExchangeRates = 1,
                    totalBudgets = 1,
                    totalBudgetCategories = 1,
                    totalTransactionSplits = 1,
                    totalBankNotifications = 1,
                    dateRange = DateRange(earliest = "2024-01-01T00:00:00", latest = "2024-01-02T00:00:00")
                )
            ),
            database = DatabaseSnapshot(
                transactions = listOf(
                    TransactionEntity(
                        id = 1,
                        amount = BigDecimal("100.0"),
                        merchantName = "Test Merchant",
                        category = "Food",
                        transactionType = TransactionType.EXPENSE,
                        dateTime = LocalDateTime.of(2024, 1, 1, 10, 0),
                        description = "Test description",
                        smsBody = "Test SMS",
                        bankName = "Test Bank",
                        smsSender = "TEST",
                        accountNumber = "1234567890",
                        balanceAfter = BigDecimal("1000.0"),
                        transactionHash = "hash123",
                        isRecurring = false,
                        isDeleted = false,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now(),
                        currency = "INR",
                        fromAccount = null,
                        toAccount = null,
                        reference = null
                    )
                ),
                categories = listOf(
                    CategoryEntity(
                        id = 1,
                        name = "Food",
                        color = "#FF0000",
                        isSystem = false,
                        isIncome = false,
                        displayOrder = 1,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                ),
                cards = emptyList(),
                accountBalances = emptyList(),
                subscriptions = emptyList(),
                merchantMappings = emptyList(),
                unrecognizedSms = emptyList(),
                chatMessages = emptyList(),
                rules = listOf(
                    RuleEntity(
                        id = "rule-1",
                        name = "Test Rule",
                        description = "Test description",
                        priority = 0,
                        conditions = """{"merchant": ".*"}""",
                        actions = """{"category": "Food"}""",
                        isActive = true,
                        isSystemTemplate = false,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                ),
                ruleApplications = listOf(
                    RuleApplicationEntity(
                        id = "app-1",
                        ruleId = "rule-1",
                        ruleName = "Test Rule",
                        transactionId = "1",
                        fieldsModified = """["category"]""",
                        appliedAt = LocalDateTime.now()
                    )
                ),
                exchangeRates = listOf(
                    ExchangeRateEntity(
                        id = 1,
                        fromCurrency = "USD",
                        toCurrency = "INR",
                        rate = BigDecimal("83.0"),
                        provider = "test",
                        updatedAt = LocalDateTime.now(),
                        updatedAtUnix = 0,
                        expiresAt = LocalDateTime.now().plusDays(1),
                        expiresAtUnix = 0,
                        isCustomRate = false
                    )
                ),
                budgets = listOf(
                    BudgetEntity(
                        id = 1,
                        name = "Monthly Food",
                        limitAmount = BigDecimal("5000.0"),
                        periodType = BudgetPeriodType.MONTHLY,
                        startDate = LocalDate.of(2024, 1, 1),
                        endDate = LocalDate.of(2024, 12, 31),
                        currency = "INR",
                        isActive = true,
                        includeAllCategories = false,
                        color = "#1565C0",
                        groupType = BudgetGroupType.LIMIT,
                        displayOrder = 0,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                ),
                budgetCategories = listOf(
                    BudgetCategoryEntity(
                        id = 1,
                        budgetId = 1,
                        categoryName = "Food",
                        budgetAmount = BigDecimal("3000.0")
                    )
                ),
                transactionSplits = listOf(
                    TransactionSplitEntity(
                        id = 1,
                        transactionId = 1,
                        category = "Food",
                        amount = BigDecimal("50.0"),
                        createdAt = LocalDateTime.now()
                    )
                ),
                bankNotifications = listOf(
                    BankNotificationEntity(
                        id = 1,
                        packageName = "com.test.bank",
                        senderAlias = "Test Bank",
                        messageBody = "Test notification",
                        messageHash = "hash456",
                        postedAt = LocalDateTime.now(),
                        processed = false,
                        transactionId = null,
                        createdAt = LocalDateTime.now()
                    )
                )
            ),
            preferences = PreferencesSnapshot(
                theme = ThemePreferences(
                    isDarkThemeEnabled = true,
                    isDynamicColorEnabled = false
                ),
                sms = SmsPreferences(
                    hasSkippedSmsPermission = false,
                    smsScanMonths = 6,
                    lastScanTimestamp = null,
                    lastScanPeriod = null
                ),
                developer = DeveloperPreferences(
                    isDeveloperModeEnabled = false,
                    systemPrompt = null
                ),
                app = AppPreferences(
                    hasShownScanTutorial = true,
                    firstLaunchTime = null,
                    hasShownReviewPrompt = false,
                    lastReviewPromptTime = null
                )
            )
        )

        val json = gson.toJson(backup)
        assertNotNull(json)

        val deserialized = gson.fromJson(json, PennyWiseBackup::class.java)
        assertNotNull(deserialized)

        assertEquals(backup.metadata.statistics.totalRules, deserialized.metadata.statistics.totalRules)
        assertEquals(backup.metadata.statistics.totalRuleApplications, deserialized.metadata.statistics.totalRuleApplications)
        assertEquals(backup.metadata.statistics.totalExchangeRates, deserialized.metadata.statistics.totalExchangeRates)
        assertEquals(backup.metadata.statistics.totalBudgets, deserialized.metadata.statistics.totalBudgets)
        assertEquals(backup.metadata.statistics.totalBudgetCategories, deserialized.metadata.statistics.totalBudgetCategories)
        assertEquals(backup.metadata.statistics.totalTransactionSplits, deserialized.metadata.statistics.totalTransactionSplits)
        assertEquals(backup.metadata.statistics.totalBankNotifications, deserialized.metadata.statistics.totalBankNotifications)

        assertEquals(1, deserialized.database.rules.size)
        assertEquals(1, deserialized.database.ruleApplications.size)
        assertEquals(1, deserialized.database.exchangeRates.size)
        assertEquals(1, deserialized.database.budgets.size)
        assertEquals(1, deserialized.database.budgetCategories.size)
        assertEquals(1, deserialized.database.transactionSplits.size)
        assertEquals(1, deserialized.database.bankNotifications.size)

        val rule = deserialized.database.rules[0]
        assertEquals("Test Rule", rule.name)

        val rate = deserialized.database.exchangeRates[0]
        assertEquals("USD", rate.fromCurrency)
        assertEquals(BigDecimal("83.0"), rate.rate)

        val budget = deserialized.database.budgets[0]
        assertEquals("Monthly Food", budget.name)
        assertEquals(BudgetPeriodType.MONTHLY, budget.periodType)
    }

    @Test
    fun backupSerializationWithEmptyLists() {
        val backup = PennyWiseBackup(
            metadata = BackupMetadata(
                exportId = "empty-test",
                appVersion = "2.15.50",
                databaseVersion = 20,
                device = "Test Device",
                androidVersion = 30,
                statistics = BackupStatistics(
                    totalTransactions = 0,
                    totalCategories = 0,
                    totalCards = 0,
                    totalSubscriptions = 0,
                    totalRules = 0,
                    totalRuleApplications = 0,
                    totalExchangeRates = 0,
                    totalBudgets = 0,
                    totalBudgetCategories = 0,
                    totalTransactionSplits = 0,
                    totalBankNotifications = 0,
                    dateRange = null
                )
            ),
            database = DatabaseSnapshot(
                transactions = emptyList(),
                categories = emptyList(),
                cards = emptyList(),
                accountBalances = emptyList(),
                subscriptions = emptyList(),
                merchantMappings = emptyList(),
                unrecognizedSms = emptyList(),
                chatMessages = emptyList(),
                rules = emptyList(),
                ruleApplications = emptyList(),
                exchangeRates = emptyList(),
                budgets = emptyList(),
                budgetCategories = emptyList(),
                transactionSplits = emptyList(),
                bankNotifications = emptyList()
            ),
            preferences = PreferencesSnapshot(
                theme = ThemePreferences(isDarkThemeEnabled = null, isDynamicColorEnabled = false),
                sms = SmsPreferences(hasSkippedSmsPermission = false, smsScanMonths = 6, lastScanTimestamp = null, lastScanPeriod = null),
                developer = DeveloperPreferences(isDeveloperModeEnabled = false, systemPrompt = null),
                app = AppPreferences(hasShownScanTutorial = false, firstLaunchTime = null, hasShownReviewPrompt = false, lastReviewPromptTime = null)
            )
        )

        val json = gson.toJson(backup)
        val deserialized = gson.fromJson(json, PennyWiseBackup::class.java)
        
        assertEquals(0, deserialized.database.rules.size)
        assertEquals(0, deserialized.database.exchangeRates.size)
        assertEquals(0, deserialized.database.budgets.size)
    }
}