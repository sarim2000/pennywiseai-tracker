import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BandhanBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BandhanBankParserTest {

    @Test
    fun `bandhan bank parser handles provided samples`() {
        val parser = BandhanBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Bandhan Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val transactionCases = listOf(
            ParserTestCase(
                name = "Interest credit",
                message = "Dear Customer, your account XXXXXXXXXX1234 is credited with INR 3.00 on 01-OCT-2025 towards interest. Bandhan Bank",
                sender = "XY-BDNSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Interest",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "UPI credit with balance",
                message = "INR 25,000.00 deposited to A/c XXXXXXXXXX1234 towards UPI/CR/C224513287910/JOHN DOE/u on 03-OCT-2025 . Clear Bal is INR 30,123.00 . Bandhan Bank.",
                sender = "XY-BDNSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "JOHN DOE",
                    reference = "C224513287910",
                    accountLast4 = "1234",
                    balance = BigDecimal("30123.00")
                )
            )
        )

        val rejectionCases = listOf(
            ParserTestCase(
                name = "OTP message",
                message = "Your Bandhan Bank OTP is 123456. Do not share with anyone.",
                sender = "XY-BDNSMS-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message",
                message = "Bandhan Bank offers 7% interest on fixed deposits. Visit branch today!",
                sender = "XY-BDNSMS-S",
                shouldParse = false
            )
        )

        val result = ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = transactionCases + rejectionCases,
            handleCases = listOf(
                "XY-BDNSMS" to true,
                "AM-BDNSMS-S" to true,
                "BP-BANDHN-S" to true,
                "VM-BOIIND-S" to false
            ),
            suiteName = "Bandhan Bank Parser"
        )


    }
}

