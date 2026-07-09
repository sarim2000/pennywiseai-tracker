package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class PasargadBankParserTest {

    private val parser = PasargadBankParser()

    @TestFactory
    fun `pasargad bank parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Pasargad Bank deposit transaction 1",
                message = """
                    777.888.20000275.1
                    +1,000,000,000
                    04/13_10:22
                    مانده: 1,000,000,000
                """.trimIndent(),
                sender = "B.Pasargad",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000000000"),
                    currency = "IRR",
                    type = TransactionType.INCOME,
                    accountLast4 = "2751",
                    balance = BigDecimal("1000000000")
                )
            ),
            ParserTestCase(
                name = "Pasargad Bank deposit transaction 2",
                message = """
                    777.888.20000275.1
                    +870,000,000
                    04/18_07:26
                    مانده: 1,870,000,000
                """.trimIndent(),
                sender = "B.Pasargad",
                expected = ExpectedTransaction(
                    amount = BigDecimal("870000000"),
                    currency = "IRR",
                    type = TransactionType.INCOME,
                    accountLast4 = "2751",
                    balance = BigDecimal("1870000000")
                )
            ),
            ParserTestCase(
                name = "Pasargad Bank withdrawal transaction",
                message = """
                    777.888.20000275.1
                    -870,000,000
                    04/18_07:26
                    مانده: 1,000,000,000
                """.trimIndent(),
                sender = "B.Pasargad",
                expected = ExpectedTransaction(
                    amount = BigDecimal("870000000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "2751",
                    balance = BigDecimal("1000000000")
                )
            )
        )

        val handleCases = listOf(
            Pair("B.Pasargad", true),
            Pair("B.PASARGAD", true),
            Pair("PASARGAD", true),
            Pair("wepod", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases)
    }

    @TestFactory
    fun `pasargad bank parser ignores non-transaction messages`(): List<DynamicTest> {
        val ignoredMessages = listOf(
            "OTP verification",
            """
                Your OTP code is 12345
            """.trimIndent()
        )

        return ignoredMessages.map { msg ->
            DynamicTest.dynamicTest("Should ignore: ${msg.take(30)}...") {
                org.junit.jupiter.api.Assertions.assertNull(parser.parse(msg, "B.Pasargad", System.currentTimeMillis()))
            }
        }
    }

    @TestFactory
    fun `factory resolves pasargad bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Pasargad Bank",
                sender = "B.Pasargad",
                currency = "IRR",
                message = """
                    777.888.20000275.1
                    +1,000,000,000
                    04/13_10:22
                    مانده: 1,000,000,000
                """.trimIndent(),
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000000000"),
                    currency = "IRR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Pasargad Bank",
                sender = "wepod",
                currency = "IRR",
                message = """
                    777.888.20000275.1
                    -870,000,000
                    04/18_07:26
                    مانده: 1,000,000,000
                """.trimIndent(),
                expected = ExpectedTransaction(
                    amount = BigDecimal("870000000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Pasargad Bank factory tests")
    }
}
