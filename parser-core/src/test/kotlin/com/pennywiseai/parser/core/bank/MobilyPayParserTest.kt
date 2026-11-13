package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

// Fixtures are synthetic: the real Mobily Pay SMS layout is not yet confirmed by a reporter.
class MobilyPayParserTest {
    @TestFactory
    fun `mobily pay parser handles common cases`(): List<DynamicTest> = ParserTestUtils.runTestSuite(
        parser = MobilyPayParser(),
        testCases = listOf(
            ParserTestCase(
                name = "Card purchase",
                message = "Card Purchase\nAmount: SAR 12.50\nCard Number: MADA *1234\nAt: Example Store",
                sender = "MOBILYPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.50"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "Example Store",
                    accountLast4 = "1234",
                    isFromCard = true,
                    isMobileWallet = false
                )
            ),
            ParserTestCase(
                name = "Top-up balance belongs to wallet",
                message = "Wallet Top-up\nAmount: SAR 25.00\nFrom Card: 1234*MADA\nCurrent Balance: SAR 50.00",
                sender = "MOBILYPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "SAR",
                    type = TransactionType.TRANSFER,
                    balance = BigDecimal("50.00"),
                    isFromCard = false,
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "Incoming local transfer",
                message = "Local Transfer\nAmount: SAR 100.00\nFrom: Example Sender\nCurrent Balance: SAR 150.00",
                sender = "MOBILYPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "Example Sender",
                    balance = BigDecimal("150.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "Outgoing local transfer",
                message = "Local Transfer\nAmount: SAR 40.00\nTo: Example Recipient\nCurrent Balance: SAR 110.00",
                sender = "MOBILYPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("40.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "Example Recipient",
                    balance = BigDecimal("110.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "Ambiguous transfer without direction is not parsed",
                message = "Local Transfer\nAmount: SAR 40.00\nCurrent Balance: SAR 110.00",
                sender = "MOBILYPAY",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Cashback reversal takes money back",
                message = "Cashback Reversal\nAmount: SAR 5.00\nCurrent Balance: SAR 105.00",
                sender = "MOBILYPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("105.00"),
                    isMobileWallet = true
                )
            ),
            ParserTestCase(
                name = "Purchase reversal is a refund",
                message = "Purchase Reversal\nAmount: SAR 12.50\nAt: Example Store\nCurrent Balance: SAR 117.50",
                sender = "MOBILYPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.50"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "Example Store",
                    balance = BigDecimal("117.50"),
                    isMobileWallet = true
                )
            ),
            ParserTestCase(
                name = "Reject verification code",
                message = "OTP: 000000 for Card Purchase\nAmount: SAR 12.50",
                sender = "MOBILYPAY",
                shouldParse = false
            )
        ),
        handleCases = listOf(
            "MOBILYPAY" to true,
            "Mobily Pay" to true,
            "MOBILY" to false,
            "UNKNOWN" to false
        ),
        suiteName = "Mobily Pay Parser"
    )
}
