import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.DiscoverCardParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class DiscoverCardParserTest {

    @TestFactory
    fun `discover card parser handles primary formats`(): List<DynamicTest> {
        val parser = DiscoverCardParser()

        ParserTestUtils.printTestHeader(
            parserName = "Discover Card",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Standard transaction with date",
                message = "Discover Card Alert: A transaction of $25.00 at WWW.XXX.ORG on February 21, 2025. No Action needed. See it at https://app.discover.com/ACTVT. Text STOP to end",
                sender = "347268",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "WWW.XXX.ORG",
                    reference = "February 21, 2025"
                )
            ),
            ParserTestCase(
                name = "PayPal transaction",
                message = "Discover Card Alert: A transaction of $5.36 at PAYPAL *SParkXXX on July 20, 2025. No Action needed. See it at https://app.discover.com/ACTVT. Text STOP to end",
                sender = "DISCOVER",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.36"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "PAYPAL *SParkXXX",
                    reference = "July 20, 2025"
                )
            ),
            ParserTestCase(
                name = "Alternative sender format",
                message = "Discover Card Alert: A transaction of $99.99 at NETFLIX.COM on March 15, 2025. No Action needed. See it at https://app.discover.com/ACTVT. Text STOP to end",
                sender = "US-DISCOVER-A",
                expected = ExpectedTransaction(
                    amount = BigDecimal("99.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "NETFLIX.COM",
                    reference = "March 15, 2025"
                )
            ),
            ParserTestCase(
                name = "Different merchant format",
                message = "Discover Card Alert: A transaction of $42.50 at STARBUCKS STORE #1234 on April 10, 2025. No Action needed. See it at https://app.discover.com/ACTVT. Text STOP to end",
                sender = "347268",
                expected = ExpectedTransaction(
                    amount = BigDecimal("42.50"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "STARBUCKS STORE #1234",
                    reference = "April 10, 2025"
                )
            )
        )

        val handleChecks = listOf(
            "347268" to true,
            "DISCOVER" to true,
            "US-DISCOVER-A" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Discover Card Parser"
        )


    }
}
