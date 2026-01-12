import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.FederalBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class FederalBankOutgoingParserTest {

    @TestFactory
    fun `federal bank parser handles outgoing has received patterns`(): List<DynamicTest> {
        val parser = FederalBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Digital Gold outgoing transfer - should be INVESTMENT",
                message = "Digital Gold India Private Limited has received Rs 21.00 from your A/c 7990 via NEFT on 10-Jan-2026 12:06:17. Ref no. FBBT260103879100 - Federal Bank",
                sender = "AD-FEDBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("21.00"),
                    currency = "INR",
                    type = TransactionType.INVESTMENT,
                    merchant = "Digital Gold India"  // "Private Limited" suffix is cleaned
                )
            ),
            ParserTestCase(
                name = "Mutual Fund outgoing transfer - should be INVESTMENT",
                message = "Nippon India Mutual Fund has received Rs 1000.00 from your A/c 3363 via NEFT on 02-Jan-2026 06:50:32. Ref no. FBBT260023158681 - Federal Bank",
                sender = "VM-FEDBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "INR",
                    type = TransactionType.INVESTMENT,
                    merchant = "Nippon India Mutual Fund"
                )
            ),
            ParserTestCase(
                name = "Credit card bill payment - should be TRANSFER",
                message = "Yay! We've received your payment of ₹12,056.72 towards your Scapia Federal credit card. -Federal Bank",
                sender = "CP-FEDSCP-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12056.72"),
                    currency = "INR",
                    type = TransactionType.TRANSFER
                )
            )
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            suiteName = "Federal Bank Outgoing Parser Tests"
        )
    }
}
