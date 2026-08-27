import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.ApolloParser
import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class ApolloParserTest {

    @TestFactory
    fun `apollo parser handles credit and debit`(): List<DynamicTest> {
        val parser = ApolloParser()

        ParserTestUtils.printTestHeader(
            parserName = "Apollo",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Credit transaction with counterparty",
                message = "Dear John, your account 1*34 was credited with ETB 550.00 by John Doe. Available Balance: ETB 12,357.73. Receipt: [receipt link] For help, call 0000 (24/7 Toll-Free).",
                sender = "apollo",
                expected = ExpectedTransaction(
                    amount = BigDecimal("550.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "John Doe",
                    // Mobile wallet: consolidated to the service-level balance, no per-account number.
                    accountLast4 = null,
                    balance = BigDecimal("12357.73")
                )
            ),
            ParserTestCase(
                name = "Debit transaction",
                message = "Dear John, your account 1*34 was debited with ETB 7,000.00. Available Balance: ETB 5,595.43. Receipt: [receipt link] For help, call 0000 (24/7 Toll-Free).",
                sender = "apollo",
                expected = ExpectedTransaction(
                    amount = BigDecimal("7000.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = null,
                    balance = BigDecimal("5595.43")
                )
            )
        )

        val handleChecks = listOf(
            "apollo" to true,
            "APOLLO" to true,
            "AB-APOLLO-S" to true,
            "BOA" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Apollo Parser"
        )
    }

    @Test
    fun `factory routes Apollo senders to ApolloParser`() {
        Assertions.assertTrue(
            BankParserFactory.getParser("apollo") is ApolloParser,
            "sender 'apollo' should route to ApolloParser"
        )
        Assertions.assertTrue(
            BankParserFactory.getParser("AB-APOLLO-S") is ApolloParser,
            "DLT sender 'AB-APOLLO-S' should route to ApolloParser"
        )
    }
}
