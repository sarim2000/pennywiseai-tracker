import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.KotakBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class KotakBankParserTest {

    @TestFactory
    fun `kotak parser handles UPI transactions with payment app QR codes`(): List<DynamicTest> {
        val parser = KotakBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Kotak Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Paytm QR code transaction",
                message = "Sent Rs.15.00 from Kotak Bank AC X1234 to paytmqr288005050101t74afkchmxjd@paytm on 14-10-25.UPI Ref 1234567890. Not you, https://kotak.com/KBANKT/Fraud",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Paytm",
                    reference = "1234567890",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "PhonePe QR code transaction",
                message = "Sent Rs.100.00 from Kotak Bank AC X5678 to phonepeqr123456789xyz@ybl on 15-10-25.UPI Ref 9876543210. Not you, https://kotak.com/KBANKT/Fraud",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "PhonePe",
                    reference = "9876543210",
                    accountLast4 = "5678"
                )
            ),
            ParserTestCase(
                name = "Person-to-person UPI with phone number",
                message = "Sent Rs.500.00 from Kotak Bank AC X9999 to 9876543210@paytm on 15-10-25.UPI Ref 1111111111. Not you, https://kotak.com/KBANKT/Fraud",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "9876543210",
                    reference = "1111111111",
                    accountLast4 = "9999"
                )
            ),
            ParserTestCase(
                name = "UPI received transaction",
                message = "Received Rs.250.00 in your Kotak Bank AC X3333 from john.doe@oksbi on 14-10-25.UPI Ref 2222222222. Not you, https://kotak.com/KBANKT/Fraud",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "john.doe",
                    reference = "2222222222",
                    accountLast4 = "3333"
                )
            ),
            ParserTestCase(
                name = "Standard debit message",
                message = "Rs.1000.00 debited from your Kotak Bank AC X4444 on 15-10-25. Avl Bal Rs.10000.00",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4444",
                    balance = BigDecimal("10000.00")
                )
            ),
            ParserTestCase(
                name = "IMPS credit surfaces masked mobile as merchant",
                message = "Received Rs. 329.00 on 15-04-26 in your Kotak Bank A/C x2451 by an A/C linked to mobile x111. IMPS Ref no 610511412340.",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("329.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "x111",
                    reference = "610511412340",
                    accountLast4 = "2451"
                )
            ),
            ParserTestCase(
                name = "Credit card spending with available limit",
                message = "INR 20 spent on Kotak Credit Card x5236 on 23-JAN-2026 at UPI-638903921672-CORN. Avl limit INR 73733.02 Fraud? https://www.kotak.bank.in/KBANKT/querytxn",
                sender = "TX-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "CORN",
                    accountLast4 = "5236",
                    isFromCard = true,
                    creditLimit = BigDecimal("73733.02")
                )
            ),
            ParserTestCase(
                name = "Short-SMS UPI to person (KOTAKD sender)",
                message = "Sent Rs.51.00 from XXXXXX9722 to DINESHBHAI RAMJIBHAI on 30/04/2026. UPI ref no. 648604626824. Not you? Tap https://kotk.in/KOTAKD/Eg9Cu0 to report -Kotak",
                sender = "VM-KOTAKD-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("51.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "DINESHBHAI RAMJIBHAI",
                    reference = "648604626824",
                    accountLast4 = "9722"
                )
            ),
            ParserTestCase(
                name = "Short-SMS UPI smaller amount",
                message = "Sent Rs.10.00 from XXXXXX9722 to Makavana Mahipatbhai on 01/05/2026. UPI ref no. 648724676562. Not you? Tap https://kotk.in/KOTAKD/EhkUKk to report -Kotak",
                sender = "VM-KOTAKD-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Makavana Mahipatbhai",
                    reference = "648724676562",
                    accountLast4 = "9722"
                )
            ),
            // Issue #360: "Sent" UPI format from a JD-KOTAKD-S sender. Merchant must
            // be the payee between "to" and "on <date>", never the "Not you? Tap
            // https://kotk.in/..." report link or the "-Kotak" signature.
            ParserTestCase(
                name = "Issue #360 - Sent UPI with multi-word payee (KOTAKD-S)",
                message = "Sent Rs.205.00 from XXXXXX1234 to ANNAPOORNESWARI K M on 26/05/2026. UPI ref no. 651229267141. Not you? Tap https://kotk.in/KOTAKD/E7LzzX to report -Kotak",
                sender = "JD-KOTAKD-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("205.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "ANNAPOORNESWARI K M",
                    reference = "651229267141",
                    accountLast4 = "1234"
                )
            ),
            // Issue #360: Kotak migrated "Sent" UPI alerts to RCS, which deliver a
            // decoded display-name sender ("Kotak Mahindra Bank") rather than a DLT
            // header. The parser must still handle and parse these correctly.
            ParserTestCase(
                name = "RCS Sent UPI with display-name sender",
                message = "Sent Rs.205.00 from XXXXXX1234 to ANNAPOORNESWARI K M on 26/05/2026. UPI ref no. 651229267141. Not you? Tap https://kotk.in/KOTAKD/E7LzzX to report -Kotak",
                sender = "Kotak Mahindra Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("205.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "ANNAPOORNESWARI K M",
                    reference = "651229267141",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Issue #360 - Sent UPI with single-word payee (KOTAKD-S)",
                message = "Sent Rs.90.00 from XXXXXX9886 to RAMESH on 25/05/2026. UPI ref no. 653114209367. Not you? Tap https://kotk.in/KOTAKD/S6ztop to report -Kotak",
                sender = "JD-KOTAKD-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("90.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "RAMESH",
                    reference = "653114209367",
                    accountLast4 = "9886"
                )
            ),
            // Negative case: a credit/refund-style message that contains the word
            // "sent" (e.g. "...has been sent to your A/c...") must still be parsed
            // as INCOME — the parser should anchor on "sent rs", not bare "sent".
            ParserTestCase(
                name = "Credit message containing the word 'sent' stays INCOME",
                message = "Cashback of Rs.50.00 has been sent to your Kotak Bank A/c x5555. Credited on 14-10-25. UPI Ref 9999999999",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    reference = "9999999999",
                    accountLast4 = "5555"
                )
            ),
            // Issue #616: credit-card refund. "refunded" is not a base transaction
            // keyword, so isTransactionMessage must recognise it or parse() drops it.
            // Type is INCOME (money back to the card), isFromCard=true, merchant is the
            // payee between "<amount> from" and "refunded", card last4 from "Card xNNNN".
            ParserTestCase(
                name = "Issue #616 - Credit card refund",
                message = "INR 2 from Airport Lounge refunded to your Kotak Credit Card x8848 on 17-Jul-2026. Opted for EMI? Please call 18602662666 to cancel.",
                sender = "JM-Kotakb-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Airport Lounge",
                    accountLast4 = "8848",
                    isFromCard = true
                )
            )
        )

        val handleChecks = listOf(
            // DLT senders still match
            "JD-KOTAKB-S" to true,
            "JD-KOTAKB-T" to true,
            "VM-KOTAKD-S" to true,
            "JD-KOTAKD-S" to true,
            // Issue #616: mixed-case DLT header for Kotak credit card refunds
            "JM-Kotakb-S" to true,
            // RCS display-name senders (issue #360) match via the contains("KOTAK") branch
            "Kotak" to true,
            "Kotak Mahindra Bank" to true,
            "Kotak811" to true,
            // Intentional superset: a bare DLT header without the -S/-T suffix also
            // matches via contains("KOTAK"). Asserted so the behavior stays documented.
            "VM-KOTAKB" to true,
            // Non-Kotak senders are rejected
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Kotak Bank Parser"
        )


    }
}
