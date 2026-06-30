package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class NepalBankParserTest {

    private val parser = NepalBankParser()

    @TestFactory
    fun `nepal bank parser handles key paths`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Nepal Bank Limited",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Credit - income",
                message = "Dear CUSTOMER_NAME,##001 credited NPR 5,000.00 15/06/2026 12:53:44,5345345/1233123",
                sender = "NBL_Alert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    accountLast4 = "001",
                    reference = "5345345/1233123"
                )
            ),
            ParserTestCase(
                name = "Debit - expense",
                message = "Dear CUSTOMER_NAME,##001 debited NPR 5,000.00 04/04/2026 12:12:12,42343244@4234234 43432@VISA",
                sender = "NBL_Alert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "VISA Transaction",
                    accountLast4 = "001",
                    reference = "42343244@4234234"
                )
            )
        )

        val handleCases = listOf(
            "NBL_Alert" to true,
            "NABIL_ALERT" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = cases,
            handleCases = handleCases,
            suiteName = "Nepal Bank Limited Parser Tests"
        )
    }

    @TestFactory
    fun `factory resolves nepal bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Nepal Bank Limited",
                sender = "NBL_Alert",
                currency = "NPR",
                message = "Dear CUSTOMER_NAME,##001 credited NPR 5,000.00 15/06/2026 12:53:44,5345345/1233123",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
