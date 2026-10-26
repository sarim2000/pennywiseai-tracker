package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/** Made-up SMS from banks with no parser (#994). */
class GenericTransactionExtractorTest {

    // (case, expected confidence) — confidence null for cases that must not extract.
    private val cases = listOf(
        ParserTestCase(
            name = "INR debit with account, merchant and balance",
            sender = "VM-ZQTBNK-S",
            message = "Rs.500.00 debited from your A/c XXX1234 to TEST STORE on 25-10-26. Avl Bal Rs.12,000.50",
            expected = ExpectedTransaction(
                amount = BigDecimal("500.00"), currency = "INR", type = TransactionType.EXPENSE,
                merchant = "TEST STORE", accountLast4 = "1234", balance = BigDecimal("12000.50")
            )
        ) to 100,
        ParserTestCase(
            name = "USD card purchase with available limit",
            sender = "ZQTCARD",
            message = "Purchase of USD 12.50 at TEST CAFE on card ending 4321. Available limit USD 1,987.50",
            expected = ExpectedTransaction(
                amount = BigDecimal("12.50"), currency = "USD", type = TransactionType.EXPENSE,
                merchant = "TEST CAFE", accountLast4 = "4321", creditLimit = BigDecimal("1987.50")
            )
        ) to 100,
        ParserTestCase(
            name = "Dollar symbol spend",
            sender = "ZQTCARD",
            message = "You spent \$45.99 at TEST MART with card **9876.",
            expected = ExpectedTransaction(
                amount = BigDecimal("45.99"), currency = "USD", type = TransactionType.EXPENSE,
                merchant = "TEST MART", accountLast4 = "9876"
            )
        ) to 90,
        ParserTestCase(
            name = "AED credit names the payer, not the account",
            sender = "ZQTMONEY",
            message = "AED 2,500.00 has been credited to your account XXXX5555 from TEST EMPLOYER on 25/10/2026.",
            expected = ExpectedTransaction(
                amount = BigDecimal("2500.00"), currency = "AED", type = TransactionType.INCOME,
                merchant = "TEST EMPLOYER", accountLast4 = "5555"
            )
        ) to 90,
        ParserTestCase(
            name = "Currency code after the amount",
            sender = "ZQTMONEY",
            message = "Your card ending 1111 was charged 150.75 EUR at TEST SHOP.",
            expected = ExpectedTransaction(
                amount = BigDecimal("150.75"), currency = "EUR", type = TransactionType.EXPENSE,
                merchant = "TEST SHOP", accountLast4 = "1111"
            )
        ) to 90,
        ParserTestCase(
            name = "Pound credit with a payer only",
            sender = "ZQTMONEY",
            message = "£20.00 received from TEST FRIEND via transfer.",
            expected = ExpectedTransaction(
                amount = BigDecimal("20.00"), currency = "GBP", type = TransactionType.INCOME,
                merchant = "TEST FRIEND", expectNullAccountLast4 = true
            )
        ) to 70,
        ParserTestCase(
            name = "Bare debit is below the suggestion bar",
            sender = "ZQTMONEY",
            message = "INR 300 debited.",
            expected = ExpectedTransaction(
                amount = BigDecimal("300"), currency = "INR", type = TransactionType.EXPENSE,
                expectNullMerchant = true, expectNullAccountLast4 = true
            )
        ) to 50,
        ParserTestCase(
            name = "Balance-only message",
            sender = "ZQTMONEY",
            message = "Your account XX1234 balance is AED 2,500.00 as of 25-Oct.",
            shouldParse = false
        ) to null,
        ParserTestCase(
            name = "Declined purchase",
            sender = "ZQTCARD",
            message = "Purchase declined: USD 12.50 at TEST CAFE with card **9876.",
            shouldParse = false
        ) to null,
        ParserTestCase(
            name = "Failed debit",
            sender = "ZQTMONEY",
            message = "Transaction of INR 750.00 from A/c XX1234 to TEST STORE has failed.",
            shouldParse = false
        ) to null,
        ParserTestCase(
            name = "OTP",
            sender = "ZQTMONEY",
            message = "Your OTP for the payment of INR 999 at TEST STORE is 482913. Do not share it.",
            shouldParse = false
        ) to null,
        ParserTestCase(
            name = "Promo",
            sender = "ZQTMONEY",
            message = "Spend INR 2,000 at TEST MALL and get 10% back! Offer valid till Sunday.",
            shouldParse = false
        ) to null,
        ParserTestCase(
            name = "Due reminder",
            sender = "ZQTCARD",
            message = "Your card bill of USD 450.00 is due on 30-Oct.",
            shouldParse = false
        ) to null,
        ParserTestCase(
            name = "No amount",
            sender = "ZQTMONEY",
            message = "Your account was debited. Contact us for details.",
            shouldParse = false
        ) to null,
        ParserTestCase(
            name = "Phone-number sender is a person",
            sender = "+15550001111",
            message = "Sent USD 10.00 to TEST CAFE.",
            shouldParse = false
        ) to null
    )

    @TestFactory
    fun `generic extractor reads unsupported bank SMS`(): List<DynamicTest> = cases.map { (case, confidence) ->
        dynamicTest(case.name) {
            val result = GenericTransactionExtractor.extract(case.sender, case.message)
            if (!case.shouldParse) {
                assertNull(result, "Should not extract: $result")
                return@dynamicTest
            }
            assertNotNull(result, "Expected a suggestion from: ${case.message}")
            ParserTestUtils.validateResultDynamic(result!!.transaction, case.expected!!)
            assertEquals(confidence, result.confidence, "Confidence mismatch")
        }
    }

    // The test senders must stay unclaimed, or a bank parser would handle these SMS instead.
    @TestFactory
    fun `test senders belong to no bank parser`(): List<DynamicTest> =
        cases.map { it.first.sender }.distinct().map { sender ->
            dynamicTest(sender) { assertNull(BankParserFactory.getParser(sender), "$sender is claimed by a parser") }
        }
}
