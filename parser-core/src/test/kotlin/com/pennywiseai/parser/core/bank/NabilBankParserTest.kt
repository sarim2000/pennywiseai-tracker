package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class NabilBankParserTest {

    private val parser = NabilBankParser()

    @TestFactory
    fun `nabil bank parser handles key paths`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Nabil Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Withdrawn example with reference",
                message = """Dear Customer, Your 091##04118 has been withdrawn by NPR 20,008.00 on 17/04/2026 07:58:06, Remarks: MTXN0000517374-130
Download App: https://rebrand.ly/nBank""",
                sender = "NABIL_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20008.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4118",
                    reference = "MTXN0000517374-130"
                )
            )
        )

        val handleCases = listOf(
            "NABIL_ALERT" to true,
            "NABIL" to true,
            "NMB_ALERT" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = cases,
            handleCases = handleCases,
            suiteName = "Nabil Bank Parser Tests"
        )
    }

    @TestFactory
    fun `factory resolves nabil bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Nabil Bank",
                sender = "NABIL_ALERT",
                currency = "NPR",
                message = "Dear Customer, Your 091##04118 has been withdrawn by NPR 20,008.00 on 17/04/2026 07:58:06, Remarks: MTXN0000517374-130",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20008.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
