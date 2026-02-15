package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class ParsianBankParserTest {

    private val parser = ParsianBankParser()

    @TestFactory
    fun `parsian bank parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Parsian Bank deposit transaction",
                message = "مبلغ 2,000,000 تومان واریز به حساب شما انجام شد. مانده: 3,000,000 تومان",
                sender = "PARSIANBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000000"),
                    currency = "IRR",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("3000000")
                )
            ),
            ParserTestCase(
                name = "Parsian Bank withdrawal transaction",
                message = "مبلغ 500,000 تومان از حساب شما برداشت شد. مانده: 2,500,000 تومان",
                sender = "PARSIAN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("2500000")
                )
            ),
            ParserTestCase(
                name = "Parsian Bank purchase transaction",
                message = "مبلغ 150,000 تومان خرید با کارت 1234-5678-9012-3456 انجام شد. مانده: 2,350,000 تومان",
                sender = "PERSIANBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("2350000"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Parsian Bank transfer transaction",
                message = "مبلغ 800,000 تومان انتقال یافت. مانده: 1,550,000 تومان",
                sender = "PARSIAN BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("800000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("1550000")
                )
            )
        )

        val handleCases = listOf(
            Pair("PARSIANBANK", true),
            Pair("PARSIAN", true),
            Pair("PERSIANBANK", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases)
    }

    @TestFactory
    fun `factory resolves parsian bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Parsian Bank",
                sender = "PARSIANBANK",
                currency = "IRR",
                message = "مبلغ 2,000,000 تومان واریز به حساب شما انجام شد. مانده: 3,000,000 تومان",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000000"),
                    currency = "IRR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Parsian Bank",
                sender = "PARSIAN",
                currency = "IRR",
                message = "مبلغ 500,000 تومان از حساب شما برداشت شد. مانده: 2,500,000 تومان",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Parsian Bank factory tests")
    }
}
