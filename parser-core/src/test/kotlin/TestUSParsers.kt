import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class USParsersTest {

    @TestFactory
    fun `factory resolves US parsers and parses sample messages`(): List<DynamicTest> {
        val citiMessage =
            "Citi Alert: A \$3.01 transaction was made at BP#1234E on card ending in 1234. View details at citi.com/citimobileapp"
        val discoverMessage =
            "Discover Card Alert: A transaction of \$25.00 at WWW.XXX.ORG on February 21, 2025. No Action needed. See it at https://app.discover.com/ACTVT. Text STOP to end"
        val charlesSchwabMessage =
            "A \$7.44 debit card transaction was debited from account ending 1234. Reply STOP to end Schwab Text Alerts."
        val charlesSchwabACHMessage =
            "A \$22.07 ACH was debited from account ending 3456. Reply STOP to end Schwab Text Alerts."

        ParserTestUtils.printSectionHeader("US Bank Parser Factory Tests")

        val factoryCases = listOf(
            SimpleTestCase(
                bankName = "Citi Bank",
                sender = "692484",
                currency = "USD",
                message = citiMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("3.01"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "BP#1234E",
                    accountLast4 = "1234"
                ),
                shouldHandle = true,
                description = "Citi Bank primary sender"
            ),
            SimpleTestCase(
                bankName = "Discover Card",
                sender = "347268",
                currency = "USD",
                message = discoverMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "WWW.XXX.ORG",
                    reference = "February 21, 2025"
                ),
                shouldHandle = true,
                description = "Discover Card primary sender"
            ),
            SimpleTestCase(
                bankName = "Charles Schwab",
                sender = "SCHWAB",
                currency = "USD",
                message = charlesSchwabMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("7.44"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    isFromCard = true
                ),
                shouldHandle = true,
                description = "Charles Schwab primary sender"
            ),
            SimpleTestCase(
                bankName = "Charles Schwab",
                sender = "24465",
                currency = "USD",
                message = charlesSchwabACHMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("22.07"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3456",
                    isFromCard = false
                ),
                shouldHandle = true,
                description = "Charles Schwab numeric sender"
            ),
            SimpleTestCase(
                bankName = "Citi Bank",
                sender = "CITI",
                currency = "USD",
                message = citiMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("3.01"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "BP#1234E",
                    accountLast4 = "1234"
                ),
                shouldHandle = true,
                description = "Citi Bank alphanumeric sender"
            ),
            SimpleTestCase(
                bankName = "Discover Card",
                sender = "DISCOVER",
                currency = "USD",
                message = discoverMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "WWW.XXX.ORG",
                    reference = "February 21, 2025"
                ),
                shouldHandle = true,
                description = "Discover Card alphanumeric sender"
            ),
            SimpleTestCase(
                bankName = "Charles Schwab",
                sender = "CHARLES SCHWAB",
                currency = "USD",
                message = charlesSchwabMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("7.44"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    isFromCard = true
                ),
                shouldHandle = true,
                description = "Charles Schwab alphanumeric sender"
            )
        )

        return ParserTestUtils.runFactoryTestSuite(factoryCases, "US Parser Factory Coverage")


    }
}
