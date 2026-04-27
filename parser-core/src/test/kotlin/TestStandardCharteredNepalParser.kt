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
                name = "Generic debit fallback test",
                message = "NPR 520.00 debited",
                sender = "SCBNL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("520.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE
                )
            )
        )

        val handleChecks = listOf(
            "SCBNL" to true,
            "SCB_NP" to true,
            "SCBNP" to true,
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
