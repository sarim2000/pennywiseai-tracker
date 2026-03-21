import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.MBankCZParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class MBankCZParserTest {

    @TestFactory
    fun `mbank cz parser covers representative scenarios`(): List<DynamicTest> {
        val parser = MBankCZParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Card payment at store",
                message = "Nová platba kartou\n100,00 CZK v obchodě POTRAVINY OLOMOUC 9.Podívejte se, zda je všechno ok.",
                sender = "mBank CZ",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "CZK",
                    type = TransactionType.EXPENSE,
                    merchant = "POTRAVINY OLOMOUC 9",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Incoming transfer",
                message = "Příchozí platba\n500,00 CZK od odesílatele JAN NOVAK. Podívejte se, zda je všechno ok.",
                sender = "mBank CZ",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "CZK",
                    type = TransactionType.INCOME,
                    merchant = "JAN NOVAK"
                )
            ),
            ParserTestCase(
                name = "Outgoing transfer",
                message = "Odchozí platba\n250,00 CZK na účet 123456789/6700. Podívejte se, zda je všechno ok.",
                sender = "mBank CZ",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "CZK",
                    type = TransactionType.EXPENSE,
                    merchant = "123456789/6700"
                )
            )
        )

        val handleCases = listOf(
            "mBank CZ" to true,
            "MBANK" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "mBank CZ Parser Suite"
        )
    }
}
