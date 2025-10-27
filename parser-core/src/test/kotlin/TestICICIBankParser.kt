import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.ICICIBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ICICIBankParserTest {

    @Test
    fun `icici parser handles currency and autopay flows`() {
        val parser = ICICIBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "ICICI Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "USD card purchase",
                message = "USD 11.80 spent using ICICI Bank Card XX7004 on 03-Sep-25 on 1xJetBrains AI . Avl Limit: INR 17,95,899.53. If not you, call 1800 2662/SMS BLOCK 7004 to 9215676766.",
                sender = "JM-ICICIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("11.80"),
                    currency = "USD",
                    type = TransactionType.CREDIT,
                    merchant = "1xJetBrains AI",
                    accountLast4 = "7004"
                )
            ),
            ParserTestCase(
                name = "EUR card purchase",
                message = "EUR 50.00 spent using ICICI Bank Card XX1234 on 05-Sep-25 on Amazon DE. Avl Limit: INR 2,00,000.00. SMS BLOCK 1234 to 9215676766",
                sender = "JM-ICICIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "EUR",
                    type = TransactionType.CREDIT,
                    merchant = "Amazon DE",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "INR card purchase",
                message = "INR 500.00 spent using ICICI Bank Card XX5678 on 06-Sep-25 on Swiggy. Avl Limit: INR 1,50,000.00.",
                sender = "JM-ICICIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "Swiggy",
                    accountLast4 = "5678"
                )
            ),
            ParserTestCase(
                name = "Future autopay notification",
                message = "Your account will be debited with Rs 649.00 on 03-Oct-25 towards Netflix Entertainment Ser for AutoPay MERCHANTMANDATE, RRN 421723106963-ICICI Bank.",
                sender = "AX-ICICIT-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Actual autopay debit",
                message = "Your account has been debited with Rs 649.00 towards Netflix Entertainment Ser for AutoPay MERCHANTMANDATE. RRN 421723106963. Avl Bal Rs 10,000.00-ICICI Bank",
                sender = "AX-ICICIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("649.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Netflix Entertainment Ser",
                    balance = BigDecimal("10000.00"),
                    reference = "421723106963"
                )
            ),
            ParserTestCase(
                name = "Future debit variation 1",
                message = "Rs. 500.00 will be debited from your account on 05-Oct-25 for EMI payment",
                sender = "AX-ICICIT-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Future debit variation 2",
                message = "Your ICICI Bank Account will be debited with Rs 1,000.00 on 10-Oct-25",
                sender = "AX-ICICIT-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Future debit variation 3",
                message = "AutoPay: Rs 299.00 will be debited on 15-Oct-25 for Spotify subscription",
                sender = "AX-ICICIT-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Regular debit with UPI reference",
                message = "ICICI Bank Acct XX123 debited for Rs 500.00 on 01-Oct-25; merchant credited. UPI: 543210987654. Call 18002662 for dispute. Updated Bal: Rs 5,000.00",
                sender = "AX-ICICIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "123",
                    reference = "543210987654",
                    balance = BigDecimal("5000.00")
                )
            ),
            ParserTestCase(
                name = "Regular debit bill payment",
                message = "Rs. 1,000.00 has been debited from your account XX456 for bill payment. Avl Bal: Rs 3,000.00",
                sender = "AX-ICICIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("3000.00")
                )
            ),
            ParserTestCase(
                name = "Regular debit with reference",
                message = "Your account has been successfully debited with Rs 250.00. Reference: TXN123456789",
                sender = "AX-ICICIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    reference = "TXN123456789"
                )
            ),
            ParserTestCase(
                name = "Salary credit with INF format",
                message = "ICICI Bank Account XX566 credited:Rs. 18,832.00 on 28-Feb-25. Info INF*000169831922*IQBO SAL FE. Available Balance is Rs. 28,076.14.",
                sender = "VM-ICICIT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("18832.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Salary",
                    accountLast4 = "566",
                    balance = BigDecimal("28076.14")
                )
            )
        )

        val handleChecks = listOf(
            "AX-ICICIT-S" to true,
            "JM-ICICIT-S" to true,
            "VM-ICICIT-S" to true,
            "ICICIB" to true,
            "ICICIBANK" to true,
            "HDFC" to false
        )

        val result = ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "ICICI Bank Parser"
        )

        ParserTestUtils.printTestSummary(
            totalTests = result.totalTests,
            passedTests = result.passedTests,
            failedTests = result.failedTests,
            failureDetails = result.failureDetails
        )
    }
}
