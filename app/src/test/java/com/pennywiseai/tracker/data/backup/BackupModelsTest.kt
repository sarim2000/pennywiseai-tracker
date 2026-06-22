package com.pennywiseai.tracker.data.backup

import com.pennywiseai.tracker.data.database.SCHEMA_VERSION
import com.pennywiseai.tracker.data.database.entity.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class BackupModelsTest {

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

        val json = backupJson.encodeToString(backup)
        assertNotNull(json)

        val deserialized = backupJson.decodeFromString<PennyWiseBackup>(json)
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

    /**
     * Regression for issue #386 — backup must round-trip transaction groups and
     * loans (both ACTIVE and SETTLED), and a transaction's loan_id/group_id
     * pointers must still match the deserialised entities so the importer can
     * re-link them. The model used to omit these tables entirely.
     */
    @Test
    fun backupSerializationIncludesLoansAndTransactionGroups() {
        val now = LocalDateTime.of(2026, 5, 31, 12, 0)

        val activeLoan = LoanEntity(
            id = 11L,
            personName = "Alex",
            direction = LoanDirection.LENT,
            originalAmount = BigDecimal("500.00"),
            remainingAmount = BigDecimal("300.00"),
            currency = "INR",
            status = LoanStatus.ACTIVE,
            note = "Trip split",
            createdAt = now,
            updatedAt = now
        )
        val settledLoan = LoanEntity(
            id = 12L,
            personName = "Riya",
            direction = LoanDirection.BORROWED,
            originalAmount = BigDecimal("1000.00"),
            remainingAmount = BigDecimal.ZERO,
            currency = "INR",
            status = LoanStatus.SETTLED,
            note = null,
            createdAt = now.minusDays(30),
            updatedAt = now.minusDays(1),
            settledAt = now.minusDays(1)
        )
        val group = TransactionGroupEntity(
            id = 21L,
            name = "Goa Trip",
            note = "March holiday",
            createdAt = now,
            updatedAt = now
        )
        val transactionLinkedToLoanAndGroup = TransactionEntity(
            id = 100L,
            amount = BigDecimal("200.00"),
            merchantName = "Cafe",
            category = "Food",
            transactionType = TransactionType.EXPENSE,
            dateTime = now,
            transactionHash = "hash-loans-groups",
            createdAt = now,
            updatedAt = now,
            currency = "INR",
            loanId = activeLoan.id,
            groupId = group.id
        )

        val backup = PennyWiseBackup(
            metadata = BackupMetadata(
                exportId = "loans-groups-test",
                appVersion = "test",
                databaseVersion = SCHEMA_VERSION,
                device = "Test",
                androidVersion = 30,
                statistics = BackupStatistics(
                    totalTransactions = 1,
                    totalCategories = 0,
                    totalCards = 0,
                    totalSubscriptions = 0,
                    totalLoans = 2,
                    totalTransactionGroups = 1,
                    dateRange = null
                )
            ),
            database = DatabaseSnapshot(
                transactions = listOf(transactionLinkedToLoanAndGroup),
                categories = emptyList(),
                cards = emptyList(),
                accountBalances = emptyList(),
                subscriptions = emptyList(),
                merchantMappings = emptyList(),
                unrecognizedSms = emptyList(),
                chatMessages = emptyList(),
                loans = listOf(activeLoan, settledLoan),
                transactionGroups = listOf(group)
            ),
            preferences = PreferencesSnapshot(
                theme = ThemePreferences(isDarkThemeEnabled = null, isDynamicColorEnabled = false),
                sms = SmsPreferences(hasSkippedSmsPermission = false, smsScanMonths = 6, lastScanTimestamp = null, lastScanPeriod = null),
                developer = DeveloperPreferences(isDeveloperModeEnabled = false, systemPrompt = null),
                app = AppPreferences(hasShownScanTutorial = false, firstLaunchTime = null, hasShownReviewPrompt = false, lastReviewPromptTime = null)
            )
        )

        val json = backupJson.encodeToString(backup)
        val deserialized = backupJson.decodeFromString<PennyWiseBackup>(json)

        // Statistics survive.
        assertEquals(2, deserialized.metadata.statistics.totalLoans)
        assertEquals(1, deserialized.metadata.statistics.totalTransactionGroups)

        // Both loans round-trip with every field intact, including the SETTLED
        // status and the optional settledAt timestamp.
        assertEquals(2, deserialized.database.loans.size)
        val deserializedActive = deserialized.database.loans.first { it.id == 11L }
        assertEquals("Alex", deserializedActive.personName)
        assertEquals(LoanDirection.LENT, deserializedActive.direction)
        assertEquals(LoanStatus.ACTIVE, deserializedActive.status)
        assertEquals(BigDecimal("500.00"), deserializedActive.originalAmount)
        assertEquals(BigDecimal("300.00"), deserializedActive.remainingAmount)
        assertEquals("Trip split", deserializedActive.note)
        assertNull(deserializedActive.settledAt)

        val deserializedSettled = deserialized.database.loans.first { it.id == 12L }
        assertEquals(LoanStatus.SETTLED, deserializedSettled.status)
        assertEquals(LoanDirection.BORROWED, deserializedSettled.direction)
        assertEquals(BigDecimal.ZERO, deserializedSettled.remainingAmount)
        // Equality (not just non-null) — guards against silent timestamp drift
        // through the LocalDateTime serializer.
        assertEquals(settledLoan.settledAt, deserializedSettled.settledAt)
        assertEquals(settledLoan.createdAt, deserializedSettled.createdAt)
        assertEquals(settledLoan.updatedAt, deserializedSettled.updatedAt)

        // Transaction group round-trips with every field intact.
        assertEquals(1, deserialized.database.transactionGroups.size)
        val deserializedGroup = deserialized.database.transactionGroups[0]
        assertEquals(21L, deserializedGroup.id)
        assertEquals("Goa Trip", deserializedGroup.name)
        assertEquals("March holiday", deserializedGroup.note)

        // The transaction's loan_id / group_id still match the deserialised
        // entities, so the importer can resolve the foreign-key references.
        val deserializedTxn = deserialized.database.transactions[0]
        assertEquals(activeLoan.id, deserializedTxn.loanId)
        assertEquals(group.id, deserializedTxn.groupId)
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

        val json = backupJson.encodeToString(backup)
        val deserialized = backupJson.decodeFromString<PennyWiseBackup>(json)

        assertEquals(0, deserialized.database.rules.size)
        assertEquals(0, deserialized.database.exchangeRates.size)
        assertEquals(0, deserialized.database.budgets.size)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Forward / backward compatibility — the core of issues #414 + #415.
    // These build raw JSON shaped like an OLDER app's backup (fields the
    // current entities have were simply not written yet) and assert it
    // imports with defaults instead of crashing.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * #414 regression. An older backup's subscription has NO `billingCycle`
     * or `direction` key, and an older transaction has no `isDeleted` /
     * `profileId`. Under Gson these arrived as null on non-null fields and
     * blew up the whole restore. kotlinx must fall back to the Kotlin
     * defaults instead.
     */
    @Test
    fun oldBackupMissingNewerFields_fallsBackToDefaults() {
        val json = """
        {
          "_format": "PennyWise Backup v1.0",
          "metadata": { "export_id": "old", "app_version": "2.0.0" },
          "database": {
            "transactions": [
              {
                "id": 5,
                "amount": "250.00",
                "merchantName": "Old Merchant",
                "category": "Food",
                "transactionType": "EXPENSE",
                "dateTime": "2023-05-01T09:30:00",
                "transactionHash": "old-hash-1"
              }
            ],
            "subscriptions": [
              {
                "id": 9,
                "merchantName": "Netflix",
                "amount": "499.00",
                "nextPaymentDate": "2023-06-01"
              }
            ]
          }
        }
        """.trimIndent()

        val backup = backupJson.decodeFromString<PennyWiseBackup>(json)

        // Transaction: newer non-null columns fall back to their defaults.
        val tx = backup.database.transactions.single()
        assertEquals("Old Merchant", tx.merchantName)
        assertEquals(false, tx.isDeleted)        // default
        assertEquals("INR", tx.currency)          // default
        assertNull(tx.profileId)                  // nullable default

        // Subscription: billingCycle + direction were added later — must
        // default, not crash.
        val sub = backup.database.subscriptions.single()
        assertEquals("Netflix", sub.merchantName)
        assertEquals("Monthly", sub.billingCycle)                 // default
        assertEquals(SubscriptionDirection.EXPENSE, sub.direction) // default enum
        assertEquals(SubscriptionState.ACTIVE, sub.state)          // default enum

        // Whole tables omitted by the old format default to empty, not null.
        assertTrue(backup.database.loans.isEmpty())
        assertTrue(backup.database.budgets.isEmpty())
        assertTrue(backup.database.profiles.isEmpty())
    }

    /**
     * Forward compatibility (#415). A backup from a *newer* app carries keys
     * this version has never heard of — both unknown object keys and a whole
     * unknown table. They must be ignored, not rejected.
     */
    @Test
    fun newerBackupWithUnknownKeys_isIgnored() {
        val json = """
        {
          "_format": "PennyWise Backup v9.9",
          "metadata": { "export_id": "future", "some_future_meta": 123 },
          "database": {
            "transactions": [
              {
                "id": 1,
                "amount": "10.00",
                "merchantName": "Future",
                "category": "X",
                "transactionType": "EXPENSE",
                "dateTime": "2030-01-01T00:00:00",
                "transactionHash": "f1",
                "a_field_from_the_future": "ignore me"
              }
            ],
            "a_table_that_does_not_exist_yet": [ { "whatever": true } ]
          }
        }
        """.trimIndent()

        val backup = backupJson.decodeFromString<PennyWiseBackup>(json)
        assertEquals(1, backup.database.transactions.size)
        assertEquals("Future", backup.database.transactions.single().merchantName)
    }

    /**
     * A backup that omits the entire `preferences` block (or a section of it)
     * still imports — each section defaults.
     */
    @Test
    fun missingPreferencesAndMetadata_useDefaults() {
        val json = """{ "database": { "transactions": [] } }"""
        val backup = backupJson.decodeFromString<PennyWiseBackup>(json)

        assertEquals(false, backup.preferences.developer.isDeveloperModeEnabled)
        assertEquals(6, backup.preferences.sms.smsScanMonths)
        assertFalse(backup.preferences.sms.smsScanUseCustomDate)
        assertNull(backup.preferences.sms.smsScanCustomDate)
        assertNull(backup.preferences.theme.isDarkThemeEnabled)
        assertEquals("", backup.metadata.exportId)
        assertTrue(backup.database.transactions.isEmpty())
    }

    /**
     * Present-but-null on a defaulted field is coerced to the default rather
     * than failing (`coerceInputValues = true`).
     */
    @Test
    fun explicitNullOnDefaultedField_isCoerced() {
        val json = """
        {
          "database": {
            "subscriptions": [
              {
                "id": 1,
                "merchantName": "X",
                "amount": "1.00",
                "nextPaymentDate": null,
                "billingCycle": null,
                "direction": null
              }
            ]
          }
        }
        """.trimIndent()

        val sub = backupJson.decodeFromString<PennyWiseBackup>(json)
            .database.subscriptions.single()
        assertEquals("Monthly", sub.billingCycle)
        assertEquals(SubscriptionDirection.EXPENSE, sub.direction)
    }

    @Test
    fun customSmsScanPreferences_roundTrip() {
        val customDateMillis = 1_705_276_800_000L
        val backup = PennyWiseBackup(
            metadata = BackupMetadata(
                exportId = "custom-sms-scan",
                appVersion = "test",
                databaseVersion = SCHEMA_VERSION,
                device = "Test",
                androidVersion = 30,
                statistics = BackupStatistics(
                    totalTransactions = 0,
                    totalCategories = 0,
                    totalCards = 0,
                    totalSubscriptions = 0,
                    dateRange = null
                )
            ),
            database = DatabaseSnapshot(),
            preferences = PreferencesSnapshot(
                theme = ThemePreferences(isDarkThemeEnabled = null, isDynamicColorEnabled = false),
                sms = SmsPreferences(
                    hasSkippedSmsPermission = false,
                    smsScanMonths = 3,
                    lastScanTimestamp = null,
                    lastScanPeriod = -2,
                    smsScanUseCustomDate = true,
                    smsScanCustomDate = customDateMillis,
                ),
                developer = DeveloperPreferences(isDeveloperModeEnabled = false, systemPrompt = null),
                app = AppPreferences(
                    hasShownScanTutorial = false,
                    firstLaunchTime = null,
                    hasShownReviewPrompt = false,
                    lastReviewPromptTime = null
                )
            )
        )

        val deserialized = backupJson.decodeFromString<PennyWiseBackup>(backupJson.encodeToString(backup))

        assertTrue(deserialized.preferences.sms.smsScanUseCustomDate)
        assertEquals(customDateMillis, deserialized.preferences.sms.smsScanCustomDate)
        assertEquals(-2, deserialized.preferences.sms.lastScanPeriod)
    }

    @Test
    fun oldBackupMissingCustomSmsScanFields_fallsBackToDefaults() {
        val json = """
        {
          "preferences": {
            "sms": {
              "sms_scan_months": 6,
              "last_scan_period": 6
            }
          }
        }
        """.trimIndent()

        val sms = backupJson.decodeFromString<PennyWiseBackup>(json).preferences.sms

        assertEquals(6, sms.smsScanMonths)
        assertFalse(sms.smsScanUseCustomDate)
        assertNull(sms.smsScanCustomDate)
    }
}