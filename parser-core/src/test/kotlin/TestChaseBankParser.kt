import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.ChaseBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class ChaseBankParserTest {

    @TestFactory
    fun `chase parser covers representative scenarios`(): List<DynamicTest> {
        val parser = ChaseBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Credit card transaction",
                message = "Rapid Rewards Plus Visa: You made a \$9.17 transaction with TACO BELL 740493 on Mar 17, 2026 at 1:56 PM ET.",
                sender = "24273",
                expected = ExpectedTransaction(
                    amount = BigDecimal("9.17"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "TACO BELL 740493",
                    isFromCard = true
                )
            )
        )

        val handleCases = listOf(
            "24273" to true,
            "CHASE" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Chase Bank Parser Suite"
        )
    }
}
