import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.StandardCharteredNepalParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class StandardCharteredNepalParserTest {

    @TestFactory
    fun `standard chartered nepal parser handles messages`(): List<DynamicTest> {
        val parser = StandardCharteredNepalParser()

        ParserTestUtils.printTestHeader(
            parserName = "Standard Chartered Nepal",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit - account withdrawal",
                message = "NPR 95,000.00 has been debited from your account 3301.",
                sender = "SC_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("95000.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3301"
                )
            ),
            ParserTestCase(
                name = "Credit - deposit",
                message = "NPR 80,000.00 has been deposited into your account 1234.",
                sender = "SCB_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("80000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234"
                )
            )
        )

        val handleChecks = listOf(
            "SC_ALERT" to true,
            "SCB_ALERT" to true,
            "SCBNL" to true,
            "SCB_NP" to true,
            "SCBNP" to true,
            "SCBANK" to false,  // India/Pakistan parser, not Nepal
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Standard Chartered Nepal Parser"
        )
    }
}
