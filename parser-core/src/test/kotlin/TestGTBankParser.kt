import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.GTBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class GTBankParserTest {

    @TestFactory
    fun `gtbank parser handles common cases`(): List<DynamicTest> {
        val parser = GTBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Outward transfer debit (EXPENSE)",
                message = """
                    Acct:******4321
                    Amt:NGN15,000.00 DR
                    Desc:OUTWARD TRANSFER TO OPAY - NGOZI PATIENCE OKAFOR
                    Bal:NGN20,000.00
                    Date:2026-08-23 9:36AM
                """.trimIndent(),
                sender = "GTBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "OUTWARD TRANSFER TO OPAY - NGOZI PATIENCE OKAFOR",
                    accountLast4 = "4321",
                    balance = BigDecimal("20000.00")
                )
            ),
            ParserTestCase(
                name = "Own account transfer credit (INCOME)",
                message = """
                    Acct:******4321
                    Amt:NGN1,000.00 CR
                    Desc:VIA GTWORLD OWN ACCOUNT TRANSFER
                    Bal:NGN40,000.00
                    Date:2026-08-22 3:46PM
                """.trimIndent(),
                sender = "GTBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "VIA GTWORLD OWN ACCOUNT TRANSFER",
                    accountLast4 = "4321",
                    balance = BigDecimal("40000.00")
                )
            ),
            ParserTestCase(
                name = "Own account transfer debit, second account",
                message = """
                    Acct:******5678
                    Amt:NGN1,000.00 DR
                    Desc:VIA GTWORLD OWN ACCOUNT TRANSFER
                    Bal:NGN70,000.00
                    Date:2026-08-22 3:42PM
                """.trimIndent(),
                sender = "GTBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "VIA GTWORLD OWN ACCOUNT TRANSFER",
                    accountLast4 = "5678",
                    balance = BigDecimal("70000.00")
                )
            ),
            ParserTestCase(
                name = "Small commission fee debit (no decimals lost)",
                message = """
                    Acct:******4321
                    Amt:NGN25.00 DR
                    Desc:Commission on NIP Transfer
                    Bal:NGN19,975.00
                    Date:2026-08-23 9:36AM
                """.trimIndent(),
                sender = "GTBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "Commission on NIP Transfer",
                    accountLast4 = "4321",
                    balance = BigDecimal("19975.00")
                )
            ),
            ParserTestCase(
                name = "Commission fee on second account",
                message = """
                    Acct:******5678
                    Amt:NGN50.00 DR
                    Desc:Commission on NIP Transfer
                    Bal:NGN150,000.00
                    Date:2026-08-13 2:06PM
                """.trimIndent(),
                sender = "GTBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "Commission on NIP Transfer",
                    accountLast4 = "5678",
                    balance = BigDecimal("150000.00")
                )
            ),
            ParserTestCase(
                name = "Transfer to Opay with large balance",
                message = """
                    Acct:******5678
                    Amt:NGN4,200.00 DR
                    Desc:BREAD TO OPAY - HALIMA YETUNDE SALAMI
                    Bal:NGN400,000.00
                    Date:2026-08-11 10:25PM
                """.trimIndent(),
                sender = "GTBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4200.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "BREAD TO OPAY - HALIMA YETUNDE SALAMI",
                    accountLast4 = "5678",
                    balance = BigDecimal("400000.00")
                )
            )
        )

        val handleCases = listOf(
            "GTBank" to true,
            "GTB" to true,
            "GUARANTY TRUST" to true,
            "AD-GTBANK-S" to true,
            "AccessBank" to false,
            "OPAY" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "GTBank Parser Suite"
        )
    }

    @Test
    fun `empty Desc field yields a null merchant, not the next line`() {
        val parser = GTBankParser()
        val message = """
            Acct:******4321
            Amt:NGN15,000.00 DR
            Desc:
            Bal:NGN20,000.00
            Date:2026-08-23 9:36AM
        """.trimIndent()

        val parsed = parser.parse(message, "GTBank", 0L)
        Assertions.assertNotNull(parsed, "Message should still parse")
        Assertions.assertNull(
            parsed!!.merchant,
            "An empty Desc: must not capture the following Bal:/Date: line"
        )
    }

    @Test
    fun `OTP messages are not treated as transactions`() {
        val parser = GTBankParser()
        val message = "Your GTBank OTP is 123456. Do not share it with anyone."

        val parsed = parser.parse(message, "GTBank", 0L)
        Assertions.assertNull(parsed, "OTP messages must not parse as transactions")
    }
}
