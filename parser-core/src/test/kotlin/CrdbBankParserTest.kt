import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.CrdbBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class CrdbBankParserTest {

    @TestFactory
    fun `crdb parser handles common cases`(): List<DynamicTest> {
        val parser = CrdbBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "ATM withdrawal (English)",
                message = "Dear NAME, TZS 50000.00 has been withdrawn using a Card 4232***0581 On 19.01.2511:53 Balance is TZS 550070.90",
                sender = "CRDB BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    balance = BigDecimal("550070.90"),
                    accountLast4 = "0581"
                )
            ),
            ParserTestCase(
                name = "Card payment foreign currency (USD)",
                message = "Paid:NETFLIX.COM, NL USD 9.99 Card:4232***0581 Date:17.01.2517:39 Bal:TZS923041.06",
                sender = "CRDB BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("9.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "NETFLIX.COM, NL",
                    balance = BigDecimal("923041.06"),
                    accountLast4 = "0581"
                )
            ),
            ParserTestCase(
                name = "Mobile money send (Swahili)",
                message = "Muamala umefanikiwa TZS40000 AIRTEL kwenda MSHAMU MSHAMU 255686621388",
                sender = "CRDB BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("40000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "MSHAMU MSHAMU"
                )
            ),
            ParserTestCase(
                name = "Bill payment / utility (Swahili)",
                message = "Malipo yamekamilika TOTAL TZS 2000",
                sender = "CRDB BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "TOTAL"
                )
            ),
            ParserTestCase(
                // Regression: a deposit ("umepokea") that also contains the outgoing word
                // "kwenda" must classify as INCOME — income keywords are matched first.
                name = "Incoming transfer with both umepokea and kwenda (INCOME)",
                message = "Umepokea TZS 75000.00 kutoka JOHN DOE kwenda akaunti yako Balance is TZS 200000.00",
                sender = "CRDB BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75000.00"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("200000.00")
                )
            )
        )

        val handleChecks = listOf(
            "CRDB BANK" to true,
            "crdb" to true,
            "MPESA" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "CRDB Bank Parser")
    }
}
