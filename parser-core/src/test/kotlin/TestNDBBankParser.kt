import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.NDBBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class NDBBankParserTest {

    @TestFactory
    fun `ndb parser handles pos debits and cefts transfers`(): List<DynamicTest> {
        val parser = NDBBankParser()

        // Account digits below are synthetic (X-prefix + dummy last-4) — real account
        // numbers are PII and must never be committed. Format matches real NDB SMS.
        val testCases = listOf(
            ParserTestCase(
                name = "POS debit with merchant",
                message = "LKR 12,724.00 debited from AC XXXXXXXX7497 as POS TXN on 30 Jul 2026 22:28 at COLOMBAY COLOMBO 2. Avl Bal 2,582.08 Call 94112448888 for info",
                sender = "NDB ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12724.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "COLOMBAY COLOMBO 2",
                    accountLast4 = "7497",
                    balance = BigDecimal("2582.08")
                )
            ),
            ParserTestCase(
                name = "CEFTS outward transfer (non-standard balance grouping tolerated)",
                message = "LKR 50,000.00 debited from AC XXXXXXXX1097 on 02 Jul 2026 15:52 as CEFTS Outward Transfer. Avl Bal 1,527,72.83 Call 94112448888 for info",
                sender = "NDB ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "CEFTS Outward Transfer",
                    accountLast4 = "1097",
                    balance = BigDecimal("152772.83")
                )
            ),
            ParserTestCase(
                name = "CEFTS inward transfer credit",
                message = "LKR 60,076.25 credited to AC XXXXXXXX1427 on 02 Jul 2026 21:53 as CEFTS Inward Transfer. Avl Bal 1,789,349.08 Call 94112448888 for info",
                sender = "NDB ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("60076.25"),
                    currency = "LKR",
                    type = TransactionType.INCOME,
                    merchant = "CEFTS Inward Transfer",
                    accountLast4 = "1427",
                    balance = BigDecimal("1789349.08")
                )
            )
        )

        val handleChecks = listOf(
            "NDB ALERT" to true,
            "NDBALERT" to true,
            "NDB-ALERT" to true,
            "NDB" to true,
            "INDBNK" to false,  // IndusInd — must not collide
            "NSB" to false,
            "HDFCBK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "National Development Bank Parser"
        )
    }
}
