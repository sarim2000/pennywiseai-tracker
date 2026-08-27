import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.AwashBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class AwashBankParserTest {

    @TestFactory
    fun `awash parser handles credit telebirr and bank transfer`(): List<DynamicTest> {
        val parser = AwashBankParser()

        // NOTE: all names / phone / account / txn-id values below are fabricated placeholders.
        val testCases = listOf(
            ParserTestCase(
                name = "Credit (income)",
                message = "Dear Customer, ETB 200 has been credited to your account from JOHN DOE on: 2026-08-09 09:33:35 with Txn ID: 000000000000001. Your available balance is now ETB 12,269.09. Receipt Link: https://example.com/r. Contact center 8980.",
                sender = "Awash Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "JOHN DOE",
                    reference = "000000000000001",
                    balance = BigDecimal("12269.09")
                )
            ),
            ParserTestCase(
                name = "Telebirr transfer (expense) - amount not charge/VAT",
                message = "Dear Customer; Telebirr Transfer of 30,000.00 ETB to JANE ROE - 251900000000 from 013201234500300/BANK, Reason- Test, Charge 10.00 VAT: 1.50. Your Balance is ETB 10,151.17. Receipt Link: https://example.com/r. Contact Center 8980.",
                sender = "AwashBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30000.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "JANE ROE",
                    accountLast4 = "0300",
                    balance = BigDecimal("10151.17")
                )
            ),
            ParserTestCase(
                name = "Bank transfer (expense) - balance has no ETB prefix",
                message = "Dear Customer, You have sent ETB 1,000 To (013204560000) - JOHN DOE by Transaction ID: 000000000000002 charge- 1.00 VAT- 0.15 Date 2026-04-05 13:24:29. Your Available Balance is 11,818.92. Download the receipt by link https://example.com/r.",
                sender = "Awash Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN DOE",
                    reference = "000000000000002",
                    balance = BigDecimal("11818.92")
                )
            )
        )

        val handleChecks = listOf(
            "Awash Bank" to true,
            "AWASHBANK" to true,
            "AB-AWASH-S" to true,
            "DASHENBANK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Awash Bank Parser"
        )
    }
}
