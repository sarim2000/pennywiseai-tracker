import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.MoniepointParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class MoniepointParserTest {

    @TestFactory
    fun `moniepoint parser handles common cases`(): List<DynamicTest> {
        val parser = MoniepointParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Credit alert (INCOME)",
                message = """
                    CREDIT ALERT

                    Acc: 512****904 (Personal)
                    Amt: NGN1,000.00
                    Bal: NGN5,000.00
                    Date: 15/01/26
                    Time: 03:09 PM
                    Desc: Transfer from JANE DOE
                """.trimIndent(),
                sender = "Moniepoint",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "Transfer from JANE DOE",
                    accountLast4 = "2904",
                    balance = BigDecimal("5000.00")
                )
            ),
            ParserTestCase(
                name = "Debit alert (EXPENSE)",
                message = """
                    DEBIT ALERT

                    Acc: 512****904 (Personal)
                    Amt: NGN1,000.00
                    Bal: NGN3,000.00
                    Date: 15/01/26
                    Time: 03:01 PM
                    Desc: Test SMS
                """.trimIndent(),
                sender = "Moniepoint",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "Test SMS",
                    accountLast4 = "2904",
                    balance = BigDecimal("3000.00")
                )
            ),
            ParserTestCase(
                name = "Business account label is not absorbed into the account number",
                message = """
                    CREDIT ALERT

                    Acc: 512****904 (Business)
                    Amt: NGN250,000.50
                    Bal: NGN1,250,000.00
                    Date: 15/01/26
                    Time: 11:45 AM
                    Desc: Transfer from ACME LTD
                """.trimIndent(),
                sender = "Moniepoint",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250000.50"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "Transfer from ACME LTD",
                    accountLast4 = "2904",
                    balance = BigDecimal("1250000.00")
                )
            )
        )

        val handleCases = listOf(
            "Moniepoint" to true,
            "MONIEPOINT" to true,
            "AD-MONIEPOINT-S" to true,
            "Monnify" to true,
            "GTBank" to false,
            "VFD MFB" to false,
            "AccessBank" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Moniepoint Parser Suite"
        )
    }

    @Test
    fun `parenthesised account label never leaks into accountLast4`() {
        val parser = MoniepointParser()
        val message = """
            CREDIT ALERT

            Acc: 512****904 (Personal)
            Amt: NGN1,000.00
            Bal: NGN5,000.00
            Date: 15/01/26
            Time: 03:09 PM
            Desc: Transfer from JANE DOE
        """.trimIndent()

        val parsed = parser.parse(message, "Moniepoint", 0L)
        Assertions.assertNotNull(parsed)
        Assertions.assertEquals(
            "2904", parsed!!.accountLast4,
            "The trailing (Personal) label must not be absorbed by the Acc: pattern"
        )
    }

    @Test
    fun `empty Desc field yields a null merchant, not the next line`() {
        val parser = MoniepointParser()
        val message = """
            DEBIT ALERT

            Acc: 512****904 (Personal)
            Amt: NGN1,000.00
            Bal: NGN3,000.00
            Date: 15/01/26
            Time: 03:01 PM
            Desc:
        """.trimIndent()

        val parsed = parser.parse(message, "Moniepoint", 0L)
        Assertions.assertNotNull(parsed, "Message should still parse")
        Assertions.assertNull(parsed!!.merchant, "An empty Desc: must not capture another line")
    }

    @Test
    fun `OTP messages are not treated as transactions`() {
        val parser = MoniepointParser()
        val message = "Your Moniepoint OTP is 123456. Do not share it with anyone."
        Assertions.assertNull(
            parser.parse(message, "Moniepoint", 0L),
            "OTP messages must not parse as transactions"
        )
    }
}
