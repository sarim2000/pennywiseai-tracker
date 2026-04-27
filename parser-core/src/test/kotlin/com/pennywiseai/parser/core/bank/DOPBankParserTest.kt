package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class DOPBankParserTest {

    @TestFactory
    fun `dop parser handles transaction alerts`(): List<DynamicTest> {
        val parser = DOPBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Credit with Rs amount and balance",
                message = "Account  No. XXXXXXXX1234 CREDIT with amount Rs. 5550.00 on 02-03-2026. Balance: Rs.40000.00. [S76543210]",
                sender = "VM-DOPBNK-G",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5550.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    balance = BigDecimal("40000.00"),
                    reference = "S76543210"
                )
            ),
            ParserTestCase(
                name = "Credit from different sender prefix",
                message = "Account  No. XXXXXXXX1234 CREDIT with amount Rs. 5550.00 on 02-02-2026. Balance: Rs.37500.00. [S33475450]",
                sender = "BZ-DOPBNK-G",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5550.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    balance = BigDecimal("37500.00"),
                    reference = "S33475450"
                )
            ),
            ParserTestCase(
                name = "Credit with S suffix sender",
                message = "Account  No. XXXXXXXX1234 CREDIT with amount Rs. 5550.00 on 02-01-2026. Balance: Rs.32000.00. [S92247102]",
                sender = "BV-DOPBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5550.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    balance = BigDecimal("32000.00"),
                    reference = "S92247102"
                )
            ),
            ParserTestCase(
                name = "Debit transaction",
                message = "Account No. XXXXXXXX5678 DEBIT with amount Rs. 2000.00 on 15-03-2026. Balance: Rs.18000.00. [D12345678]",
                sender = "VM-DOPBNK-G",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("18000.00"),
                    reference = "D12345678"
                )
            )
        )

        val handleChecks = listOf(
            "VM-DOPBNK-G" to true,
            "BZ-DOPBNK-G" to true,
            "BV-DOPBNK-S" to true,
            "BT-DOPBNK-G" to true,
            "DOP-ALERTS" to true,
            "ALERT-DOP" to true,
            "DOP" to true,
            "UNKNOWN" to false,
            "HDFC" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "DOP Parser"
        )
    }
}
