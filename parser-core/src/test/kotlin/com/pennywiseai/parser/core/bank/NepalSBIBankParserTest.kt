package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class NepalSBIBankParserTest {

    private val parser = NepalSBIBankParser()

    @TestFactory
    fun `nepal sbi bank parser handles key paths`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Nepal SBI Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Credit - deposit",
                message = "Your A/c XX0673 Credited by NPR 20000.00 on 08-06-2026 16:35:06,Ref: 664406213/. -NSBI\nDownload YONO Nepal SBI by clicking bit.ly/4e0NYk8 for A/c balance.",
                sender = "NSBI_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    accountLast4 = "0673",
                    reference = "664406213/"
                )
            ),
            ParserTestCase(
                name = "Debit - expense",
                message = "Your A/c XX0673 Debited by NPR 400000.00 on 08-06-2026 12:49:04,Ref: MERCHANT NAME. -NSBI\nDownload YONO Nepal SBI by clicking bit.ly/4e0NYk8 for A/c balance.",
                sender = "NSBI_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("400000.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0673",
                    reference = "MERCHANT NAME"
                )
            )
        )

        val handleCases = listOf(
            "NSBI_ALERT" to true,
            "NSBI" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = cases,
            handleCases = handleCases,
            suiteName = "Nepal SBI Bank Parser Tests"
        )
    }

    @TestFactory
    fun `factory resolves nepal sbi bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Nepal SBI Bank",
                sender = "NSBI_ALERT",
                currency = "NPR",
                message = "Your A/c XX0673 Credited by NPR 20000.00 on 08-06-2026 16:35:06,Ref: 664406213/. -NSBI\nDownload YONO Nepal SBI by clicking bit.ly/4e0NYk8 for A/c balance.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
