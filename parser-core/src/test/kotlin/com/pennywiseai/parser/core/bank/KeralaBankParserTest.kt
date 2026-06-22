package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class KeralaBankParserTest {

    private val parser = KeralaBankParser()

    @TestFactory
    fun `kerala bank parser handles common cases`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Loan recovery credit with negative balance",
                message = "Dear Customer Your A/c no XXXX0024 is credited with 15000.00 on 06-06-2026 by Loan Recovery From : 139451061. Balance is -579822.00 - Kerala Bank",
                sender = "VM-KELBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Loan Recovery",
                    accountLast4 = "0024",
                    balance = BigDecimal("-579822.00")
                )
            )
        )

        val handleChecks = listOf(
            "KELBNK" to true,
            "VM-KELBNK-S" to true,
            "KGBANK" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Kerala Bank Parser")
    }
}
