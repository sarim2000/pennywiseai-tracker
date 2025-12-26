import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.CharlesSchwabParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class CharlesSchwabParserTest {

    @TestFactory
    fun `charles schwab parser handles primary formats`(): List<DynamicTest> {
        val parser = CharlesSchwabParser()

        ParserTestUtils.printTestHeader(
            parserName = "Charles Schwab",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit card transaction",
                message = "A $7.44 debit card transaction was debited from account ending 1234. Reply STOP to end Schwab Text Alerts.",
                sender = "SCHWAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("7.44"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Another debit card transaction",
                message = "A $10.00 debit card transaction was debited from account ending 5678. Reply STOP to end Schwab Text Alerts.",
                sender = "SCHWAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ATM transaction",
                message = "A $139.71 ATM transaction was debited from account ending 9012. Reply STOP to end Schwab Text Alerts.",
                sender = "SCHWAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("139.71"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9012",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ACH transaction",
                message = "A $22.07 ACH was debited from account ending 3456. Reply STOP to end Schwab Text Alerts.",
                sender = "SCHWAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("22.07"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3456",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Alternative sender format",
                message = "A $50.25 debit card transaction was debited from account ending 7890. Reply STOP to end Schwab Text Alerts.",
                sender = "CHARLES SCHWAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.25"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "7890",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Schwab Bank sender",
                message = "A $15.99 debit card transaction was debited from account ending 2468. Reply STOP to end Schwab Text Alerts.",
                sender = "SCHWAB BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "2468",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Numeric sender ID",
                message = "A $75.00 debit card transaction was debited from account ending 1357. Reply STOP to end Schwab Text Alerts.",
                sender = "24465",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1357",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "EUR transaction",
                message = "A €25.50 debit card transaction was debited from account ending 2468. Reply STOP to end Schwab Text Alerts.",
                sender = "SCHWAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.50"),
                    currency = "EUR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "2468",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "GBP ATM transaction",
                message = "A £150.00 ATM transaction was debited from account ending 9876. Reply STOP to end Schwab Text Alerts.",
                sender = "SCHWAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "GBP",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9876",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "CAD transaction with currency code",
                message = "A CAD 85.75 debit card transaction was debited from account ending 5432. Reply STOP to end Schwab Text Alerts.",
                sender = "SCHWAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("85.75"),
                    currency = "CAD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5432",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "AED transaction with currency code",
                message = "A AED 120.50 debit card transaction was debited from account ending 9876. Reply STOP to end Schwab Text Alerts.",
                sender = "SCHWAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("120.50"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9876",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "THB transaction with symbol",
                message = "A ฿500.00 ATM transaction was debited from account ending 1111. Reply STOP to end Schwab Text Alerts.",
                sender = "SCHWAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1111",
                    isFromCard = true
                )
            )
        )

        val handleChecks = listOf(
            "SCHWAB" to true,
            "CHARLES SCHWAB" to true,
            "SCHWAB BANK" to true,
            "24465" to true,
            "US-SCHWAB-A" to true,
            "UNKNOWN" to false,
            "HDFC" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Charles Schwab Parser"
        )


    }

    @TestFactory
    fun `factory resolves charles schwab`(): List<DynamicTest> {
        val cases = listOf(
            com.pennywiseai.parser.core.test.SimpleTestCase(
                bankName = "Charles Schwab",
                sender = "SCHWAB",
                currency = "USD",
                message = "A $25.00 debit card transaction was debited from account ending 1234. Reply STOP to end Schwab Text Alerts.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    isFromCard = true
                ),
                shouldHandle = true
            ),
            com.pennywiseai.parser.core.test.SimpleTestCase(
                bankName = "Charles Schwab",
                sender = "CHARLES SCHWAB",
                currency = "USD",
                message = "A $100.00 ACH was debited from account ending 5678. Reply STOP to end Schwab Text Alerts.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    isFromCard = false
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Charles Schwab Factory Tests")

    }
}