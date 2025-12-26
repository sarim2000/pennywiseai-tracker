import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.CBEBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class CBEBankParserTest {

    @TestFactory
    fun `cbe parser handles credit debit and transfer`(): List<DynamicTest> {
        val parser = CBEBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Commercial Bank of Ethiopia",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Credit transaction",
                message = "Dear [Name] your Account 1*********9388 has been Credited with ETB 3,000.00 from Be***, on 13/09/2025 at 12:37:24 with Ref No ********* Your Current Balance is ETB 3,104.87. Thank you for Banking with CBE!",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "Be",
                    accountLast4 = "9388",
                    balance = BigDecimal("3104.87")
                )
            ),
            ParserTestCase(
                name = "Debit transaction",
                message = "Dear [Name] your Account 1*********9388 has been debited with ETB 25.00. Your Current Balance is ETB 3,079.87 Thank you for Banking with CBE! https://apps.cbe.com.et:100/?id=FT25256RP1FK27799388",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3079.87"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9388",
                    balance = BigDecimal("3079.87"),
                    reference = "FT25256RP1FK27799388"
                )
            ),
            ParserTestCase(
                name = "Transfer transaction",
                message = "Dear [Name], You have transfered ETB 250.00 to Se***** on 14/09/2025 at 12:28:56 from your account 1*********9388. Your account has been debited with a S.charge of ETB 0 and  15% VAT of ETB0.00, with a total of ETB250. Your Current Balance is ETB 2,829.87. Thank you for Banking with CBE!",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "your",
                    accountLast4 = "9388",
                    balance = BigDecimal("2829.87")
                )
            ),
            ParserTestCase(
                name = "Alternative sender credit",
                message = "Dear Customer your Account 1*********1234 has been Credited with ETB 5,000.00 from Salary Payment, on 15/09/2025 at 09:00:00 with Ref No ABC123456 Your Current Balance is ETB 8,000.00. Thank you for Banking with CBE!",
                sender = "CBEBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "Salary",
                    accountLast4 = "1234",
                    balance = BigDecimal("8000.00"),
                    reference = "ABC123456"
                )
            ),
            ParserTestCase(
                name = "Large amount credit",
                message = "Dear [Name] your Account 1*********5678 has been Credited with ETB 125,500.50 from Business Payment, on 16/09/2025 at 14:30:00 Your Current Balance is ETB 130,000.75. Thank you for Banking with CBE!",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("125500.50"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "Business",
                    accountLast4 = "5678",
                    balance = BigDecimal("130000.75")
                )
            )
        )

        val handleChecks = listOf(
            "CBE" to true,
            "CBEBANK" to true,
            "AD-CBE-A" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "CBE Parser"
        )


    }
}
