import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.SaraswatBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class SaraswatBankParserTest {

    @TestFactory
    fun `saraswat bank parser handles primary formats`(): List<DynamicTest> {
        val parser = SaraswatBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Saraswat Co-operative Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "ACH Credit transaction",
                message = "Your A/c no. 1234 is credited with INR 100.50 on 13-10-2025 towards ACH Credit:MERCHANT NAME. Current Bal is INR 950.00 CR  - Saraswat Bank",
                sender = "JD-SARBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.50"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    merchant = "MERCHANT NAME",
                    balance = BigDecimal("950.00")
                )
            ),
            ParserTestCase(
                name = "Standing Instruction debit",
                message = "Dear Customer, Your account no. ending with 5678 is debited with INR 1,000.00 on 25-09-2025  for S.I. Current Bal is INR 8,500.00CR. - Saraswat Bank",
                sender = "JD-SARBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    merchant = "Standing Instruction",
                    balance = BigDecimal("8500.00")
                )
            ),
            ParserTestCase(
                name = "Simple credit transaction",
                message = "Your A/c no. 9012 is credited with INR 500.00 on 01-11-2025 towards Salary. Current Bal is INR 15,000.00 CR - Saraswat Bank",
                sender = "SARBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "9012",
                    merchant = "Salary",
                    balance = BigDecimal("15000.00")
                )
            ),
            ParserTestCase(
                name = "Large amount debit",
                message = "Dear Customer, Your account no. ending with 3456 is debited with INR 25,000.00 on 15-10-2025 for NEFT. Current Bal is INR 50,000.00CR. - Saraswat Bank",
                sender = "AD-SARBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3456",
                    merchant = "NEFT Transfer",
                    balance = BigDecimal("50000.00")
                )
            ),
            ParserTestCase(
                name = "Small amount credit",
                message = "Your A/c no. 7890 is credited with INR 10.00 on 20-10-2025 towards Cashback. Current Bal is INR 1,200.50 CR - Saraswat Bank",
                sender = "SARASWAT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "7890",
                    merchant = "Cashback",
                    balance = BigDecimal("1200.50")
                )
            ),
            ParserTestCase(
                name = "RTGS transfer debit",
                message = "Dear Customer, Your account no. ending with 2468 is debited with INR 50,000.00 on 22-10-2025 for RTGS. Current Bal is INR 100,000.00CR. - Saraswat Bank",
                sender = "JD-SARBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "2468",
                    merchant = "RTGS Transfer",
                    balance = BigDecimal("100000.00")
                )
            ),
            ParserTestCase(
                name = "IMPS transfer debit",
                message = "Dear Customer, Your account no. ending with 1357 is debited with INR 2,500.00 on 23-10-2025 for IMPS. Current Bal is INR 12,345.67CR. - Saraswat Bank",
                sender = "BV-SARBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1357",
                    merchant = "IMPS Transfer",
                    balance = BigDecimal("12345.67")
                )
            ),
            ParserTestCase(
                name = "Alternative sender format",
                message = "Your A/c no. 9753 is credited with INR 750.00 on 24-10-2025 towards Refund. Current Bal is INR 5,000.00 CR - Saraswat Bank",
                sender = "SARASWATBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "9753",
                    merchant = "Refund",
                    balance = BigDecimal("5000.00")
                )
            )
        )

        val handleChecks = listOf(
            "JD-SARBNK-S" to true,
            "AD-SARBNK-S" to true,
            "BV-SARBNK-S" to true,
            "SARBNK" to true,
            "SARASWAT" to true,
            "SARASWATBANK" to true,
            "XX-SARBNK-T" to true,
            "UNKNOWN" to false,
            "HDFC" to false,
            "SBI" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Saraswat Co-operative Bank Parser"
        )


    }

    @TestFactory
    fun `factory resolves saraswat bank`(): List<DynamicTest> {
        val cases = listOf(
            com.pennywiseai.parser.core.test.SimpleTestCase(
                bankName = "Saraswat Co-operative Bank",
                sender = "JD-SARBNK-S",
                currency = "INR",
                message = "Your A/c no. 1234 is credited with INR 100.00 on 01-01-2025 towards Payment. Current Bal is INR 1,000.00 CR - Saraswat Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    merchant = "Payment",
                    balance = BigDecimal("1000.00")
                ),
                shouldHandle = true
            ),
            com.pennywiseai.parser.core.test.SimpleTestCase(
                bankName = "Saraswat Co-operative Bank",
                sender = "SARBNK",
                currency = "INR",
                message = "Dear Customer, Your account no. ending with 5678 is debited with INR 500.00 on 02-01-2025 for SI. Current Bal is INR 2,000.00CR. - Saraswat Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    merchant = "Standing Instruction",
                    balance = BigDecimal("2000.00")
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Saraswat Bank Factory Tests")

    }
}
