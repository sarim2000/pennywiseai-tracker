import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.IndianBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class IndianBankParserTest {

    @TestFactory
    fun `indian bank parser handles primary formats`(): List<DynamicTest> {
        val parser = IndianBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Indian Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "UPI credit transaction with VPA",
                message = "Rs.2.00 credited to a/c *8175 on 07/10/2025 by a/c linked to VPA poweraccess.paytm3@axisbank (UPI Ref no 981408452805).Indian Bank",
                sender = "BV-INDBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "8175",
                    merchant = "poweraccess.paytm3",
                    reference = "981408452805"
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal",
                message = "Rs. 2000 withdrawn from ATM at MAIN STREET BRANCH on 09/10/2025.Indian Bank",
                sender = "INDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM - MAIN STREET BRANCH"
                )
            ),
            ParserTestCase(
                name = "Cash deposit",
                message = "Rs. 5000.00 deposited to a/c *8175 on 10/10/2025.Indian Bank",
                sender = "INDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "8175"
                )
            ),
            ParserTestCase(
                name = "Large UPI credit with VPA",
                message = "Rs.15000.00 credited to a/c *1234 on 11/10/2025 by a/c linked to VPA customer@paytm (UPI Ref no 555444333222).Indian Bank",
                sender = "BV-INDBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    merchant = "customer",
                    reference = "555444333222"
                )
            ),
            ParserTestCase(
                name = "Small amount credit",
                message = "Rs.1.50 credited to a/c *5678 on 12/10/2025 by a/c linked to VPA reward@gpay (UPI Ref no 111222333444).Indian Bank",
                sender = "BV-INDBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1.50"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    merchant = "reward",
                    reference = "111222333444"
                )
            ),
            ParserTestCase(
                name = "Alternative sender pattern",
                message = "Rs.100.00 credited to a/c *9012 on 13/10/2025.Indian Bank",
                sender = "INDIAN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "9012"
                )
            )
        )

        val handleChecks = listOf(
            "BV-INDBNK-S" to true,
            "AD-INDBNK-S" to true,
            "AX-INDBNK-S" to true,
            "INDBNK" to true,
            "INDIAN" to true,
            "XX-INDBNK-T" to true,  // OTP messages (though not transaction)
            "AB-INDBNK-P" to true,  // Promotional messages
            "UNKNOWN" to false,
            "HDFC" to false,
            "SBI" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Indian Bank Parser"
        )


    }

    @TestFactory
    fun `factory resolves indian bank`(): List<DynamicTest> {
        val cases = listOf(
            com.pennywiseai.parser.core.test.SimpleTestCase(
                bankName = "Indian Bank",
                sender = "BV-INDBNK-S",
                currency = "INR",
                message = "Rs.2.00 credited to a/c *8175 on 07/10/2025 by a/c linked to VPA poweraccess.paytm3@axisbank (UPI Ref no 981408452805).Indian Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "8175",
                    merchant = "poweraccess.paytm3",
                    reference = "981408452805"
                ),
                shouldHandle = true
            ),
            com.pennywiseai.parser.core.test.SimpleTestCase(
                bankName = "Indian Bank",
                sender = "INDBNK",
                currency = "INR",
                message = "Rs. 1000.00 deposited to a/c *5678 on 01/01/2025.Indian Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678"
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Indian Bank Factory Tests")

    }
}
