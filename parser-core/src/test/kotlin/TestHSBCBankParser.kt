import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.HSBCBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class HSBCBankParserTest {

    @TestFactory
    fun `hsbc bank parser handles expected scenarios`(): List<DynamicTest> {
        val parser = HSBCBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "HSBC Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Issue #118 - Credit transactions with account format A/c XXX-XXX***-XXX
            ParserTestCase(
                name = "NEFT Credit with UTR - Format A/c 074-260***-006",
                message = "HSBC: A/c 074-260***-006 is credited with INR 5000.00 on 27NOV at 06.33.02 with UTR CHASH00007392391 as NEFT from CHAS A/c ***6983 of John Doe . Your Avl Bal is INR 15000.50.",
                sender = "HSBC",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "CHAS A/c ***6983 of John Doe",
                    accountLast4 = "0006",
                    balance = BigDecimal("15000.50"),
                    reference = "CHASH00007392391"
                )
            ),

            ParserTestCase(
                name = "NEFT Credit with UTR - Different account format",
                message = "HSBC: A/c 123-456***-789 is credited with INR 2500.75 on 15DEC at 10.15.30 with UTR NEFT12345678901 as NEFT from AXIS A/c ***1234 of Jane Smith . Your Avl Bal is INR 50000.00.",
                sender = "HSBC",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.75"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "AXIS A/c ***1234 of Jane Smith",
                    accountLast4 = "0789",
                    balance = BigDecimal("50000.00"),
                    reference = "NEFT12345678901"
                )
            ),

            // Debit card transaction
            ParserTestCase(
                name = "Debit Card Purchase",
                message = "Thank you for using HSBC Debit Card XXXXX71xx at IKEA INDIA . for INR 49.00 on 12-04-25.",
                sender = "HSBC",
                expected = ExpectedTransaction(
                    amount = BigDecimal("49.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "IKEA INDIA",
                    accountLast4 = "71xx"
                )
            ),

            // Credit card transaction
            ParserTestCase(
                name = "Credit Card Purchase",
                message = "Your HSBC creditcard xxxxx1234 used at AMAZON for INR 305.00 on 15-04-25.",
                sender = "HSBC",
                expected = ExpectedTransaction(
                    amount = BigDecimal("305.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "AMAZON",
                    accountLast4 = "1234"
                )
            ),

            // Payment transaction
            ParserTestCase(
                name = "Payment Transaction",
                message = "HSBC: INR 1000.50 is paid from account XXXXXX4567 to ELECTRICITY BOARD on 20APR with ref 222222222222. Your available bal is INR 8000.00.",
                sender = "HSBC",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.50"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "ELECTRICITY BOARD",
                    accountLast4 = "4567",
                    balance = BigDecimal("8000.00"),
                    reference = "222222222222"
                )
            )
        )

        val handleChecks = listOf(
            "HSBC" to true,
            "HSBCIN" to true,
            "AX-HSBC-S" to true,
            "JD-HSBCIN-T" to true,
            "HDFC" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "HSBC Bank Parser Tests"
        )
    }
}
