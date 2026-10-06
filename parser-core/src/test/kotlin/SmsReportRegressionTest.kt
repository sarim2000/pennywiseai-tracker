import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/**
 * Regressions from user-submitted SMS reports. Each case is a message that was
 * parsed wrongly in the wild — booked money that never moved, or read the wrong
 * figure out of the message. Routed through [com.pennywiseai.parser.core.bank.BankParserFactory]
 * so the sender→parser dispatch is covered too.
 */
class SmsReportRegressionTest {

    @TestFactory
    fun `reported messages parse correctly`(): List<DynamicTest> {
        val cases = listOf(
            // --- Not transactions at all -------------------------------------
            SimpleTestCase(
                description = "Amex SafeKey OTP is not a card spend (hyphenated One-Time Password)",
                bankName = "American Express",
                sender = "TX-MYAMEX-S",
                currency = "INR",
                message = "Your Amex SafeKey One-Time Password for INR 213.50, at X CORP- PAID FEATURES is 000000. Valid for 10 mins for Card ending  1111. Do not disclose it to anyone.",
                shouldParse = false
            ),
            SimpleTestCase(
                description = "Kotak credit-card payment reminder is not an expense",
                bankName = "Kotak Bank",
                sender = "VM-KOTAKB-S",
                currency = "INR",
                message = "Payment of INR 1577 on Kotak Credit Card xx2222 is due on 13-07-26. Min due: INR 100. Tap to pay: https://example.invalid/pay Ignore if paid",
                shouldParse = false
            ),
            SimpleTestCase(
                description = "Axis auto-debit intimation is a future notice, not a debit",
                bankName = "Axis Bank",
                sender = "AD-AXISBK-S",
                currency = "INR",
                message = "INR 587.64 for Airtel Payments Bank Limited will be auto-debited via Axis Bank Card no. XXxxxx by 27-07-26. Please ensure sufficient limit/balance on your card/account to process the auto-debit. To deactivate the AutoPay facility for ID xxxxx, visit https://www.sihub.in/managesi/axisbank. TnC apply.",
                shouldParse = false
            ),
            SimpleTestCase(
                description = "IDFC ASBA/IPO blocking is not a debit",
                bankName = "IDFC First Bank",
                sender = "AD-IDFCFB-S",
                currency = "INR",
                message = "Your ASBA application for AUGMONT is received and Application value of Rs 14972 is blocked in your registered Bank account on 25/08/2026.",
                shouldParse = false
            ),
            SimpleTestCase(
                description = "HDFC rewards e-voucher is not income",
                bankName = "HDFC Bank",
                sender = "TX-HDFCBK-S",
                currency = "INR",
                message = "Dear Customer, You have received Amazon Shopping E-voucher Rs.500/- from HDFC Bank My Rewards Redemption programme. Your E-Voucher [E-Voucher Code is XXXX-XXXXXX-XXX, Pin: , Valid till 02-Aug-2027 Value : INR 500.00] . For more details call 1800000000. HDFC Bank",
                shouldParse = false
            ),

            SimpleTestCase(
                description = "Buying a voucher is still a real spend (guard must not swallow it)",
                bankName = "HDFC Bank",
                sender = "TX-HDFCBK-S",
                currency = "INR",
                // Split literal on purpose: BankSamplesDocTest harvests whole
                // message literals for the web tester's per-bank sample, and this
                // guard-rail case would displace HDFC's nicer one.
                message = "Rs.500.00 debited from A/c XX1111 on 09-Aug-26 " +
                    "to amazon evoucher (UPI Ref No 123456789012)",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE
                )
            ),

            // --- Wrong figure / wrong direction ------------------------------
            SimpleTestCase(
                description = "ICICI sub-unit foreign amount: USD .28, not the INR available limit",
                bankName = "ICICI Bank",
                sender = "JM-ICICIT-S",
                currency = "INR",
                message = "USD .28 spent using ICICI Bank Card XX3333 on 01-Jun-26 on GOOGLE*CLOUD PH. Avl Limit: INR 3,85,664.53. If not you, call 1800000000/SMS BLOCK 3333 to 1800000000.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.28"),
                    currency = "USD",
                    type = TransactionType.CREDIT,
                    merchant = "GOOGLE*CLOUD PH",
                    isFromCard = true
                )
            ),
            SimpleTestCase(
                description = "BoB credit with no leading digit takes the credited amount, not the balance",
                bankName = "Bank of Baroda",
                sender = "VM-BOBTXN-S",
                currency = "INR",
                message = "Rs..6 Credited to A/c ...4444 from:ACHCR/JIO FINANC. Total Bal:Rs.22976.31CR. Avlbl Amt:Rs.22976.31(26-08-2026 18:20:36) - Bank of Baroda",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.6"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("22976.31")
                )
            ),
            SimpleTestCase(
                description = "CUB transfer names both accounts — your credited a/c decides the direction",
                bankName = "City Union Bank",
                sender = "JX-CUBANK-S",
                currency = "INR",
                message = "Your a/c no. XXXXXXXX5555 is credited for Rs.5000.00 on 03-05-2026 and debited from a/c no. XXXXXXXX6666 (UPI Ref no 123456789012) -CUB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME
                )
            )
            ,
            SimpleTestCase(
                description = "Kotak NEFT credit names the sender and keeps the UTR",
                bankName = "Kotak Bank",
                sender = "VM-KOTAKB-S",
                currency = "INR",
                message = "Rs. 5000 credited to your Kotak Bank a/c XX1111 via NEFT from beneficiary John Doe. UTR Ref. HDFCH00000000000",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "John Doe",
                    reference = "HDFCH00000000000",
                    accountLast4 = "1111"
                )
            ),
            SimpleTestCase(
                description = "Kotak NEFT beneficiary keeps the periods inside a name",
                bankName = "Kotak Bank",
                sender = "VM-KOTAKB-S",
                currency = "INR",
                message = "Rs. 5000 credited to your Kotak Bank a/c XX1111 via NEFT from beneficiary Mr. John Doe. UTR Ref. HDFCH00000000000",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Mr. John Doe",
                    reference = "HDFCH00000000000",
                    accountLast4 = "1111"
                )
            ),
            SimpleTestCase(
                description = "Slice NEFT credit keeps the ref, not the word \"No\"",
                bankName = "Slice",
                sender = "VM-SLICEIT-S",
                currency = "INR",
                message = "Rs. 5000 received in a/c XX1111 from Person Name on 22-Sep-26 (NEFT Ref No. IDFB0000A0000000). - slice",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Person Name",
                    reference = "IDFB0000A0000000",
                    accountLast4 = "1111"
                )
            )
        )

        return ParserTestUtils.runFactoryTestSuite(
            cases + SimpleTestCase(
                // "paid" (expense) was matched before "deposited" (income).
                description = "HDFC interest deposit is income, not an expense",
                bankName = "HDFC Bank",
                sender = "JM-HDFCBK-S",
                currency = "INR",
                message = "Update! INR 6,657.00 deposited in HDFC Bank A/c XX1111 on 30-SEP-26 for Interest paid till 30-SEP-2026.Avl bal INR 12,28,554.72. Cheque deposits in A/C are subject to clearing",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6657.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "1111",
                    balance = BigDecimal("1228554.72")
                ),
                shouldHandle = true
            ) + SimpleTestCase(
                // "Postpaid" matched the "paid" keyword; the bill is only due.
                description = "Axis bill-due reminder is not a transaction",
                bankName = "Axis Bank",
                sender = "AX-AXISBK-S",
                currency = "INR",
                message = "Airtel Postpaid Fetch and Pay bill of INR 293.82 due on 28-09-26. Pay on https://example.invalid/pay and get instant cashback - Axis Bank",
                shouldParse = false
            ) + SimpleTestCase(
                // Guard: a completed debit that mentions a due date still parses.
                description = "A debit that mentions a due date is still a debit",
                bankName = "Axis Bank",
                sender = "AX-AXISBK-S",
                currency = "INR",
                message = "INR 5,000.00 debited from A/c XX1111 for EMI due on 05-10-26. Avl Bal INR 10,000.00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ) + SimpleTestCase(
                description = "HDFC PIXEL credit card spend (#861)",
                bankName = "HDFC Bank",
                sender = "AD-HDFCBK-S",
                currency = "INR",
                message = "Rs 1368.00 used on HDFC Bank PIXEL Card at TEST MERCHANT on 05/10/26 09:03.Not You? SMS BLOCKPCC 2222 to 1800000000 or https://example.invalid/b",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1368.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "TEST MERCHANT",
                    accountLast4 = "2222",
                    isFromCard = true
                ),
                shouldHandle = true
            ) + SimpleTestCase(
                description = "slice app notification names the payer, not the account",
                bankName = "Slice",
                sender = "slice",
                currency = "INR",
                message = "UPI payment received! — You’ve got ₹1,565 from Person Name in your slice bank a/c xx1234. Avl. Bal. ₹3,502.05.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1565"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Person Name",
                    accountLast4 = "1234",
                    balance = BigDecimal("3502.05")
                ),
                shouldHandle = true
            ) + SimpleTestCase(
                // The US ChaseBankParser accepts any sender containing "CHASE"; the
                // UK alias must reach the GBP parser instead.
                description = "ChaseUK alias routes to Chase UK, not the US parser",
                bankName = "Chase UK",
                sender = "ChaseUK",
                currency = "GBP",
                message = "🎉 £0.01 just landed in Test's Account from Jane Doe",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.01"),
                    currency = "GBP",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ) + SimpleTestCase(
                // Numeric shortcode — the parser only knew "Huntington Bank" (a contact
                // display name), so real texts from 446622 were discarded unparsed.
                description = "Huntington shortcode 446622 routes to Huntington",
                bankName = "Huntington Bank",
                sender = "446622",
                currency = "USD",
                message = "Huntington Heads Up. We processed an ATM withdrawal: \$17.07 at TEST ATM. Acct CK1234 has a \$500.00 bal (10/01/26 7:13 AM ET).",
                expected = ExpectedTransaction(
                    amount = BigDecimal("17.07"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "TEST ATM",
                    accountLast4 = "1234",
                    balance = BigDecimal("500.00")
                ),
                shouldHandle = true
            ) + SimpleTestCase(
                // Numeric shortcode: must not be claimed by a parser that grabs
                // numeric senders (EverestBank) ahead of NFCU.
                description = "NFCU shortcode 21398 routes to Navy Federal (#852)",
                bankName = "Navy Federal Credit Union",
                sender = "21398",
                currency = "USD",
                message = "NFCU: Transaction for \$231.72 was approved on credit card 1234 at TEST MERCHANT at 07:35 AM EDT on 09/25/26.Txt STOP to opt-out. Txt HELP for help.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("231.72"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    isFromCard = true
                ),
                shouldHandle = true
            ),
            "SMS report regressions"
        )
    }
}
