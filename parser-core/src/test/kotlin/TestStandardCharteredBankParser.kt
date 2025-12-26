import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.StandardCharteredBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class StandardCharteredBankParserTest {

    @TestFactory
    fun `standard chartered bank parser handles expected scenarios`(): List<DynamicTest> {
        val parser = StandardCharteredBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Standard Chartered Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // UPI Debit transactions
            ParserTestCase(
                name = "UPI Debit Transfer - Example 1",
                message = "Your a/c XX3421 is debited for Rs. 302.00 on 03-12-2025 15:49 and credited to a/c XX1465 (UPI Ref no 487597904232).Plz call 18002586465 if not done by you.",
                sender = "VM-SCBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("302.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "UPI Transfer to XX1465",
                    accountLast4 = "3421",
                    reference = "487597904232"
                )
            ),

            ParserTestCase(
                name = "UPI Debit Transfer - Example 2",
                message = "Your a/c XX1234 is debited for Rs. 30.00 on 29-11-2025 22:49 and credited to a/c XX0025 (UPI Ref no 764379954202).Plz call 18002586465 if not done by you.",
                sender = "VD-SCBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "UPI Transfer to XX0025",
                    accountLast4 = "1234",
                    reference = "764379954202"
                )
            ),

            // NEFT Credit
            ParserTestCase(
                name = "NEFT Credit with Balance",
                message = "Dear Customer, there is an NEFT credit of INR 48,796.00 in your account 123xxxx7655 on 1/11/2025.Available Balance:INR 97,885.05 -StanChart",
                sender = "JK-SCBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("48796.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "NEFT Credit",
                    accountLast4 = "7655",
                    balance = BigDecimal("97885.05")
                )
            ),

            // Large amount with comma
            ParserTestCase(
                name = "UPI Transfer - Large Amount",
                message = "Your a/c XX5678 is debited for Rs. 1,250.00 on 01-12-2025 10:30 and credited to a/c XX9999 (UPI Ref no 123456789012).Plz call 18002586465 if not done by you.",
                sender = "VM-SCBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "UPI Transfer to XX9999",
                    accountLast4 = "5678",
                    reference = "123456789012"
                )
            ),

            // RTGS Credit
            ParserTestCase(
                name = "RTGS Credit",
                message = "Dear Customer, there is an RTGS credit of INR 100,000.00 in your account 456xxxx1234 on 15/12/2025.Available Balance:INR 250,000.00 -StanChart",
                sender = "SCBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "RTGS Credit",
                    accountLast4 = "1234",
                    balance = BigDecimal("250000.00")
                )
            ),

            // IMPS Credit
            ParserTestCase(
                name = "IMPS Credit",
                message = "Dear Customer, there is an IMPS credit of INR 5,000.00 in your account 789xxxx5555 on 10/12/2025.Available Balance:INR 15,000.00 -StanChart",
                sender = "VD-SCBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "IMPS Credit",
                    accountLast4 = "5555",
                    balance = BigDecimal("15000.00")
                )
            )
        )

        val handleChecks = listOf(
            "VM-SCBANK-S" to true,
            "VD-SCBANK-S" to true,
            "JK-SCBANK-S" to true,
            "SCBANK" to true,
            "StanChart" to true,
            "STANCHART" to true,
            "AX-SCBANK-T" to true,
            "HDFC" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Standard Chartered Bank Parser Tests"
        )
    }
}
