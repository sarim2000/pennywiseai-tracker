import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.YesBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class YesBankParserTest {

    @TestFactory
    fun `yes bank parser handles transactions and rejections`(): List<DynamicTest> {
        val parser = YesBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Yes Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val transactionCases = listOf(
            ParserTestCase(
                name = "C N S Fuel Port",
                message = "INR 404.36 spent on YES BANK Card X3349 @UPI_C N S FUEL PORT 24-08-2025 06:17:25 pm. Avl Lmt INR 211,476.24. SMS BLKCC 3349 to 9840909000 if not you",
                sender = "CP-YESBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("404.36"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "C N S FUEL PORT",
                    accountLast4 = "3349",
                    creditLimit = BigDecimal("211476.24"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "S B Enterprises",
                message = "INR 56.00 spent on YES BANK Card X3349 @UPI_S B ENTERPRISES 24-08-2025 06:03:40 am. Avl Lmt INR 211,880.60. SMS BLKCC 3349 to 9840909000 if not you",
                sender = "VM-YESBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "S B ENTERPRISES",
                    accountLast4 = "3349",
                    creditLimit = BigDecimal("211880.60"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Mohammed Akram",
                message = "INR 24.00 spent on YES BANK Card X3349 @UPI_MOHAMMED AKRAM 23-08-2025 11:51:19 am. Avl Lmt INR 212,012.60. SMS BLKCC 3349 to 9840909000 if not you",
                sender = "JX-YESBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("24.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "MOHAMMED AKRAM",
                    accountLast4 = "3349",
                    creditLimit = BigDecimal("212012.60"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Surakshaa Healthcare",
                message = "INR 250.00 spent on YES BANK Card X3349 @UPI_SURAKSHAA HEALTHCA 23-08-2025 10:02:59 am. Avl Lmt INR 212,036.60. SMS BLKCC 3349 to 9840909000 if not you",
                sender = "CP-YESBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "SURAKSHAA HEALTHCA",
                    accountLast4 = "3349",
                    creditLimit = BigDecimal("212036.60"),
                    isFromCard = true
                )
            )
        )

        val rejectionCases = listOf(
            ParserTestCase(
                name = "OTP message",
                message = "Dear Customer, your OTP for login is 123456. Do not share with anyone. -Yes Bank",
                sender = "CP-YESBNK-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional offer",
                message = "Get exciting offers on Yes Bank Credit Cards. Apply now! Visit yesbank.in",
                sender = "CP-YESBNK-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Payment request",
                message = "Payment request of INR 500.00 from merchant@upi. Ignore if already paid.",
                sender = "CP-YESBNK-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Payment due reminder",
                message = "Your Yes Bank Credit Card payment of INR 10,000 is due by 25-08-2025",
                sender = "CP-YESBNK-S",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = transactionCases + rejectionCases,
            handleCases = listOf(
                "CP-YESBNK-S" to true,
                "VM-YESBNK-S" to true,
                "JX-YESBNK-S" to true,
                "YESBANK" to true,
                "UNKNOWN" to false
            ),
            suiteName = "Yes Bank Parser"
        )


    }
}
