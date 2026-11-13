package com.pennywiseai.tracker.data.manager

import android.content.Context
import android.content.SharedPreferences
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.widget.WidgetRefresher
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Made-up messages: the processor raises the same money alerts through
 * [MoneyAlerts] as it did when it called the worker directly. A saved live
 * transaction queues the check; a reported balance that falls to the
 * threshold posts the low-balance alert.
 */
class SmsTransactionProcessorMoneyAlertsTest {
    private val bank = "Zqt Bank"
    private val account = "4321"
    private val threshold = BigDecimal("500")

    private val prefs = mockk<SharedPreferences> {
        every { getStringSet(any(), any()) } returns emptySet()
    }
    private val context = mockk<Context>(relaxed = true) {
        every { getSharedPreferences(any(), any()) } returns prefs
    }
    private val transactions = mockk<TransactionRepository>()
    private val balances = mockk<AccountBalanceRepository>()
    private val cards = mockk<CardRepository>()
    private val mappings = mockk<MerchantMappingRepository>()
    private val subscriptions = mockk<SubscriptionRepository>()
    private val rules = mockk<RuleRepository>()
    private val ignored = mockk<IgnoredAccountsStore>()
    private val alerts = mockk<MoneyAlerts>()

    private val processor = SmsTransactionProcessor(
        appContext = context,
        transactionRepository = transactions,
        accountBalanceRepository = balances,
        cardRepository = cards,
        merchantMappingRepository = mappings,
        subscriptionRepository = subscriptions,
        ruleRepository = rules,
        ruleEngine = RuleEngine(),
        tagRepository = mockk<TagRepository>(),
        ignoredAccountsStore = ignored,
        database = mockk<PennyWiseDatabase>(),
        moneyAlerts = alerts,
    )

    @Before
    fun setUp() {
        mockkObject(WidgetRefresher)
        justRun { WidgetRefresher.refreshTransactionWidgets(any()) }
        justRun { alerts.onTransactionSaved() }
        justRun { alerts.showLowBalance(any()) }
        every { ignored.isIgnored(any(), *anyVararg()) } returns false
        coEvery { transactions.getTransactionByHash(any()) } returns null
        coEvery { transactions.getAnyBySms(any(), any()) } returns null
        coEvery { transactions.insertTransaction(any()) } returns 42L
        coEvery { cards.getCard(any(), any()) } returns null
        coEvery { mappings.getCategoryForMerchant(any()) } returns null
        coEvery { rules.getActiveRulesByType(any()) } returns emptyList()
        coEvery { subscriptions.matchTransactionToSubscription(any()) } returns null
        coEvery { subscriptions.priceChangedSubscriptionFor(any()) } returns null
    }

    @After
    fun tearDown() = unmockkObject(WidgetRefresher)

    @Test
    fun `a saved transaction queues the money-alert check once`() = runBlocking {
        val result = processor.saveParsedTransaction(parsed(balance = null, accountLast4 = null), BODY)

        assertTrue(result.reason, result.success)
        verify(exactly = 1) { alerts.onTransactionSaved() }
        verify(exactly = 0) { alerts.showLowBalance(any()) }
    }

    @Test
    fun `a duplicate queues nothing`() = runBlocking {
        coEvery { transactions.getTransactionByHash(any()) } returns mockk { every { isDeleted } returns false }

        val result = processor.saveParsedTransaction(parsed(balance = BigDecimal("100")), BODY)

        assertFalse(result.success)
        verify(exactly = 0) { alerts.onTransactionSaved() }
        verify(exactly = 0) { alerts.showLowBalance(any()) }
    }

    @Test
    fun `a reported balance that falls to the threshold posts the low-balance alert`() = runBlocking {
        val saved = stubBalanceWrite(before = BigDecimal("900"))

        val result = processor.saveParsedTransaction(parsed(balance = BigDecimal("400")), BODY)

        assertTrue(result.reason, result.success)
        verify(exactly = 1) { alerts.onTransactionSaved() }
        verify(exactly = 1) { alerts.showLowBalance(saved.captured) }
        assertEquals(BigDecimal("400"), saved.captured.balance)
    }

    @Test
    fun `a balance already under the threshold doesn't alert again`() = runBlocking {
        stubBalanceWrite(before = BigDecimal("450"))

        processor.saveParsedTransaction(parsed(balance = BigDecimal("400")), BODY)

        verify(exactly = 0) { alerts.showLowBalance(any()) }
    }

    @Test
    fun `a derived balance never alerts, even across the threshold`() = runBlocking {
        val saved = stubBalanceWrite(before = BigDecimal("525"))

        val result = processor.saveParsedTransaction(parsed(balance = null), BODY)

        assertTrue(result.reason, result.success)
        // 525 - 50 crosses 500, but no SMS reported that figure.
        assertEquals(BigDecimal("475"), saved.captured.balance)
        verify(exactly = 0) { alerts.showLowBalance(any()) }
    }

    @Test
    fun `a failing low-balance alert doesn't fail the save`() = runBlocking {
        stubBalanceWrite(before = BigDecimal("900"))
        every { alerts.showLowBalance(any()) } throws IllegalStateException("no channel")

        val result = processor.saveParsedTransaction(parsed(balance = BigDecimal("400")), BODY)

        assertTrue(result.reason, result.success)
        assertEquals(42L, result.transactionId)
        verify(exactly = 1) { alerts.onTransactionSaved() }
    }

    /** The account sits at [before] with a threshold; captures the row the processor saves. */
    private fun stubBalanceWrite(before: BigDecimal): io.mockk.CapturingSlot<AccountBalanceEntity> {
        val existing = AccountBalanceEntity(
            bankName = bank,
            accountLast4 = account,
            balance = before,
            timestamp = LocalDateTime.of(2026, 1, 1, 9, 0),
            currency = "INR",
            lowBalanceThreshold = threshold,
        )
        coEvery { balances.getLatestBalance(bank, account) } returns existing
        val saved = slot<AccountBalanceEntity>()
        coEvery {
            balances.insertSmsTransactionBalance(bank, account, any(), any(), any())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val build = args[4] as (BigDecimal?) -> AccountBalanceEntity
            build(before).also { saved.captured = it }
        }
        return saved
    }

    private fun parsed(balance: BigDecimal?, accountLast4: String? = account) = ParsedTransaction(
        amount = BigDecimal("50"),
        type = TransactionType.EXPENSE,
        merchant = "Juice Bar",
        reference = null,
        accountLast4 = accountLast4,
        balance = balance,
        smsBody = BODY,
        sender = "AD-ZQTBNK",
        timestamp = 1_780_000_000_000L,
        bankName = bank,
        currency = "INR",
    )

    private companion object {
        const val BODY = "Paid INR 50.00 at Juice Bar"
    }
}
