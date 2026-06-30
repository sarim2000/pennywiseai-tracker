package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class LumbiniBikashBankParserTest {

    private val parser = LumbiniBikashBankParser()

    @TestFactory
    fun `lumbini bikash bank parser handles key paths`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Lumbini Bikash Bank (Nepal)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Debit - withdrawal with eSewa",
                message = "Dear Customer, NPR 1,199.00 has been withdrawn from your A/C 050######4545 on 28/04/2026 16:40. Remarks: 98412334556 Load eSewa;8234454",
                sender = "LBBL_SMART",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1199.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4545",
                    reference = "98412334556 Load eSewa"
                )
            ),
            ParserTestCase(
                name = "Credit - deposit",
                message = "Dear Customer, NPR 15,000.00 has been deposited in your A/C 050#####4545 on 29/3/2026 16:31. Remarks: Int Paid 34234;345454",
                sender = "LBBL_SMART",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    accountLast4 = "4545",
                    reference = "Int Paid 34234"
                )
            )
        )

        val handleCases = listOf(
            "LBBL_SMART" to true,
            "LBBL" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = cases,
            handleCases = handleCases,
            suiteName = "Lumbini Bikash Bank Parser Tests"
        )
    }

    @TestFactory
    fun `factory resolves lumbini bikash bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Lumbini Bikash Bank",
                sender = "LBBL_SMART",
                currency = "NPR",
                message = "Dear Customer, NPR 1,199.00 has been withdrawn from your A/C 050######4545 on 28/04/2026 16:40. Remarks: 98412334556 Load eSewa;8234454",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1199.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
