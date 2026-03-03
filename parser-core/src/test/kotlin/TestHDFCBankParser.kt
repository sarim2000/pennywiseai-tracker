package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class HDFCBankParserTest {
    @TestFactory
    fun `test HDFC Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = HDFCBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "HDFC Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Bill Alert - should NOT parse as transaction
            ParserTestCase(
                name = "Bill Alert Notification - Should Not Parse",
                message = """New Bill Alert:
Your XUBA00000TST1A Bill 1234567890 of Rs.1500.00 is due on 15-Jan-2026. To pay, login to HDFC Bank Net/Mobile Banking>BillPay
T&C. Ignore if paid""",
                sender = "CP-HDFCBK-S",
                shouldParse = false
            ),

            // Actual transaction examples that SHOULD parse
            ParserTestCase(
                name = "UPI Debit Transaction",
                message = "Rs.500.00 debited from A/c XX1234 on 20-Oct-25 to merchant@upi (UPI Ref No 123456789012)",
                sender = "CP-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    reference = "123456789012"
                )
            ),

            ParserTestCase(
                name = "Sent from A/C with asterisk mask",
                message = "Sent Rs.15000.00 From HDFC Bank A/C *1234 To TEST MERCHANT PVT LTD On 01/01/26 Ref 567890567890 Not You? Call 18005556789/SMS BLOCK UPI to 7305556789",
                sender = "HDFCBK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    merchant = "TEST MERCHANT"
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "CP-HDFCBK-S" to true,
            "AX-HDFCBK-S" to true,
            "JM-HDFCBK-S" to true,
            "HDFCBANK" to true,
            "SBI" to false,
            "" to false
        )

        val result =
            return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "HDFC Bank Parser Tests")

    }
}
