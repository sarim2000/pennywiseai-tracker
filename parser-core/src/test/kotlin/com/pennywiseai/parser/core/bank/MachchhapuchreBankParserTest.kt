package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class MachchhapuchreBankParserTest {

    private val parser = MachchhapuchreBankParser()

    @TestFactory
    fun `machchhapuchre bank parser handles key paths`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Machchhapuchchhre Bank (Nepal)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Debit - Withdrawal",
                message = "Dear CUSTOMER,NPR 3,190.00 Withdrawn from your A/C ###0018 on 29/03/2026 Remarks: medicine,K Available Bal: 20532.09. For app: http://bit.ly/3QZrCFj",
                sender = "MBL_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3190.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0018",
                    merchant = "medicine",
                    reference = "medicine,K",
                    balance = BigDecimal("20532.09")
                )
            ),
            ParserTestCase(
                name = "Credit - Deposit",
                message = "Dear CUSTOMER,NPR 50,000.00 Deposited in your A/C ###0018 on 26/02/2026 Remarks: Salary (Ma Available Bal: 55652.24. For app: http://bit.ly/3QZrCFj",
                sender = "MBL_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    accountLast4 = "0018",
                    reference = "Salary (Ma",
                    balance = BigDecimal("55652.24")
                )
            )
        )

        val handleCases = listOf(
            "MBL_ALERT" to true,
            "MBL" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = cases,
            handleCases = handleCases,
            suiteName = "Machchhapuchchhre Bank Parser Tests"
        )
    }

    @TestFactory
    fun `factory resolves machchhapuchre bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Machchhapuchchhre Bank",
                sender = "MBL_ALERT",
                currency = "NPR",
                message = "Dear CUSTOMER,NPR 3,190.00 Withdrawn from your A/C ###0018 on 29/03/2026 Remarks: medicine,K Available Bal: 20532.09. For app: http://bit.ly/3QZrCFj",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3190.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
