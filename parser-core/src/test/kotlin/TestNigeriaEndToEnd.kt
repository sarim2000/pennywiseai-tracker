import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BankParserFactory
import org.junit.jupiter.api.*
import java.math.BigDecimal

/**
 * End-to-end check for the Nigerian banks: goes through BankParserFactory exactly
 * the way the app does when an SMS arrives, rather than calling a parser directly.
 *
 * This is the test that proves the wiring — registration, sender routing, and
 * content-aware dispatch — not just the individual regexes.
 *
 * All data here is fictional.
 */
class NigeriaEndToEndTest {

    private data class Case(
        val bank: String,
        val sender: String,
        val message: String,
        val amount: String,
        val type: TransactionType,
        val balance: String?,
        val last4: String?
    )

    private val cases = listOf(
        Case(
            "GTBank", "GTBank",
            """
            Acct:******4321
            Amt:NGN15,000.00 DR
            Desc:OUTWARD TRANSFER TO OPAY - JANE DOE
            Bal:NGN20,000.00
            Date:2026-08-23 9:36AM
            """.trimIndent(),
            "15000.00", TransactionType.EXPENSE, "20000.00", "4321"
        ),
        Case(
            "GTBank", "GTBank",
            """
            Acct:******4321
            Amt:NGN1,000.00 CR
            Desc:VIA GTWORLD OWN ACCOUNT TRANSFER
            Bal:NGN40,000.00
            Date:2026-08-22 3:46PM
            """.trimIndent(),
            "1000.00", TransactionType.INCOME, "40000.00", "4321"
        ),
        Case(
            "Standard Chartered Bank Nigeria", "StanChart",
            "Credit Alert! Acct:xxxxxx1234, Amt:NGN1000.00, Desc:JANE DOE, Date:2026-08-24, Bal:NGN1500000.00",
            "1000.00", TransactionType.INCOME, "1500000.00", "1234"
        ),
        Case(
            "Standard Chartered Bank Nigeria", "StanChart",
            "Debit Alert! Acct:xxxxxx1234, Amt:NGN1000000.00, Desc:JANE DOE LENDING, Date:2026-08-14, Bal:NGN1400000.00",
            "1000000.00", TransactionType.EXPENSE, "1400000.00", "1234"
        ),
        Case(
            "VFD Microfinance Bank", "VFD",
            """
            Acct: xxx901
            Amt: N11,000.00 DR
            Date: 11-MAY-2026 21:53:32
            Chgs: N25.00 (COMM VAT)
            Desc: Fruits/ To JANE DOE
            Balance:N30,000.00
            """.trimIndent(),
            "11000.00", TransactionType.EXPENSE, "30000.00", "901"
        ),
        Case(
            "VFD Microfinance Bank", "VFD",
            """
            Acct: xxx901
            Amt: N197,500.00 CR
            Date: 29-APR-2026 16:43:56
            Chgs: N0.00 (COMM VAT)
            Desc: Salary to Jane Doe
            Balance:N200,000.00
            """.trimIndent(),
            "150000.00".let { "197500.00" }, TransactionType.INCOME, "200000.00", "901"
        ),
        Case(
            "Access Bank", "AccessBank",
            """
            Debit
            Amt:NGN20,400.00
            Acc:146******325
            Desc:MOBILE TRF TO POS
            Date:09/06/2026
            Avail Bal:NGN224,408.56
            """.trimIndent(),
            "20400.00", TransactionType.EXPENSE, "224408.56", "325"
        )
    )

    @TestFactory
    fun `every nigerian sms routes through the factory to the right bank`(): List<DynamicTest> =
        cases.mapIndexed { i, c ->
            DynamicTest.dynamicTest("${c.bank} #$i -> ${c.type}") {
                val parsed = BankParserFactory.parse(c.message, c.sender, 0L)

                Assertions.assertNotNull(parsed, "Factory returned null — SMS would be IGNORED by the app")
                Assertions.assertEquals(c.bank, parsed!!.bankName, "Routed to the wrong bank")
                Assertions.assertEquals("NGN", parsed.currency, "Currency must be NGN")
                Assertions.assertEquals(BigDecimal(c.amount), parsed.amount, "Amount mismatch")
                Assertions.assertEquals(c.type, parsed.type, "Debit/credit direction wrong")
                c.balance?.let {
                    Assertions.assertEquals(BigDecimal(it), parsed.balance, "Balance mismatch")
                }
                c.last4?.let {
                    Assertions.assertEquals(it, parsed.accountLast4, "Account last4 mismatch")
                }
            }
        }

    @Test
    fun `unknown nigerian sender is not silently claimed by another parser`() {
        val msg = "Acct:******4321\nAmt:NGN500.00 DR\nDesc:TEST\nBal:NGN1,000.00\nDate:2026-08-23 9:36AM"
        val parsed = BankParserFactory.parse(msg, "SOME-UNKNOWN-BANK", 0L)
        Assertions.assertNull(parsed, "No parser should claim an unknown sender")
    }

    @Test
    fun `nigerian amounts never mix with other currencies`() {
        // Hard Constraint #2: a NGN transaction must never be tagged another currency.
        cases.forEach { c ->
            val parsed = BankParserFactory.parse(c.message, c.sender, 0L)
            Assertions.assertEquals("NGN", parsed?.currency, "${c.bank} produced a non-NGN currency")
        }
    }
}
