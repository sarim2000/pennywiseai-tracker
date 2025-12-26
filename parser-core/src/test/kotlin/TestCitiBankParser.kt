import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.CitiBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class CitiBankParserTest {

    @TestFactory
    fun `citi parser handles common alerts`(): List<DynamicTest> {
        val parser = CitiBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Citi Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Standard transaction",
                message = "Citi Alert: A $3.01 transaction was made at BP#1234E  on card ending in 1234. View details at citi.com/citimobileapp",
                sender = "692484",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3.01"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "BP#1234E",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Card not present",
                message = "Citi Alert: Card ending in 1234 was not present for a $506.39 transaction at WWW Google C. View at citi.com/citimobileapp",
                sender = "CITI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("506.39"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "WWW Google C",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Alternative sender",
                message = "Citi Alert: A $150.00 transaction was made at AMAZON.COM on card ending in 5678. View details at citi.com/citimobileapp",
                sender = "US-CITI-A",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678"
                )
            )
        )

        val handleChecks = listOf(
            "692484" to true,
            "CITI" to true,
            "US-CITI-A" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Citi Bank Parser"
        )


    }
}
