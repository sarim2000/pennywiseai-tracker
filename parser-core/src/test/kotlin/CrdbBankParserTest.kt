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
            ),
            ParserTestCase(
                // Tiering: a first-person send ("umetuma") must stay EXPENSE even when the
                // message also mentions the recipient having "received" the funds. The
                // strong sender verb outranks the weak income hint.
                name = "Outgoing send mentioning recipient received (EXPENSE)",
                message = "Umetuma TZS 30000.00 kwenda JANE DOE. JANE DOE has received the funds. Bal:TZS 50000.00",
                sender = "CRDB BANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("50000.00")
                )
            ),
            ParserTestCase(
                // LUKU electricity token: amount must anchor on the "TOTAL TZS" line,
                // never the intermediate Cost/VAT/EWURA/REA/Debt Collected figures.
                name = "LUKU electricity token (TOTAL anchored)",
                message = "Malipo yamekamilika.19ec9775f682395e 9007261660708258420 TOKEN 6642 0488 4500 7039 0706 12.2KWH Cost 1229.51 VAT 18% 221.31 EWURA 1% 12.30 REA 3% 36.88 Debt Collected 1500.00 TOTAL TZS 3000.00 2026-06-15 07:08",
                sender = "CRDB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "LUKU"
                )
            ),
            ParserTestCase(
                // SimBanking interbank transfer: recipient name after "kwenda" must stop
                // before the masked phone number; receipt id captured as reference.
                name = "SimBanking interbank transfer",
                message = "KUMB:19ec6ebe82c12bc7 Muamala umefanikiwa TZS35000 SELCOM BANK kwenda JANE DOE 06XXXXXXXX Risiti:003-19ec6ebe82c12bc7 2026-06-14 19:16:49  CRDB SIMBANKING APP",
                sender = "CRDB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("35000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "JANE DOE",
                    reference = "003-19ec6ebe82c12bc7"
                )
            ),
            ParserTestCase(
                // Account-to-account send ("Umetuma" = you sent): recipient name must stop
                // before the "AC:"/"REF:" labels.
                name = "Account-to-account transfer (Umetuma)",
                message = "Umetuma TZS 39,600.0 kutoka AC:01522***6100 kwenda JOHN DOE AC: 11375680 REF: 19ec69444b7bba7c 2026-06-14 17:41:9. Kwa msaada piga 0755197700",
                sender = "CRDB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("39600.0"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN DOE",
                    reference = "19ec69444b7bba7c"
                )
            ),
            ParserTestCase(
                // Merchant ("Lipa") payment via SimBanking: "kwenda LIPA <merchant>" drops
                // the LIPA prefix and keeps the merchant name.
                name = "Merchant payment via SimBanking (Lipa)",
                message = "KUMB:19ec660b8a1c1bde Muamala umefanikiwa TZS30000 VODACOM kwenda LIPA MERCHANT NAME 51469658 Risiti:003-19ec660b8a1c1bde 2026-06-14 16:44:48  CRDB SIMBANKING APP",
                sender = "CRDB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30000"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "MERCHANT NAME",
                    reference = "003-19ec660b8a1c1bde"
                )
            ),
            ParserTestCase(
                // Inbound deposit/credit: "you have received" -> INCOME, REF captured.
                name = "Inbound deposit / credit",
                message = "Dear Customer, you have received TZS850,000.00 in your account number: 0152********100 2026-06-13T19:18 REF:FT2616465ZC2 . For queries call 0755197700.",
                sender = "CRDB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("850000.00"),
                    currency = "TZS",
                    type = TransactionType.INCOME,
                    reference = "FT2616465ZC2"
                )
            ),
            ParserTestCase(
                // ATM cash withdrawal via card: balance captured, isFromCard true.
                name = "ATM cash withdrawal (card, masked)",
                message = "Dear JOHN DOE, TZS150000.00 has been withdrawn using a Card 4232***XXXX On 20.04.26 11:39 Balance is TZS2237.77 Inq. Call: 0755197700",
                sender = "CRDB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150000.00"),
                    currency = "TZS",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    balance = BigDecimal("2237.77"),
                    isFromCard = true,
                    accountLast4 = "4232"
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
