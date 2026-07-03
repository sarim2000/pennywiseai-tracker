package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class StandardCharteredNepalParserTest {

    private val parser = StandardCharteredNepalParser()

    @TestFactory
    fun `standard chartered nepal parser handles key paths`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Standard Chartered Bank Nepal",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Debit - withdrawal",
                message = "NPR 95,000.00 has been debited from your account 3301.",
                sender = "SC_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("95000.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3301"
                )
            ),
            ParserTestCase(
                name = "Credit - deposit",
                message = "NPR 80,000.00 has been deposited into your account 1234.",
                sender = "SC_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("80000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Expense - ATM withdrawal",
                message = "NPR 5,000.00 has been debited from your account 3301 at ATM.",
                sender = "SC_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3301",
                    merchant = "ATM Withdrawal"
                )
            )
        )

        val handleCases = listOf(
            "SC_ALERT" to true,
            "SCBANK" to false,
            "STANCHART" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = cases,
            handleCases = handleCases,
            suiteName = "Standard Chartered Nepal Parser Tests"
        )
    }

    @TestFactory
    fun `factory resolves standard chartered nepal`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Standard Chartered Bank Nepal",
                sender = "SC_ALERT",
                currency = "NPR",
                message = "NPR 95,000.00 has been debited from your account 3301.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("95000.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
