import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BankinoBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BankinoBankParserTest {

    @TestFactory
    fun `bankino parser handles common cases`(): List<DynamicTest> {
        val parser = BankinoBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "Purchase / debit (EXPENSE)",
                message = "بانک خاورمیانه\nخرید با کارت 7284\n-3,300,000\nXXX/XXXXXXXX\nمانده 14,412,600\n03/22\n12:49",
                sender = "20004861",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3300000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("14412600"),
                    accountLast4 = "7284"
                )
            ),
            ParserTestCase(
                name = "Second purchase / debit (EXPENSE)",
                message = "بانک خاورمیانه\nخرید با کارت 7284\n-1,100,000\nXXX/XXXXXXXXX\nمانده 17,712,600\n03/22\n12:42",
                sender = "+98 20 0048 61",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1100000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("17712600"),
                    accountLast4 = "7284"
                )
            ),
            ParserTestCase(
                name = "Internet-bank transfer / credit (INCOME)",
                message = "بانک خاورمیانه\nانتقال از اینترنت بانک از کارت 9286\n+2,500,000\nXXX/XXXXXXXXX\nمانده 19,021,600\n03/22\n07:28",
                sender = "98200048610",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500000"),
                    currency = "IRR",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("19021600"),
                    accountLast4 = "9286"
                )
            )
        )

        val handleChecks = listOf(
            "20004861" to true,
            "+98 20 0048 61" to true,
            "98200048610" to true,
            "AD-ICICIB" to false,
            "HDFC" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Bankino Bank Parser")
    }
}
