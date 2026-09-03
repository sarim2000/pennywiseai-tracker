import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.D360BankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class D360BankParserTest {

    @TestFactory
    fun `d360 parser covers representative scenarios`(): List<DynamicTest> {
        val parser = D360BankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "International online purchase (foreign amount, SAR conversion)",
                message = """
                    International Online Purchase
                    Amount: TRY 200.00 (SAR 12.34)
                    Card: *0007 - VISA (Ecommerce)
                    Fee: SAR 0.00
                    At: SYNTHETIC MERCHANT
                    Account number: *0008
                    Country: Turkey
                    On: 2026-07-08 22:08
                """.trimIndent(),
                sender = "D360Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MERCHANT",
                    accountLast4 = "0007",
                    isFromCard = true
                ),
                description = "Foreign purchase: record the parenthetical SAR amount, not the TRY figure or the Fee line."
            ),
            ParserTestCase(
                name = "International ATM withdrawal (foreign amount, SAR conversion)",
                message = """
                    International ATM Withdrawal
                    Amount: TRY 300.00 (SAR 23.45)
                    Card: *0007 - VISA
                    Fee: 0.00
                    At: SYNTHETIC ATM
                    Country: Turkey
                    On: 2026-07-08 15:33
                """.trimIndent(),
                sender = "D360BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0007",
                    isFromCard = true
                ),
                description = "ATM withdrawal is an expense; SAR conversion is preferred over the TRY amount."
            ),
            ParserTestCase(
                name = "Incoming transfer (local SAR)",
                message = """
                    Incoming Transfer: SYNTHETIC BANK
                    Amount: SAR 45.67
                    From: *0008
                    IBAN: SA00
                    at: 2026-07-08 18:14
                """.trimIndent(),
                sender = "D360BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC BANK"
                ),
                description = "Incoming transfer is income; merchant is the counterparty on the title line."
            ),
            ParserTestCase(
                name = "Outgoing transfer (local SAR)",
                message = """
                    Outgoing Transfer: SYNTHETIC BANK
                    Amount: SAR 56.78
                    To: *0008
                    IBAN: SA00
                    at: 2026-07-08 19:20
                """.trimIndent(),
                sender = "D360BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.78"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC BANK"
                ),
                description = "Outgoing transfer is an expense; the 'at:' datetime line must not be picked as merchant."
            ),
            ParserTestCase(
                name = "Merchant name containing a promo substring still parses",
                message = """
                    International Online Purchase
                    Amount: SAR 67.89
                    Card: *0007 - VISA (Ecommerce)
                    Fee: SAR 0.00
                    At: SYNTHETIC WHOLESALE MARKET
                    Account number: *0008
                    Country: Saudi Arabia
                    On: 2026-07-08 12:00
                """.trimIndent(),
                sender = "D360BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("67.89"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC WHOLESALE MARKET",
                    isFromCard = true
                ),
                description = "'sale' inside 'WHOLESALE' must not trip the promo filter (word-boundary match)."
            ),
            ParserTestCase(
                name = "Promotional SMS is not a transaction",
                message = "Exclusive SAR cashback offer on all your transfers this weekend! Amount limits apply.",
                sender = "D360BANK",
                shouldParse = false,
                description = "Promo messages that mention transaction words must be rejected."
            ),
            ParserTestCase(
                name = "OTP is not a transaction",
                message = "Your D360 Bank verification code is <CODE>. Do not share it with anyone.",
                sender = "D360BANK",
                shouldParse = false,
                description = "Verification codes must never be parsed as transactions."
            ),
            ParserTestCase(
                name = "Number-last SAR settlement is preferred",
                message = "International Online Purchase\nAmount: USD 200.00 (12.34 SAR)\nCard: *0007 - VISA\nFee: SAR 0.50\nAt: SYNTHETIC STORE",
                sender = "D360BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC STORE",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Arabic PIN authentication is ignored",
                message = "الرقم السري لتأكيد شراء عبر الانترنت: <CODE>\nAmount: SAR 12.34",
                sender = "D360BANK",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined purchase is ignored",
                message = "International Online Purchase Declined\nAmount: SAR 12.34\nCard: *0007",
                sender = "D360BANK",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Refund naming a previously rejected purchase is ignored",
                message = "Refund for previously rejected purchase\nAmount: SAR 14.56\nCard: *0007",
                sender = "D360Bank",
                shouldParse = false,
                description = "Refund *of* a failed payment and a refund that itself failed are the same words reordered, so the guard treats any failure word as a failure. A dropped refund is recoverable; invented income is not."
            )
        )

        val handleCases = listOf(
            "D360Bank" to true,
            "D360BANK" to true,
            "AD-D360BANK" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "D360 Bank Parser Suite"
        )
    }
}
