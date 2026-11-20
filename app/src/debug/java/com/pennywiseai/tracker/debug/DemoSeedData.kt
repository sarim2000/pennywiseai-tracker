package com.pennywiseai.tracker.debug

import androidx.room3.withWriteTransaction
import com.pennywiseai.shared.data.bootstrap.DefaultCategoryData
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.webhook.WebhookSyncReason
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.FieldModification
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.domain.service.RuleTemplateService
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

internal const val DEMO_CURRENCY = "JOD"
internal const val DEMO_PREFIX = "pennywise-debug-demo-"
private const val FOOD = "Food & Dining"
private const val GROCERIES = "Groceries"
private const val TRANSPORT = "Transportation"
private const val CAFE = "Demo Cafe"
private const val SUBCATEGORY = "Demo Coffee"

internal enum class DemoSeedStatus { CREATED, ALREADY_SEEDED, HAS_DATA }

internal suspend fun PennyWiseDatabase.seedDemoData(today: LocalDate, receipt: () -> String): DemoSeedStatus = withWriteTransaction {
    if (transactionDao().getTransactionByHash("${DEMO_PREFIX}salary") != null) {
        return@withWriteTransaction DemoSeedStatus.ALREADY_SEEDED
    }

    // A customization to Room's built-in categories or profiles is user data.
    val defaults = DefaultCategoryData.ALL
    val categories = categoryDao().getAllCategoriesList().associateBy { it.name }
    val defaultCategories = categories.size == defaults.size && defaults.withIndex().all { (index, seed) ->
        val category = categories[seed.name]
        category != null && category.isSystem && category.systemName == seed.name && category.color == seed.colorHex &&
            category.isIncome == seed.isIncome && category.displayOrder == index + 1 &&
            !category.isHidden && category.icon == null && category.parentId == null
    }
    val defaultProfiles = profileDao().getAllProfiles().toSet() == setOf(
        ProfileEntity(ProfileEntity.PERSONAL_ID, "Personal", "#4CAF50"),
        ProfileEntity(ProfileEntity.BUSINESS_ID, "Business", "#2196F3", sortOrder = 1)
    )
    if (!defaultCategories || !defaultProfiles || !hasOnlyDefaultRuleTemplates()) {
        return@withWriteTransaction DemoSeedStatus.HAS_DATA
    }

    val tables = usePrepared(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('android_metadata', 'room_master_table', 'room_table_modification_log', " +
            "'categories', 'profiles', 'transaction_rules')"
    ) { statement ->
        buildList { while (statement.step()) add(statement.getText(0)) }
    }
    for (table in tables) {
        val quotedTable = table.replace("\"", "\"\"")
        if (usePrepared("SELECT 1 FROM \"$quotedTable\" LIMIT 1") { it.step() }) {
            return@withWriteTransaction DemoSeedStatus.HAS_DATA
        }
    }

    DemoLedger(this@seedDemoData, today).populate(receipt)
    DemoSeedStatus.CREATED
}

private suspend fun PennyWiseDatabase.hasOnlyDefaultRuleTemplates(): Boolean {
    val rules = ruleDao().getAllRules().first()
    if (rules.isEmpty()) return true
    val engine = RuleEngine()
    // Template IDs and timestamps are generated at insertion. Match the stored configuration.
    return RuleTemplateService().getDefaultRuleTemplates().count { template ->
        val conditions = engine.serializeConditions(template.conditions)
        val actions = engine.serializeActions(template.actions)
        rules.singleOrNull { rule ->
            rule.isSystemTemplate && rule.isActive == template.isActive &&
                rule.name == template.name && rule.description == template.description &&
                rule.priority == template.priority && rule.conditions == conditions && rule.actions == actions
        } != null
    } == rules.size
}

private class DemoLedger(private val db: PennyWiseDatabase, private val today: LocalDate) {
    private val now = today.atStartOfDay()
    private val month = today.withDayOfMonth(1)
    private val lastMonth = month.minusMonths(1)
    private val personal = DemoAccount("Arab Bank", "1001", DEMO_CURRENCY)
    private val card = DemoAccount("Bank al Etihad", "2002", DEMO_CURRENCY, purchaseType = TransactionType.CREDIT)
    private val business = DemoAccount("Chase", "4004", "USD", ProfileEntity.BUSINESS_ID)
    private val euros = DemoAccount("Deutsche Bank", "5005", "EUR")

    suspend fun populate(receipt: () -> String) {
        val food = checkNotNull(db.categoryDao().getCategoryByName(FOOD))
        check(db.categoryDao().insertCategory(CategoryEntity(name = SUBCATEGORY, color = "#AD5725", parentId = food.id)) > 0)
        seedAccounts()
        seedPlans()
        val group = db.transactionGroupDao().insertGroup(TransactionGroupEntity(name = "Demo weekend", note = "Synthetic trip expenses"))
        val lent = loan("Demo Guest", LoanDirection.LENT, "8.000")
        val borrowed = loan("Demo Peer", LoanDirection.BORROWED, "100.000")
        val market = transaction("market", "Demo Market", "34.000", GROCERIES).copy(groupId = group)
        val marketId = insert(market)
        insert(transaction("transit", "Demo Tram", "6.500", TRANSPORT, date = recent(3)).copy(groupId = group))
        val cafe = insert(transaction("cafe", CAFE, "20.000", SUBCATEGORY))
        insert(transaction("arabic", "مقهى التجربة", "12.000", FOOD, date = recent(1)))
        insert(transaction("diner-usd", "Sample Diner", "18.00", FOOD, account = business))
        insert(transaction("diner-eur", "Example Bistro", "9.50", FOOD, account = euros))
        insert(transaction("prior-cafe", CAFE, "14.000", SUBCATEGORY, date = lastMonth.plusDays(2)))
        insert(transaction("prior-lunch", "Demo Lunch", "6.000", FOOD, date = lastMonth.plusDays(11)))
        insert(transaction("prior-diner", "Sample Diner", "10.00", FOOD, date = lastMonth.plusDays(18), account = business))
        insert(transaction("prior-bistro", "Example Bistro", "6.00", FOOD, date = lastMonth.plusDays(23), account = euros))
        insert(transaction("salary", "Demo Payroll", "1200.000", "Salary", type = TransactionType.INCOME))
        insert(transaction("credit", "Demo Books", "13.000", "Education", date = recent(5), account = card))
        insert(transaction("investment", "Demo Index Fund", "75.000", "Investments", date = recent(6), type = TransactionType.INVESTMENT))
        insert(transaction("unsorted", "Demo Unsorted", "3.000", "Others", date = recent(4)))
        insert(transaction("excluded", "Demo Reimbursable", "99.000", FOOD).copy(excludedFromAnalytics = true))
        val deleted = insert(transaction("deleted", "Demo Removed", "88.000", GROCERIES).copy(isDeleted = true))
        insert(transaction("refund", "Demo Market refund", "4.000", GROCERIES, type = TransactionType.INCOME).copy(
            budgetCategory = GROCERIES, budgetImpactType = BudgetImpactType.DEDUCT_SPENT
        ))
        val split = insert(transaction("split", "Demo Variety Store", "23.000", "Others", date = recent(6)))
        db.transactionSplitDao().insertSplits(listOf(
            TransactionSplitEntity(transactionId = split, category = "Shopping", amount = BigDecimal("18.000")),
            TransactionSplitEntity(transactionId = split, category = TRANSPORT, amount = BigDecimal("5.000"))
        ))
        val shared = insert(transaction("shared", "Demo Festival", "20.000", "Entertainment", date = recent(2)).copy(lentSharesTotal = BigDecimal("8.000")))
        db.loanDao().insertShare(LoanShareEntity(transactionId = shared, loanId = lent, amount = BigDecimal("8.000")))
        insert(transaction("borrowed", "Demo Peer", "110.000", "Others", type = TransactionType.INCOME).copy(
            loanId = borrowed, loanContribution = BigDecimal("100.000")
        ))
        val savings = personal.copy(last4 = "1002")
        val transfer = transaction("transfer-out", "Demo savings transfer", "60.000", "Banking", type = TransactionType.TRANSFER).copy(
            fromAccount = personal.last4, fromBankName = personal.bank,
            toAccount = savings.last4, toBankName = savings.bank, transferPairId = "${DEMO_PREFIX}transfer"
        )
        insert(transfer)
        insert(transfer.copy(transactionHash = "${DEMO_PREFIX}transfer-in", accountNumber = savings.last4))
        seedHistory()
        db.transactionDao().insertTransactions(demoChartTransactions(today, listOf(personal, card, business, euros)))
        seedGoals(marketId, deleted)
        seedActivity(marketId, cafe)
        db.transactionDao().updateTransaction(market.copy(id = marketId, receiptPath = receipt()))
    }

    private suspend fun seedAccounts() {
        val accounts = db.accountBalanceDao()
        val checking = balance(personal, "842.250", "CURRENT", "Arab Bank · Everyday")
        for (monthsAgo in 1L..11L) {
            val timestamp = month.minusMonths(monthsAgo).atStartOfDay()
            val historicBalance = BigDecimal.valueOf(830 - monthsAgo * 23 + (monthsAgo % 3) * 12)
            accounts.insertBalance(checking.copy(balance = historicBalance, timestamp = timestamp))
        }
        accounts.insertBalance(checking)
        accounts.insertBalance(balance(personal.copy(last4 = "1002"), "560.000", "SAVINGS", "Arab Bank · Savings"))
        accounts.insertBalance(balance(card, "150.000", "CREDIT", "Bank al Etihad · Credit").copy(
            isCreditCard = true, creditLimit = BigDecimal("1000.000"), statementDay = 1,
            cardDueDay = today.plusDays(3).dayOfMonth.coerceAtMost(28),
            cardBillAmount = BigDecimal("90.000"), cardBillAsOf = now
        ))
        accounts.insertBalance(balance(business, "250.00", "SAVINGS", "Chase · Business"))
        accounts.insertBalance(balance(euros, "175.00", "SAVINGS", "Deutsche Bank · Travel"))
        accounts.insertBalance(balance(personal.copy(last4 = "3003"), "5000.000", AccountBalanceEntity.LOAN_TYPE, "Arab Bank · Loan"))
        db.cardDao().insertCard(CardEntity(cardLast4 = personal.last4, cardType = CardType.DEBIT, bankName = personal.bank,
            accountLast4 = personal.last4, nickname = "Demo Debit", currency = personal.currency))
        db.cardDao().insertCard(CardEntity(cardLast4 = card.last4, cardType = CardType.CREDIT, bankName = card.bank,
            nickname = "Demo Credit", currency = card.currency))
        val expiry = now.plusYears(1)
        val zone = ZoneId.systemDefault()
        val usdRate = ExchangeRateEntity(
            fromCurrency = business.currency, toCurrency = DEMO_CURRENCY, rate = BigDecimal("0.71"),
            provider = "Debug demo", updatedAt = now, expiresAt = expiry, updatedAtUnix = now.atZone(zone).toEpochSecond(),
            expiresAtUnix = expiry.atZone(zone).toEpochSecond(), isCustomRate = true
        )
        db.exchangeRateDao().insertExchangeRates(listOf(
            usdRate, usdRate.copy(fromCurrency = euros.currency, rate = BigDecimal("0.80"))
        ))
    }

    private suspend fun seedPlans() {
        val budget = db.budgetDao()
        val dining = BudgetEntity(
            name = "Demo dining", limitAmount = BigDecimal("25.000"), periodType = BudgetPeriodType.MONTHLY,
            startDate = month, endDate = month.plusMonths(1).minusDays(1), currency = DEMO_CURRENCY
        )
        val diningId = budget.insertBudget(dining)
        val groceryId = budget.insertBudget(dining.copy(name = "Demo groceries", limitAmount = BigDecimal("90.000"), color = "#26765D"))
        budget.insertBudgetCategories(listOf(BudgetCategoryEntity(budgetId = diningId, categoryName = FOOD),
            BudgetCategoryEntity(budgetId = groceryId, categoryName = GROCERIES)))
        val snapshots = db.budgetSnapshotDao()
        snapshots.insertGroupSnapshots(listOf(BudgetMonthSnapshotEntity(
            budgetId = diningId, year = lastMonth.year, month = lastMonth.monthValue,
            budgetName = dining.name, limitAmount = dining.limitAmount
        )))
        snapshots.insertCategorySnapshots(listOf(BudgetCategoryMonthSnapshotEntity(
            budgetId = diningId, year = lastMonth.year, month = lastMonth.monthValue, categoryName = FOOD, budgetAmount = BigDecimal.ZERO
        )))
        val subscriptions = db.subscriptionDao()
        val streaming = SubscriptionEntity(
            merchantName = "Demo Streaming", amount = BigDecimal("8.000"), nextPaymentDate = today.plusDays(2),
            currency = DEMO_CURRENCY, category = "Entertainment", bankName = personal.bank, accountLast4 = personal.last4
        )
        subscriptions.insertSubscription(streaming)
        subscriptions.insertSubscription(streaming.copy(merchantName = "Demo Internet", amount = BigDecimal("22.000"),
            nextPaymentDate = today.minusDays(1), category = "Bills & Utilities"))
        subscriptions.insertSubscription(streaming.copy(merchantName = "Demo Installments", amount = BigDecimal("35.000"),
            nextPaymentDate = today.plusDays(15), totalPayments = 6, paymentsMade = 2))
        subscriptions.insertSubscription(streaming.copy(merchantName = "Demo Ended", nextPaymentDate = null, state = SubscriptionState.ENDED))
        db.recurringTransactionDao().insert(RecurringTransactionEntity(
            uid = "${DEMO_PREFIX}template", merchantName = "Demo Rent", amount = BigDecimal("320.000"), currency = DEMO_CURRENCY,
            category = "Bills & Utilities", nextDueDate = today.plusDays(14), dayOfMonth = today.plusDays(14).dayOfMonth,
            bankName = personal.bank, accountLast4 = personal.last4
        ))
    }

    private suspend fun seedHistory() {
        // Stay within each month, even if the seed is run on the first or the 31st.
        for (monthsAgo in 1L..11L) {
            val past = month.minusMonths(monthsAgo)
            insert(transaction("salary-$monthsAgo", "Demo Payroll", "1200.000", "Salary",
                date = past.plusDays(24), type = TransactionType.INCOME))
            insert(transaction("market-$monthsAgo", "Demo Market", "${31 + monthsAgo}.000", GROCERIES, date = past.plusDays(3)))
            insert(transaction("rent-$monthsAgo", "Demo Rent", "320.000", "Bills & Utilities", date = past.plusDays(8)).copy(isRecurring = true))
            insert(transaction("tram-$monthsAgo", "Demo Tram", "${6 + monthsAgo}.500", TRANSPORT, date = past.plusDays(16)))
        }
    }

    private suspend fun seedGoals(marketId: Long, deletedId: Long) {
        val goals = db.goalDao()
        val target = GoalEntity(uid = "${DEMO_PREFIX}goal", name = "Demo travel fund", targetAmount = BigDecimal("500.000"),
            currency = DEMO_CURRENCY, alreadySaved = BigDecimal("120.000"), dueDate = today.plusMonths(4))
        val goalId = goals.insertGoal(target)
        goals.insertGoal(target.copy(uid = "${DEMO_PREFIX}archived-goal", name = "Demo completed trip", alreadySaved = target.targetAmount, isArchived = true))
        goals.insertContribution(GoalContributionEntity(uid = "${DEMO_PREFIX}goal-deposit", goalId = goalId,
            amount = BigDecimal("14.000"), date = today, transactionId = marketId, note = "Demo linked contribution"))
        goals.insertContribution(GoalContributionEntity(uid = "${DEMO_PREFIX}goal-withdrawal", goalId = goalId,
            amount = BigDecimal("-5.000"), date = today.minusDays(2), note = "Demo withdrawal"))
        goals.insertContribution(GoalContributionEntity(uid = "${DEMO_PREFIX}goal-deleted", goalId = goalId,
            amount = BigDecimal("6.000"), date = today, transactionId = deletedId))
    }

    private suspend fun seedActivity(marketId: Long, cafeId: Long) {
        val tag = db.tagDao().insertTag(TagEntity(name = "Demo weekend"))
        db.tagDao().insertCrossRef(TransactionTagCrossRef(transactionId = marketId, tagId = tag))
        db.merchantMappingDao().insertMapping(MerchantMappingEntity(merchantName = CAFE, category = SUBCATEGORY))
        db.merchantAliasDao().insertOrUpdateAlias(MerchantAliasEntity(merchantName = "Demo Market", alias = "Demo Grocer"))
        val engine = RuleEngine()
        val rule = RuleEntity(
            id = "${DEMO_PREFIX}rule", name = "Demo coffee rule", description = "File demo cafe purchases under Demo Coffee",
            priority = 100, conditions = engine.serializeConditions(listOf(RuleCondition(TransactionField.MERCHANT, ConditionOperator.EQUALS, CAFE))),
            actions = engine.serializeActions(listOf(RuleAction(TransactionField.CATEGORY, ActionType.SET, SUBCATEGORY))),
            isActive = true, createdAt = now, updatedAt = now
        )
        db.ruleDao().insertRule(rule)
        db.ruleApplicationDao().insertApplication(RuleApplicationEntity(
            id = "${DEMO_PREFIX}application", ruleId = rule.id, ruleName = rule.name, transactionId = cafeId.toString(),
            fieldsModified = engine.serializeFieldModifications(listOf(FieldModification(TransactionField.CATEGORY, FOOD, SUBCATEGORY, ActionType.SET))),
            appliedAt = now
        ))
        val message = "TEST ONLY: ${personal.bank}, JOD 34.000 debited from sample account XX1001 at Demo Market."
        db.bankNotificationDao().insert(BankNotificationEntity(
            packageName = "example.pennywise.demo", senderAlias = personal.bank, messageBody = message,
            messageHash = "${DEMO_PREFIX}notification", postedAt = now, processed = true, transactionId = marketId
        ))
        db.unrecognizedSmsDao().insert(UnrecognizedSmsEntity(sender = "DEMO-BANK-T",
            smsBody = "Sample alert: posted a JOD 11.250 payment to Demo Shop.", receivedAt = now))
        val chatTime = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        db.chatDao().insertMessages(listOf(
            ChatMessage(id = "${DEMO_PREFIX}question", message = "What can I ask about this demo ledger?", isUser = true, timestamp = chatTime),
            ChatMessage(id = "${DEMO_PREFIX}answer", message = "Try asking how much you spent on dining last month or which bills are due.", isUser = false, timestamp = chatTime + 1)
        ))
        val webhook = WebhookProfileEntity(id = "${DEMO_PREFIX}webhook", name = "Demo webhook", url = "https://pennywise-demo.invalid/transactions",
            enabled = false, currency = DEMO_CURRENCY, lastSyncedAt = now)
        db.webhookProfileDao().upsert(webhook)
        db.webhookLogDao().insert(WebhookLogEntity(profileId = webhook.id, profileName = webhook.name,
            syncReason = WebhookSyncReason.TEST, status = WebhookLogStatus.SUCCESS, message = "Synthetic delivery history", httpStatus = 200, batchCount = 1))
        db.webhookCursorDao().upsert(WebhookCursorEntity(profileId = webhook.id, dataType = WebhookDataType.TRANSACTIONS, lastSuccessAt = now, lastRangeEnd = now))
        db.webhookDeliveredTransactionDao().insert(WebhookDeliveredTransactionEntity(profileId = webhook.id, transactionId = marketId))
    }

    private suspend fun loan(name: String, direction: LoanDirection, amount: String): Long = db.loanDao().insertLoan(
        LoanEntity(personName = name, direction = direction, originalAmount = BigDecimal(amount),
            remainingAmount = BigDecimal(amount), currency = DEMO_CURRENCY)
    )

    private suspend fun insert(row: TransactionEntity): Long = db.transactionDao().insertTransaction(row).also { check(it > 0) }

    private fun recent(daysAgo: Long) = today.minusDays(daysAgo).coerceAtLeast(month)

    private fun transaction(
        key: String, merchant: String, amount: String, category: String, date: LocalDate = today,
        account: DemoAccount = personal, type: TransactionType = account.purchaseType
    ) = TransactionEntity(
        amount = BigDecimal(amount), merchantName = merchant, category = category, transactionType = type,
        dateTime = date.atStartOfDay(), bankName = account.bank, accountNumber = account.last4, currency = account.currency,
        transactionHash = "$DEMO_PREFIX$key", profileId = account.profileId
    )

    private fun balance(account: DemoAccount, amount: String, type: String, alias: String) = AccountBalanceEntity(
        bankName = account.bank, accountLast4 = account.last4, currency = account.currency, profileId = account.profileId,
        balance = BigDecimal(amount), timestamp = now, accountType = type, sourceType = "MANUAL", alias = alias
    )
}
