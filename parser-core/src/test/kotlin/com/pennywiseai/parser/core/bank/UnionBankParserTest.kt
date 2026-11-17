package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class UnionBankParserTest {
    @TestFactory
    fun `Union Bank parser`(): List<DynamicTest> {
        val testCases = listOf(
            ParserTestCase(
                name = "Long form mobile banking debit",
                message = "A/c *1234 Debited for Rs:100.00 on 11-08-2025 18:28:02 by Mob Bk ref no 123456789000 Avl Bal Rs:12345.67",
                sender = "VM-UNIONB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Mobile Banking Transfer",
                    accountLast4 = "1234",
                    balance = BigDecimal("12345.67")
                )
            ),
            ParserTestCase(
                name = "Short form \"Dr\" mobile banking debit",
                message = "Union Bank of India A/c*1234 Dr Rs166.00 on 15-11-26 21:26 by MB 6600000000 SAMPLE STORE, Bal Rs137.57 Not U?Call 18002333/SMS BLOCK 1234 to 0000000000",
                sender = "JX-UNIONB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("166.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "SAMPLE STORE",
                    accountLast4 = "1234",
                    balance = BigDecimal("137.57"),
                    reference = "6600000000"
                )
            ),
            ParserTestCase(
                name = "Short form \"Cr\" credit",
                message = "Union Bank of India A/c*1234 Cr Rs500.00 on 15-11-26 10:05 by MB 6600000001 SAMPLE SENDER, Bal Rs637.57 Not U?Call 0000000000",
                sender = "JX-UNIONB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    balance = BigDecimal("637.57")
                )
            ),
            ParserTestCase(
                name = "Short form \"Dr\" to an investment platform is INVESTMENT",
                message = "Union Bank of India A/c*1234 Dr Rs500.00 on 15-11-26 21:26 by MB 6600000002 GROWW, Bal Rs137.57",
                sender = "JX-UNIONB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.INVESTMENT,
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "OTP quoting a \"Dr\" amount is ignored",
                message = "OTP 123456 for A/c*1234 Dr Rs166.00. Do not share.",
                sender = "JX-UNIONB-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "OTP is ignored",
                message = "123456 is your OTP for login to Union Bank Vyom. Do not share it with anyone.",
                sender = "JX-UNIONB-S",
                shouldParse = false
            )
        )
        val handleCases = listOf("JX-UNIONB-S" to true, "VM-UNIONB" to true, "HDFCBK" to false)
        return ParserTestUtils.runTestSuite(UnionBankParser(), testCases, handleCases, "Union Bank Parser Tests")
    }
}
