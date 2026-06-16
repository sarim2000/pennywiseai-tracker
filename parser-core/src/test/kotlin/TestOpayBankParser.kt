import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.OpayBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class OpayBankParserTest {

    @TestFactory
    fun `opay parser handles common cases`(): List<DynamicTest> {
        val parser = OpayBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Card Payment via POS (EXPENSE)",
                message = "Dear OPay user, N2,300.00 has been debited for Card Payment via POS on 14-May-2026 19:28.",
                sender = "Opay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2300.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "Card Payment via POS"
                )
            ),
            ParserTestCase(
                name = "Transfer via E-Channel (EXPENSE)",
                message = "Dear OPay user, N150.00 has been debited for Transfer via E-Channel on 12-Jun-2026 16:28.",
                sender = "Opay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "Transfer via E-Channel"
                )
            )
        )

        val handleCases = listOf(
            "Opay" to true,
            "OPAY" to true,
            "AD-OPAY-S" to true,
            "Jaiz" to false,
            "KEYSTONE" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Opay Parser Suite"
        )
    }

    @Test
    fun `credited message is classified as income best-effort`() {
        val parser = OpayBankParser()
        val message = "Dear OPay user, N500.00 has been credited for Transfer via E-Channel on 12-Jun-2026 16:28."

        val parsed = parser.parse(message, "Opay", 0L)
        Assertions.assertNotNull(parsed, "Credit message should parse")
        Assertions.assertEquals(TransactionType.INCOME, parsed!!.type)
        Assertions.assertEquals(BigDecimal("500.00"), parsed.amount)
    }

    @Test
    fun `merchant containing " on " is not truncated at the first occurrence`() {
        val parser = OpayBankParser()
        // The purpose itself contains " on " — the date-anchored regex must capture the
        // whole purpose, not stop at "Payment".
        val message = "Dear OPay user, N500.00 has been debited for Payment on Account via POS on 14-May-2026 19:28."

        val parsed = parser.parse(message, "Opay", 0L)
        Assertions.assertNotNull(parsed)
        Assertions.assertEquals("Payment on Account via POS", parsed!!.merchant)
    }

    @Test
    fun `OTP message is rejected`() {
        val parser = OpayBankParser()
        val message = "Dear OPay user, your OTP is 123456. Do not share it with anyone."

        Assertions.assertNull(parser.parse(message, "Opay", 0L))
    }
}
