import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.HDFCMutualFundParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class HDFCMutualFundParserTest {

    @TestFactory
    fun `hdfc mutual fund parser covers representative scenarios`(): List<DynamicTest> {
        val parser = HDFCMutualFundParser()

        val testCases = listOf(
            ParserTestCase(
                name = "SIP Purchase message",
                message = "Your SIP Purchase in Folio 38822412/82 under HDFC Mid Cap Fund-DG for Rs. 7,000.65 has been processed at the NAV of 224.603 for 31.169 units and 24-Feb-2026. Your smart statement https://shrtsms.in/HDFCMF/xYSrbW. Enter your PAN as the password. Sincerely, HDFCMF",
                sender = "AD-HDFCMF-AC",
                expected = ExpectedTransaction(
                    amount = BigDecimal("7000.65"),
                    currency = "INR",
                    type = TransactionType.INVESTMENT,
                    merchant = "HDFC Mid Cap Fund-DG"
                )
            )
        )

        val handleChecks = listOf(
            "AD-HDFCMF-AC" to true,
            "VM-HDFCMF" to true,
            "AD-HDFCBK" to false,
            "HDFCBANK" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "HDFC Mutual Fund Parser Suite"
        )
    }
}
