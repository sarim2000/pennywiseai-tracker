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
            ),

            ParserTestCase(
                name = "Credit Card Spent - Avenue Supermarts (format 2)",
                message = """Spent
Card no. XX7441
INR 562
01-09-25 12:04:18
AVENUE SUPE
Avl Lmt INR 5120.87
SMS BLOCK 7441 to 919951860002, if not you - Axis Bank""",
                sender = "CP-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("562"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "AVENUE",  // cleanMerchantName converts ALL_CAPS -> Proper Case, but single words stay uppercase
                    accountLast4 = "7441",
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - Blinkit (Format 1 with IST)",
                message = """Spent INR 174
Axis Bank Card no. XX7441
13-09-25 21:35:56 IST
Blinkit
Avl Limit: INR 6652.78
Not you? SMS BLOCK 7441 to 919951860002""",
                sender = "JX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("174"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Blinkit",
                    accountLast4 = "7441",
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - Blinkit (Format 2 without IST)",
                message = """Spent
Card no. XX7441
INR 207
01-09-25 14:10:35
Blinkit
Avl Lmt INR 4632.87
SMS BLOCK 7441 to 919951860002, if not you - Axis Bank""",
                sender = "AX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("207"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Blinkit",
                    accountLast4 = "7441",
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - BPCL Petrol",
                message = """Spent INR 500
Axis Bank Card no. XX6018
22-09-25 09:03:41 IST
BPCL ARUNAA
Avl Limit: INR 17131.47
Not you? SMS BLOCK 6018 to 919951860002""",
                sender = "CP-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "BPCL ARUNAA",
                    accountLast4 = "6018",
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent - JSK Fuel Station",
                message = """Spent INR 500
Axis Bank Card no. XX6018
13-09-25 13:08:07 IST
JSK FUEL ST
Avl Limit: INR 6826.78
Not you? SMS BLOCK 6018 to 919951860002""",
                sender = "JX-AXISBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "JSK FUEL ST",
                    accountLast4 = "6018",
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
