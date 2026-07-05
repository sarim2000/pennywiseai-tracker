import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BkashParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class BkashParserTest {

    @TestFactory
    fun `bkash parser handles mobile money flows`(): List<DynamicTest> {
        val parser = BkashParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Received money",
                message = "You have received Tk 6,400.00 from 01700000000. Fee Tk 0.00. Balance Tk 20,288.41. TrxID ABC1234XYZ at 26/05/2026 10:58.",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6400.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    reference = "ABC1234XYZ",
                    balance = BigDecimal("20288.41")
                )
            ),
            ParserTestCase(
                name = "Payment",
                message = "Payment of Tk 20.00 to 01800000000 is successful. Balance Tk 20,268.41. TrxID DEF5678UVW at 26/05/2026 15:07",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    reference = "DEF5678UVW",
                    balance = BigDecimal("20268.41")
                )
            ),
            ParserTestCase(
                name = "Cash In",
                message = "Cash In Tk 500.00 from 01900000000 successful. Fee Tk 0.00. Balance Tk 506.91. TrxID GHI9012RST at 29/05/2026 19:00. Download App: https://bKa.sh/8app",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    reference = "GHI9012RST",
                    balance = BigDecimal("506.91")
                )
            ),
            ParserTestCase(
                name = "Send Money",
                message = "Send Money Tk 0.20 to 01600000000 successful. Ref 2. Fee Tk 0.00. Balance Tk 0.08. TrxID JKL3456MNO at 07/06/2026 22:45.",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.20"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    reference = "JKL3456MNO",
                    balance = BigDecimal("0.08")
                )
            )
        )

        val handleChecks = listOf(
            "bKash" to true,
            "BKASH" to true,
            "HDFCBK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "bKash Parser"
        )
    }
}
