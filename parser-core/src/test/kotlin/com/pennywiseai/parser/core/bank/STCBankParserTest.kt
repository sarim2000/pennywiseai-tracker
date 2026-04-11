package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class STCBankParserTest {

    private val parser = STCBankParser()

    @TestFactory
    fun `stc bank parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Card purchase from screenshot",
                message = "**4561 Purchase\nVia:4561\nAmount: 3 SAR\nFrom: ABDULLAH SALEM MUEEN\nAt: 26/07/25 21:58",
                sender = "STC Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "ABDULLAH SALEM MUEEN",
                    accountLast4 = "4561",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Card purchase with decimal amount",
                message = "**9876 Purchase\nVia:9876\nAmount: 125.50 SAR\nFrom: LULU HYPERMARKET\nAt: 14/05/26 18:23",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("125.50"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "LULU HYPERMARKET",
                    accountLast4 = "9876",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "OTP message is ignored",
                message = "Your STC Bank verification code is 123456. Do not share it.",
                sender = "STC Bank",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves stc bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "STC Bank",
                sender = "STC Bank",
                currency = "SAR",
                message = "**4561 Purchase\nVia:4561\nAmount: 3 SAR\nFrom: ABDULLAH SALEM MUEEN\nAt: 26/07/25 21:58",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "STC Bank",
                sender = "STCBank",
                currency = "SAR",
                message = "**9876 Purchase\nVia:9876\nAmount: 125.50 SAR\nFrom: LULU HYPERMARKET\nAt: 14/05/26 18:23",
                expected = ExpectedTransaction(
                    amount = BigDecimal("125.50"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
