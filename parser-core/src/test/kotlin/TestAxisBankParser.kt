package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ExpectedTransaction
import org.junit.jupiter.api.Test

import java.math.BigDecimal

class AxisBankParserTest {
    @Test
    fun `test Axis Bank Parser comprehensive test suite`() {
        val parser = AxisBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Axis Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Credit card "Spent" transactions
            ParserTestCase(
                name = "Credit Card Spent - Swiggy",
                message = """Spent INR 131
Axis Bank Card no. XX0818
05-10-25 09:43:27 IST
Swiggy Limi
Avl Limit: INR 217162.72
Not you? SMS BLOCK 0818 to 919951860002""",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("131"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Swiggy",
                    accountLast4 = "0818",
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - Amazon",
                message = """Spent INR 1299.00
Axis Bank Card no. XX5678
12-10-25 14:30:15 IST
Amazon Pay
Avl Limit: INR 50000.00
Not you? SMS BLOCK 5678 to 919951860002""",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1299.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Amazon",
                    accountLast4 = "5678",
                    isFromCard = true
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "AX-AXISBK-S" to true,
            "AX-AXISBANK-S" to true,
            "AX-AXIS-S" to true,
            "AXISBK" to true,
            "AXISBANK" to true,
            "AXIS" to true,
            "HDFC" to false,
            "SBI" to false,
            "" to false
        )

        val result = ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Axis Bank Parser Tests")
        ParserTestUtils.printTestSummary(
            totalTests = result.totalTests,
            passedTests = result.passedTests,
            failedTests = result.failedTests,
            failureDetails = result.failureDetails
        )
    }
}
