package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class BankOfIndiaParserTest {
    @TestFactory
    fun `test Bank of India Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = BankOfIndiaParser()

        ParserTestUtils.printTestHeader(
            parserName = "Bank of India",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Cash deposit via Cash Acceptor Machine
            ParserTestCase(
                name = "Cash Deposit via Cash Acceptor Machine",
                message = "BOI -  Cash Rs. 500 deposited in your account XX5468 from Cash Acceptor Machine R0807030 at  MAIN TRIMBAK ROAD ON 14-10-2025. Available balance Rs. 20100.81",
                sender = "JM-BOIIND-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "Cash Deposit",
                    accountLast4 = "5468",
                    balance = BigDecimal("20100.81")
                )
            ),

            // Cash deposit via Cash Acceptor Machine - variant
            ParserTestCase(
                name = "Cash Deposit at MAIN ROAD",
                message = "BOI -  Cash Rs. 500 deposited in your account XX5468 from Cash Acceptor Machine R0807030 at  MAIN ROAD ON 14-10-2025. Available balance Rs. 15000.50",
                sender = "BOIIND",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "Cash Deposit",
                    accountLast4 = "5468",
                    balance = BigDecimal("15000.50")
                )
            ),

            // UPI debit transaction
            ParserTestCase(
                name = "UPI Debit Transaction",
                message = "Rs.200.00 debited A/cXX5468 and credited to SAI MISAL via UPI Ref No 315439383341 on 23Aug25. Call 18001031906, if not done by you. -BOI",
                sender = "JM-BOIIND-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "SAI MISAL",
                    accountLast4 = "5468",
                    reference = "315439383341"
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "JM-BOIIND-S" to true,
            "JD-BOIIND-S" to true,
            "BK-BOIIND-S" to true,
            "BOIIND" to true,
            "BOIBNK" to true,
            "HDFC" to false,
            "SBI" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            testCases,
            handleCases,
            "Bank of India Parser Tests"
        )

    }
}
