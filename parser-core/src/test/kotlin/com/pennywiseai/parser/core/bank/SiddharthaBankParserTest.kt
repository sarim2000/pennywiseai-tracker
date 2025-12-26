package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class SiddharthaBankParserTest {

    private val parser = SiddharthaBankParser()

    @TestFactory
    fun `siddhartha bank parser handles key paths`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Siddhartha Bank Limited (Nepal)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Debit - Fund Transfer (IBFT)",
                message = "Dear [NAME], AC ###XXXX1234, NPR 97.00 withdrawn on 09/12/2025 12:31:20 for Fund Trf to A/C PAYABLE IBFT (IN-670725619,222",
                sender = "SBL_Alert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("97.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "Fund Transfer (IBFT)",
                    accountLast4 = "1234",
                    reference = "IN-670725619"
                )
            ),
            ParserTestCase(
                name = "Debit - QR Payment",
                message = "Dear [NAME], AC ###XXXX1234, NPR 810.00 withdrawn on 05/12/2025 18:06:50 for QR Payment to FALCHA KHAJA GHAR - falcha",
                sender = "SBL_Alert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("810.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "FALCHA KHAJA GHAR",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Credit - Deposit (Fund Transfer)",
                message = "Dear [NAME], AC ###XXXX1234, NPR 120,000.00 deposited on 28/11/2025 20:13:59 for Fund Trf frm A/C PAYABLE IBF-FON:IBFT:1171853",
                sender = "SBL_Alert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("120000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    merchant = "Fund Transfer (IBFT)",
                    accountLast4 = "1234",
                    reference = "1171853"
                )
            ),
            ParserTestCase(
                name = "Debit - Utility Bill (NEA)",
                message = "Dear [NAME], AC ###XXXX1234, NPR 1,822.00 withdrawn on 09/12/2025 12:29:06 for Fund Trf to A/C PAYABLE IBFT (IN-670724040,NEA",
                sender = "SBL_Alert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1822.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "Nepal Electricity Authority",
                    accountLast4 = "1234",
                    reference = "IN-670724040"
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves siddhartha bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Siddhartha Bank",
                sender = "SBL_Alert",
                currency = "NPR",
                message = "Dear [NAME], AC ###XXXX1234, NPR 97.00 withdrawn on 09/12/2025 12:31:20 for Fund Trf to A/C PAYABLE IBFT (IN-670725619,222",
                expected = ExpectedTransaction(
                    amount = BigDecimal("97.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Siddhartha Bank",
                sender = "SBL-Alert",
                currency = "NPR",
                message = "Dear Customer, AC ###XXXX5678, NPR 500.00 deposited on 01/12/2025 10:00:00 for deposit",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Siddhartha Bank",
                sender = "SIDDHARTHA",
                currency = "NPR",
                message = "Dear Customer, NPR 100.00 withdrawn",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
