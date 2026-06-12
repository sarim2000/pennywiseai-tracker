import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.KeystoneBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class KeystoneBankParserTest {

    @TestFactory
    fun `keystone bank parser handles common cases`(): List<DynamicTest> {
        val parser = KeystoneBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Debit transaction (EXPENSE)",
                message = """
                    Debit!
                    Acct:602****370
                    Amt:NGN-57,000.00
                    Desc:TRF IFO WILSPLENDOR LIMITED FRM OBA
                    Date:26-05-2026 0:0
                    Bal:NGN1,929.24
                    Download Keymobile bit.ly/31MJj1s
                """.trimIndent(),
                sender = "KEYSTONE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("57000.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "TRF IFO WILSPLENDOR LIMITED FRM OBA",
                    accountLast4 = "370",
                    balance = BigDecimal("1929.24")
                )
            ),
            ParserTestCase(
                name = "Credit transaction (INCOME)",
                message = """
                    Credit!
                    Acct:602****370
                    Amt:NGN3,000.00
                    Desc:NXG :MOBILE TRF TO KSB  OBAYUWANA C
                    Date:26-05-2026 0:0
                    Bal:NGN59,030.84
                    Download Keymobile bit.ly/31MJj1s
                """.trimIndent(),
                sender = "KEYSTONE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "NXG :MOBILE TRF TO KSB  OBAYUWANA C",
                    accountLast4 = "370",
                    balance = BigDecimal("59030.84")
                )
            )
        )

        val handleCases = listOf(
            "KEYSTONE" to true,
            "AD-KEYSTONE-S" to true,
            "ZENITHBANK" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Keystone Bank Parser Suite"
        )
    }

    @Test
    fun `empty Desc field yields a null merchant, not the next line`() {
        val parser = KeystoneBankParser()
        val message = """
            Debit!
            Acct:602****370
            Amt:NGN-57,000.00
            Desc:
            Date:26-05-2026 0:0
            Bal:NGN1,929.24
        """.trimIndent()

        val parsed = parser.parse(message, "KEYSTONE", 0L)
        Assertions.assertNotNull(parsed, "Message should still parse")
        Assertions.assertNull(
            parsed!!.merchant,
            "An empty Desc: must not capture the following Date:/Bal: line"
        )
    }
}
