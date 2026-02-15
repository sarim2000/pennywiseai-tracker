package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class MelliBankParserTest {

    private val parser = MelliBankParser()

    @TestFactory
    fun `melli bank parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Melli Bank deposit transaction",
                message = "مبلغ 1,500,000 ریال واریز به حساب شما انجام شد. مانده: 2,500,000 ریال",
                sender = "+98700717",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500000"),
                    currency = "IRR",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("2500000")
                )
            ),
            ParserTestCase(
                name = "Melli Bank withdrawal transaction",
                message = "مبلغ 750,000 ریال از حساب شما برداشت شد. مانده: 1,750,000 ریال",
                sender = "+98700717",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("1750000")
                )
            ),
            ParserTestCase(
                name = "Melli Bank purchase transaction",
                message = "مبلغ 250,000 ریال خرید با کارت شما انجام شد. مانده: 1,500,000 ریال",
                sender = "MELLI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("1500000"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Melli Bank transfer transaction",
                message = "مبلغ 1,000,000 ریال انتقال یافت. مانده: 500,000 ریال",
                sender = "MELLIBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("500000")
                )
            )
        )

        val handleCases = listOf(
            Pair("+98700717", true),
            Pair("MELLI", true),
            Pair("MELLIBANK", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases)
    }

    @TestFactory
    fun `factory resolves melli bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Melli Bank",
                sender = "+98700717",
                currency = "IRR",
                message = "مبلغ 1,500,000 ریال واریز به حساب شما انجام شد. مانده: 2,500,000 ریال",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500000"),
                    currency = "IRR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Melli Bank",
                sender = "MELLI",
                currency = "IRR",
                message = "مبلغ 750,000 ریال از حساب شما برداشت شد. مانده: 1,750,000 ریال",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Melli Bank factory tests")
    }
}
