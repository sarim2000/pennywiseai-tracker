import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.SBIBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SBIBankParserTest {

    @Test
    fun `sbi parser handles debit alerts`() {
        val parser = SBIBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "SBI Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit card transaction",
                message = "Dear Customer, transaction number 1234 for Rs.383.00 by SBI Debit Card 0000 done at merchant on 13Sep25 at 21:38:26. Your updated available balance is Rs.999999999. If not done by you, forward this SMS to 7400165218/ call 1800111109/9449112211 to block card. GOI helpline for cyber fraud 1930.",
                sender = "ATMSBI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("383.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0000"
                )
            ),
            ParserTestCase(
                name = "Standard debit message",
                message = "Rs.500 debited from A/c X1234 on 13Sep25. Avl Bal Rs.999999999",
                sender = "ATMSBI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234"
                )
            )
        )

        val handleChecks = listOf(
            "ATMSBI" to true,
            "SBICRD" to true,
            "SBIBK" to true,
            "UNKNOWN" to false
        )

        val result = ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "SBI Parser"
        )


    }
}
