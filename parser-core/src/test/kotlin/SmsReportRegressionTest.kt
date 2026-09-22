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
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "SMS report regressions")
    }
}
