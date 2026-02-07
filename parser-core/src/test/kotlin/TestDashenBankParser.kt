import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.DashenBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class DashenBankParserTest {

    @TestFactory
    fun `dashen parser handles credit debit and transfer`(): List<DynamicTest> {
        val parser = DashenBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Dashen Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit standard transaction",
                message = "Dear Customer, your account 5387********011 has been debited with ETB 700.00 on 2025-08-11 at 09:28:18. A service fee of ETB 0 and VAT of ETB 0 have been applied. Your current balance is ETB 1,846.06. Thank you for using Dashen Super App! https://receipt.dashensuperapp.com/receipt/387WDTS252240001",
                sender = "Dashen Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("700.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5387",
                    balance = BigDecimal("1846.06"),
                    reference = "https://receipt.dashensuperapp.com/receipt/387WDTS252240001"
                )
            ),
            ParserTestCase(
                name = "Debit wallet/telebirr transaction",
                message = "Dear Customer, ETB 9,500.00 has been debited from your account 5387********011 and credited to the Telebirr account +251922222222 on 2025-12-19 at 07:58:55. A service fee of ETB 10 and VAT of ETB 1.5 have been applied Your current balance is ETB 120.93. Thank you for using the Dashen Super App! https://receipt.dashensuperapp.com/receipt/387LTWS2535300WM",
                sender = "DashenBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("9500.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5387",
                    balance = BigDecimal("120.93"),
                    merchant = "Telebirr account +251922222222",
                    reference = "https://receipt.dashensuperapp.com/receipt/387LTWS2535300WM"
                )
            ),
            ParserTestCase(
                name = "Transfer credit transaction",
                message = "Dear Customer, your account 5387********011 has been credited with ETB 10,000.00 from PERSON NAME on on 2025-08-29 at 09:57:14. Your current balance is ETB 11,846.06. Thank you for using Dashen Super App!",
                sender = "DashenBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "PERSON NAME",
                    accountLast4 = "5387",
                    balance = BigDecimal("11846.06")
                )
            ),
            ParserTestCase(
                name = "wallet/telebirr credit transaction",
                message = "Dear Customer, You have received ETB 525.00 from telebirr account number 251922222222 Ref No:2209012000164277  on 01/09/2022 11:47:11 to your bank account '5387*****9011'. Your account balance is ETB 543.49. Dashen Bank - Always one step ahead!",
                sender = "DashenBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("525.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "telebirr account number 251922222222 ",
                    accountLast4 = "5387",
                    balance = BigDecimal("543.49"),
                    reference = "2209012000164277"
                )
            )
        )

        val handleChecks = listOf(
            "DASHENBANK" to true,
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Dashen Bank Parser"
        )


    }
}
