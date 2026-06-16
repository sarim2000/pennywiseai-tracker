import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.JaizBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class JaizBankParserTest {

    @TestFactory
    fun `jaiz bank parser handles common cases`(): List<DynamicTest> {
        val parser = JaizBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Debit transaction (EXPENSE)",
                message = """
                    Acct:**737
                    Amt:N50.00DR
                    Desc:IFO NIBSS DIRECT DEBIT Mob Other Bank Trf # 70123
                    04-JAN-26 17:46
                    Help:07007730000
                    Bal:N252.28
                    Buy Airtime, Dial *773*Amount#
                """.trimIndent(),
                sender = "Jaiz",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "IFO NIBSS DIRECT DEBIT Mob Other Bank Trf # 70123",
                    accountLast4 = "737",
                    balance = BigDecimal("252.28")
                )
            ),
            ParserTestCase(
                name = "Credit transaction (INCOME)",
                message = """
                    Acct:**737
                    Amt:N100.00CR
                    Desc:OPAY DIGITAL SERVICES LIMITED
                    18-JAN-26 20:11
                    Help:07007730000
                    Bal:N352.28
                    Buy Airtime, Dial *773*Amount#
                """.trimIndent(),
                sender = "Jaiz",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "OPAY DIGITAL SERVICES LIMITED",
                    accountLast4 = "737",
                    balance = BigDecimal("352.28")
                )
            )
        )

        val handleCases = listOf(
            "Jaiz" to true,
            "JAIZ" to true,
            "AD-JAIZ-S" to true,
            "Opay" to false,
            "KEYSTONE" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Jaiz Bank Parser Suite"
        )
    }

    @Test
    fun `empty Desc field yields a null merchant, not the next line`() {
        val parser = JaizBankParser()
        val message = """
            Acct:**737
            Amt:N50.00DR
            Desc:
            04-JAN-26 17:46
            Bal:N252.28
        """.trimIndent()

        val parsed = parser.parse(message, "Jaiz", 0L)
        Assertions.assertNotNull(parsed, "Message should still parse")
        Assertions.assertNull(
            parsed!!.merchant,
            "An empty Desc: must not capture the following date/Bal: line"
        )
    }
}
