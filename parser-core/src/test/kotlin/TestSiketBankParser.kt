import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.SiketBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class SiketBankParserTest {

    @TestFactory
    fun `siket parser handles credit debit and transfer`(): List<DynamicTest> {
        val parser = SiketBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Siket Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Credit transaction",
                message = "Dear Siket Bank Family, Your Account 1****1234 has been Credited with ETB 30,000.00. Your Current Balance is ETB 577,453.30. Thank you for Banking with Siket Bank! For any queries call 8342.",
                sender = "Siket Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    balance = BigDecimal("577453.30")
                )
            ),
            ParserTestCase(
                name = "Regular debit transaction",
                message = "Dear Siket Bank Family, Your Account 1****1234 has been Debited with ETB 50,000.00. Your Current Balance is ETB 315,022.41. Thank you for Banking with Siket Bank!",
                sender = "Siket Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("315022.41")
                )
            ),
            ParserTestCase(
                name = "Transfer debit uses transfer amount not service charge",
                message = "Dear Siket Bank Family, You have transferred ETB 10,012.00 from your account 1****1234 to telebirr account 251900000000 on 06 MAR 26 with Reference number FT00000000000. The Service Charge is ETB10.00 and VAT of ETB1.50. Your Current Balance is ETB 128,330.87. Thank you for Banking with Siket Bank.",
                sender = "Siket Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10012.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("128330.87"),
                    merchant = "telebirr account 251900000000",
                    reference = "FT00000000000"
                )
            )
        )

        val handleChecks = listOf(
            "Siket Bank" to true,
            "SIKETBANK" to true,
            "DASHENBANK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Siket Bank Parser"
        )
    }
}
