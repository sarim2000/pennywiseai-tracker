import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BankOfAbyssiniaParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class BankOfAbyssiniaParserTest {

    @TestFactory
    fun `bank of abyssinia parser handles credit and debit`(): List<DynamicTest> {
        val parser = BankOfAbyssiniaParser()

        ParserTestUtils.printTestHeader(
            parserName = "Bank of Abyssinia",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Credit transaction with counterparty",
                message = "Dear John, your account 1*34 was credited with ETB 10,000.00 by John Doe. Available Balance: ETB 10,276.49. Receipt: [receipt link] For help, call 0000 (24/7 Toll-Free). Bank of Abyssinia.",
                sender = "BOA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "John Doe",
                    accountLast4 = "134",
                    balance = BigDecimal("10276.49")
                )
            ),
            ParserTestCase(
                name = "Debit transaction",
                message = "Dear John, your account 1*34 was debited with ETB 6,030.63. Available Balance: ETB 4,245.86. Receipt: [receipt link] For help, call 0000 (24/7 Toll-Free). Bank of Abyssinia.",
                sender = "BOA",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6030.63"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "134",
                    balance = BigDecimal("4245.86")
                )
            )
        )

        val handleChecks = listOf(
            "BOA" to true,
            "AB-BOA-S" to true,
            "CBE" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Bank of Abyssinia Parser"
        )
    }
}
