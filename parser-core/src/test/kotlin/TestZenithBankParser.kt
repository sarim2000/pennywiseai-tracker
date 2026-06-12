import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.ZenithBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class ZenithBankParserTest {

    @TestFactory
    fun `zenith bank parser handles common cases`(): List<DynamicTest> {
        val parser = ZenithBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Debit transaction (EXPENSE)",
                message = """
                    Acct:421****577
                    DT:09/06/2026 03:23:17 PM
                    CIP/CR/MOB/OLIVER TWIST LOKOGO
                    DR Amt:650.00
                    Bal:289.69
                    Dial *966# for quick airtime/Data purchase
                """.trimIndent(),
                sender = "ZENITHBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("650.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "CIP/CR/MOB/OLIVER TWIST LOKOGO",
                    accountLast4 = "577",
                    balance = BigDecimal("289.69")
                )
            ),
            ParserTestCase(
                name = "Credit transaction (INCOME)",
                message = """
                    Acct:421****577
                    DT:24/05/2026 08:05:26 AM
                    ETI NXG  MOBILE TRF TO ZIB  CO
                    CR Amt:40,000.00
                    Bal:40,277.08
                    Dial *966# for quick airtime/Data purchase
                """.trimIndent(),
                sender = "ZENITHBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("40000.00"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "ETI NXG  MOBILE TRF TO ZIB  CO",
                    accountLast4 = "577",
                    balance = BigDecimal("40277.08")
                )
            ),
            ParserTestCase(
                name = "Debit without a narration line still parses amount/type/balance",
                message = """
                    Acct:421****577
                    DT:09/06/2026 03:23:17 PM
                    DR Amt:650.00
                    Bal:289.69
                    Dial *966# for quick airtime/Data purchase
                """.trimIndent(),
                sender = "ZENITHBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("650.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    // merchant intentionally unasserted here; the null guarantee is
                    // checked directly in `no narration line yields a null merchant`.
                    accountLast4 = "577",
                    balance = BigDecimal("289.69")
                )
            )
        )

        val handleCases = listOf(
            "ZENITHBANK" to true,
            "AD-ZENITH-S" to true,
            "AccessBank" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Zenith Bank Parser Suite"
        )
    }

    @Test
    fun `no narration line yields a null merchant, not the amount line`() {
        val parser = ZenithBankParser()
        val message = """
            Acct:421****577
            DT:09/06/2026 03:23:17 PM
            DR Amt:650.00
            Bal:289.69
            Dial *966# for quick airtime/Data purchase
        """.trimIndent()

        val parsed = parser.parse(message, "ZENITHBANK", 0L)
        Assertions.assertNotNull(parsed, "Message should still parse")
        Assertions.assertNull(
            parsed!!.merchant,
            "Without a narration line the merchant must be null, not the amount line"
        )
    }
}
