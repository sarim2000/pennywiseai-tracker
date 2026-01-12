package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class NMBBankParserTest {
    @TestFactory
    fun `test NMB Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = NMBBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "NMB Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Example 1: Fund transfer
            ParserTestCase(
                name = "Fund Transfer",
                message = """NMB_ALERT:

Fund transfer of NPR 250.00 to A/C 01000000055 was successful on 19-Feb-2025 15:38:23 If you have not done this transfer please contact us immediately.""",
                sender = "NMB_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "NPR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Fund Transfer",
                    accountLast4 = "0055"
                )
            ),

            // Example 2: ATM withdrawal with reference
            ParserTestCase(
                name = "ATM Withdrawal with Reference",
                message = """NMB_ALERT:

Enjoy the new features of eNMB App. Click here to learn more bit.ly/3qpteyE A/C 0#16 withdrawn NPR 700.00 on 24/05/2025 (FBS:D:FPQR:523396049).
.""",
                sender = "NMB_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("700.00"),
                    currency = "NPR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    accountLast4 = "0016",  // 0 + 16 = last 4 of combined
                    reference = "523396049"
                )
            ),

            // Example 3: Esewa wallet load
            ParserTestCase(
                name = "Esewa Wallet Load",
                message = """NMB_ALERT:

Your  Esewa Wallet Load for 9850000007 of 300.00 is successful on 24-May-2025 18:26:26 .""",
                sender = "NMB_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "NPR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Esewa Wallet Load"
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "NMB_ALERT" to true,
            "NMB" to true,
            "NMBBANK" to true,
            "NABIL" to true,
            "HDFC" to false,
            "SBI" to false,
            "" to false
        )

        val result =
            return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "NMB Bank Parser Tests")

    }
}
