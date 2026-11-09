package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.parser.core.MessageTemplate
import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.TeachSelection
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BaseIndianBankParser
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SmsScanClassifierTest {

    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)
    private val savedBalanceCurrencies = mutableListOf<String>()

    private fun classifier(
        templates: Map<String, List<MessageTemplate>> = emptyMap(),
        recentSince: Long = 0L,
    ) = SmsScanClassifier(
        recentSinceMillis = recentSince,
        taughtTemplates = lazyOf(templates),
        subscriptionRepository = subscriptions,
        saveBalanceNotification = { _: BaseIndianBankParser.BaseBalanceUpdateInfo, currency: String, _: ScannedSms ->
            savedBalanceCurrencies += currency
        },
    )

    @Test
    fun `promotional and government senders are dropped`() {
        val body = "Rs.500.00 debited from A/c XX1234"

        assertTrue(classifier().classify(scannedSms("VM-HDFCBK-P", body)) is ParseResult.Discard)
        assertTrue(classifier().classify(scannedSms("VM-HDFCBK-G", body)) is ParseResult.Discard)
    }

    @Test
    fun `an unknown DLT sender is kept for review`() {
        val result = classifier().classify(scannedSms("AD-NEWBNK-T", "Your statement is ready"))

        assertTrue(result is ParseResult.StoreUnrecognized)
    }

    @Test
    fun `a person's message is dropped even with an amount`() {
        val result = classifier().classify(scannedSms("+10000000000", "I paid 12.50 EUR for lunch"))

        assertTrue(result is ParseResult.Discard)
    }

    @Test
    fun `a known bank's debit is read as a transaction in its currency`() {
        val result = classifier().classify(
            scannedSms("VM-HDFCBK-S", "Rs.500.00 debited from A/c XX1234 on 20-Oct-25 to merchant@upi (UPI Ref No 123456789012)")
        )

        val parsed = (result as ParseResult.Transaction).parsed
        assertEquals(0, BigDecimal("500.00").compareTo(parsed.amount))
        assertEquals("INR", parsed.currency)
    }

    @Test
    fun `a known bank's non-transaction SMS is dropped, not filed`() {
        val result = classifier().classify(scannedSms("VM-HDFCBK-S", "123456 is your OTP for login. Do not share it with anyone."))

        assertTrue(result is ParseResult.Discard)
    }

    @Test
    fun `a taught sender's message is kept when the parser passes on it`() {
        val sender = "VM-HDFCBK-S"
        val body = "Juice Bar bill of SAR 75.00 settled"
        val template = MessageTemplates.build(
            id = "t",
            senderKey = LearnedSendersStore.normalize(sender),
            body = "Juice Bar bill of SAR 50.00 settled",
            selection = TeachSelection(amount = 22..26),
            type = TransactionType.EXPENSE,
            currency = "SAR",
            bankName = "Zqt Bank",
        )!!
        val taught = classifier(templates = mapOf(template.senderKey to listOf(template)))
        val sms = scannedSms(sender, body)

        assertTrue(classifier().classify(sms) is ParseResult.Discard)
        assertTrue(taught.classify(sms) is ParseResult.StoreUnrecognized)
        assertTrue(taught.matchesTaughtTemplate(sms))
        assertFalse(taught.matchesTaughtTemplate(scannedSms(sender, "Your parcel has arrived")))
    }

    @Test
    fun `a balance-only SMS saves a balance in the account's currency`() {
        val result = classifier().classify(
            scannedSms(
                "AD-INDUSIND-S",
                "Your A/C 2134***12345 has Avl BAL of INR 1,234.56 as on 05/10/25 04:10 AM. Download IndusMobile from PlayStore - IndusInd Bank",
            )
        )

        assertTrue(result is ParseResult.SpecialNotification)
        runBlocking { (result as ParseResult.SpecialNotification).onSave() }
        assertEquals(listOf("INR"), savedBalanceCurrencies)
    }

    @Test
    fun `a mandate SMS becomes a subscription only while it is recent`() {
        val sms = scannedSms(
            "AX-PNBSMS-S",
            "Your UPI-Mandate is successfully created towards Test Service for Rs.99.00 from A/c No.XXXXXX1234. UMN:abc123@ybl-PNB",
            timestamp = 1_000L,
        )

        assertTrue(classifier(recentSince = 500L).classify(sms) is ParseResult.SpecialNotification)
        assertTrue(classifier(recentSince = 2_000L).classify(sms) is ParseResult.Discard)
    }
}
