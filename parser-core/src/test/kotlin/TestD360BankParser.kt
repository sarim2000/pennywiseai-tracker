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
                    Amount: TRY 342.00 (SAR 27.51)
                    Card: *1234 - VISA (Ecommerce)
                    Fee: SAR 0.00
                    At: FEED ME
                    Account number: *5678
                    Country: Turkey
                    On: 2026-07-08 22:08
                """.trimIndent(),
                sender = "D360Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("27.51"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "FEED ME",
                    accountLast4 = "1234",
                    isFromCard = true
                ),
                description = "Foreign purchase: record the parenthetical SAR amount, not the TRY figure or the Fee line."
            ),
            ParserTestCase(
                name = "International ATM withdrawal (foreign amount, SAR conversion)",
                message = """
                    International ATM Withdrawal
                    Amount: TRY 219.98 (SAR 17.71)
                    Card: *4321 - VISA
                    Fee: 0.00
                    At: CITY,TR
                    Country: Turkey
                    On: 2026-07-08 15:33
                """.trimIndent(),
                sender = "D360BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("17.71"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4321",
                    isFromCard = true
                ),
                description = "ATM withdrawal is an expense; SAR conversion is preferred over the TRY amount."
            ),
            ParserTestCase(
                name = "Incoming transfer (local SAR)",
                message = """
                    Incoming Transfer: SAMPLE BANK
                    Amount: SAR 250.00
                    From: *1234
                    IBAN: SA00
                    at: 2026-07-08 18:14
                """.trimIndent(),
                sender = "D360BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SAMPLE BANK"
                ),
                description = "Incoming transfer is income; merchant is the counterparty on the title line."
            ),
            ParserTestCase(
                name = "Outgoing transfer (local SAR)",
                message = """
                    Outgoing Transfer: SAMPLE BANK
                    Amount: SAR 500.00
                    To: *9876
                    IBAN: SA00
                    at: 2026-07-08 19:20
                """.trimIndent(),
                sender = "D360BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SAMPLE BANK"
                ),
                description = "Outgoing transfer is an expense; the 'at:' datetime line must not be picked as merchant."
            ),
            ParserTestCase(
                name = "Merchant name containing a promo substring still parses",
                message = """
                    International Online Purchase
                    Amount: SAR 88.00
                    Card: *1234 - VISA (Ecommerce)
                    Fee: SAR 0.00
                    At: WHOLESALE MARKET
                    Account number: *5678
                    Country: Saudi Arabia
                    On: 2026-07-08 12:00
                """.trimIndent(),
                sender = "D360BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("88.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "WHOLESALE MARKET",
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
                message = "Your D360 Bank verification code is 123456. Do not share it with anyone.",
                sender = "D360BANK",
                shouldParse = false,
                description = "Verification codes must never be parsed as transactions."
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
