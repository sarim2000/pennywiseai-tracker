import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.VFDBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class VFDBankParserTest {

    @TestFactory
    fun `vfd bank parser handles common cases`(): List<DynamicTest> {
        val parser = VFDBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Credit with zero charges (INCOME)",
                message = """
                    Acct: xxx901
                    Amt: N500.00 CR
                    Date: 30-MAY-2026 20:51:54
                    Chgs: N0.00 (COMM VAT)
                    Desc: From Universal Savings Account
                    Balance:N3,000.00
                """.trimIndent(),
                sender = "VFD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "From Universal Savings Account",
                    accountLast4 = "901",
                    balance = BigDecimal("3000.00")
                )
            ),
            ParserTestCase(
                name = "Debit with zero charges, second account",
                message = """
                    Acct: xxx902
                    Amt: N500.00 DR
                    Date: 30-MAY-2026 20:51:54
                    Chgs: N0.00 (COMM VAT)
                    Desc: To Universal Savings Account
                    Balance:N900.00
                """.trimIndent(),
                sender = "VFD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "To Universal Savings Account",
                    accountLast4 = "902",
                    balance = BigDecimal("900.00")
                )
            ),
            ParserTestCase(
                name = "Debit with NON-ZERO charges - fee must not become the amount",
                message = """
                    Acct: xxx901
                    Amt: N11,000.00 DR
                    Date: 11-MAY-2026 21:53:32
                    Chgs: N25.00 (COMM VAT)
                    Desc: Fruits/ To JANE DOE
                    Balance:N30,000.00
                """.trimIndent(),
                sender = "VFD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("11000.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "Fruits/ To JANE DOE",
                    accountLast4 = "901",
                    balance = BigDecimal("30000.00")
                )
            ),
            ParserTestCase(
                name = "Large credit with reference digits in description",
                message = """
                    Acct: xxx901
                    Amt: N150,000.00 CR
                    Date: 29-APR-2026 16:43:56
                    Chgs: N0.00 (COMM VAT)
                    Desc: Car Fuel Gas Wi-fi to Jane Doe. 000000000000000000000000000000
                    Balance:N200,000.00
                """.trimIndent(),
                sender = "VFD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150000.00"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "Car Fuel Gas Wi-fi to Jane Doe. 000000000000000000000000000000",
                    accountLast4 = "901",
                    balance = BigDecimal("200000.00")
                )
            )
        )

        val handleCases = listOf(
            "VFD" to true,
            "VFDBank" to true,
            "AD-VFD-S" to true,
            "GTBank" to false,
            "AccessBank" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "VFD Bank Parser Suite"
        )
    }

    @Test
    fun `charges amount is never mistaken for the transaction amount or balance`() {
        val parser = VFDBankParser()
        // Fee (N25.00) appears BEFORE Desc and after Amt; balance is last.
        val message = """
            Acct: xxx901
            Amt: N11,000.00 DR
            Date: 11-MAY-2026 21:53:32
            Chgs: N25.00 (COMM VAT)
            Desc: Fruits/ To JANE DOE
            Balance:N30,000.00
        """.trimIndent()

        val parsed = parser.parse(message, "VFD", 0L)
        Assertions.assertNotNull(parsed)
        Assertions.assertEquals(
            BigDecimal("11000.00"), parsed!!.amount,
            "Amount must come from Amt:, never from Chgs:"
        )
        Assertions.assertEquals(
            BigDecimal("30000.00"), parsed.balance,
            "Balance must come from Balance:, never from Chgs:"
        )
    }

    @Test
    fun `alert with no Date line still parses`() {
        // Observed in the wild: some older VFD alerts omit the Date: line entirely.
        // The transaction date comes from the SMS timestamp, not this field, so the
        // message must still parse rather than being rejected.
        val parser = VFDBankParser()
        val message = """
            Acct: xxx903
            Amt: N200,000.00 CR
            Chgs: N0.00 (COMM VAT)
            Desc: Savings/ From Universal Savings Account
            Balance:N800,000.00
        """.trimIndent()

        val parsed = parser.parse(message, "VFD MFB", 0L)
        Assertions.assertNotNull(parsed, "A VFD alert without a Date: line must still parse")
        Assertions.assertEquals(BigDecimal("200000.00"), parsed!!.amount)
        Assertions.assertEquals(TransactionType.INCOME, parsed.type)
        Assertions.assertEquals(BigDecimal("800000.00"), parsed.balance)
        Assertions.assertEquals("903", parsed.accountLast4)
    }

    @Test
    fun `sender VFD MFB is handled`() {
        // The real on-device sender ID is "VFD MFB", not a bare "VFD".
        Assertions.assertTrue(VFDBankParser().canHandle("VFD MFB"))
    }

    @Test
    fun `empty Desc field yields a null merchant, not the next line`() {
        val parser = VFDBankParser()
        val message = """
            Acct: xxx901
            Amt: N500.00 CR
            Date: 30-MAY-2026 20:51:54
            Chgs: N0.00 (COMM VAT)
            Desc:
            Balance:N3,000.00
        """.trimIndent()

        val parsed = parser.parse(message, "VFD", 0L)
        Assertions.assertNotNull(parsed, "Message should still parse")
        Assertions.assertNull(
            parsed!!.merchant,
            "An empty Desc: must not capture the following Balance: line"
        )
    }
}
