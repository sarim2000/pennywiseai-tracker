import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.CentralBankOfIndiaParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class CentralBankOfIndiaParserTest {

    @TestFactory
    fun `central bank parser handles NEFT credits`(): List<DynamicTest> {
        val parser = CentralBankOfIndiaParser()

        val testCases = listOf(
            ParserTestCase(
                name = "NEFT credit with By. prefix",
                message = "Rs. 5.000 credited to your A/c xxxxxx1234 on 03/01/2026 through NEFT vide Ref No./XUTR/IN22XX...XX24 By.NEXTBILLION TECHNOLOGY PRIVATE LIMI-CBoI",
                sender = "JD-CENTBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "NEXTBILLION TECHNOLOGY PRIVATE LIMI",
                    accountLast4 = "1234"
                )
            )
        )

        val handleChecks = listOf(
            "JD-CENTBK-S" to true,
            "CENTBK" to true,
            "CBOI" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Central Bank of India Parser"
        )
    }
}
