package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class JupiterBankParserTest {

    private val parser = JupiterBankParser()

    @TestFactory
    fun `jupiter parser handles key paths`(): List<DynamicTest> {
        val testCases = listOf(
            ParserTestCase(
                name = "CSB credit card debit via UPI",
                message = "Rs.25.00 debited to your Edge CSB Bank RuPay Credit Card ending 6788 on 3/18/26, 6:39 PM - (UPI Ref no.702711160776). To dispute, call 8655055086.",
                sender = "VK-JTEDGE-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = null,
                    accountLast4 = "6788",
                    reference = "702711160776"
                )
            )
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = listOf("VK-JTEDGE-S" to true, "HDFCBK" to false),
            suiteName = "Jupiter Parser"
        )
    }
}
