package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class CitizensBankParserTest {

    private val parser = CitizensBankParser()

    @TestFactory
    fun `citizens bank parser handles key paths`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Citizens Bank (Nepal)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Debit - ATM withdrawal",
                message = "Dear ARUN, ###4041 is debited by NPR 5,000.00 on 29/03/2026, Remarks: ATM/459521x2018/CTZW Av Bal: 140270.04. Support Center:01-5970068",
                sender = "CTZN_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    accountLast4 = "4041",
                    reference = "ATM/459521x2018/CTZW",
                    balance = BigDecimal("140270.04")
                )
            ),
            ParserTestCase(
                name = "Credit - cIPS transfer",
                message = "Dear ARUN, ###4041 is credited by NPR 150,000.00 on 25/03/2026, Remarks: cIPS/NP2603250066636 Av Bal: 153520.04. Support Center:01-5970068",
                sender = "CTZN_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    merchant = "cIPS",
                    accountLast4 = "4041",
                    reference = "cIPS/NP2603250066636",
                    balance = BigDecimal("153520.04")
                )
            )
        )

        val handleCases = listOf(
            "CTZN_ALERT" to true,
            "CTZN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = cases,
            handleCases = handleCases,
            suiteName = "Citizens Bank Parser Tests"
        )
    }

    @TestFactory
    fun `factory resolves citizens bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Citizens Bank",
                sender = "CTZN_ALERT",
                currency = "NPR",
                message = "Dear ARUN, ###4041 is debited by NPR 5,000.00 on 29/03/2026, Remarks: ATM/459521x2018/CTZW Av Bal: 140270.04. Support Center:01-5970068",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
