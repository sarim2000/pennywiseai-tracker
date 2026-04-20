package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest
import java.math.BigDecimal

class GreaterBankParserTest {

    @TestFactory
    fun `test Greater Bank Parser`(): List<DynamicTest> {
        val parser = GreaterBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Greater Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit alert with balance",
                message = "Your Account XXXX5207 had a DEBIT transaction of RS. 100.00 on 19/04/2026 at 23:21:35.Available balance is Rs. 1127.55: GREATER BANK",
                sender = "GREATERBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Debit Transaction",
                    accountLast4 = "5207",
                    balance = BigDecimal("1127.55"),
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "UPI transfer debit with reference",
                message = "Your a/c no. XXXXXXXX5207 is debited for Rs.100.00 on 19-04-26 and credited to a/c no. XXXXXXXX8364 (UPI Ref no 232135417634) If Not You? Call 18001217224 Greater Bank",
                sender = "GREATERBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Bank Transfer",
                    accountLast4 = "5207",
                    reference = "232135417634",
                    isFromCard = false
                )
            )
        )

        val handleCases = listOf(
            "GREATERBNK" to true,
            "VM-GRTRBN-S" to true,
            "AD-GRTRBN-T" to true,
            "SBIBANK" to false,
            "HDFCBNK" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            testCases,
            handleCases,
            "Greater Bank Parser Tests"
        )
    }
}
