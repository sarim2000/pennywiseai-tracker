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
                message = "Dear [Name] your Account 1*********9388 has been Credited with ETB 3,000.00 from Be, on 13/09/2025 at 12:37:24 with Ref No ********* Your Current Balance is ETB 3,104.87. Thank you for Banking with CBE!",
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
                message = "Dear [Name], You have transfered ETB 250.00 to Sender on 14/09/2025 at 12:28:56 from your account 1*********9388. Your account has been debited with a S.charge of ETB 0 and  15% VAT of ETB0.00, with a total of ETB250. Your Current Balance is ETB 2,829.87. Thank you for Banking with CBE!",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "Sender",
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
                    merchant = "Salary Payment",
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
                    merchant = "Business Payment",
                    accountLast4 = "5678",
                    balance = BigDecimal("130000.75")
                )
            ),
            ParserTestCase(
                name = "Large amount credit",
                message = "Dear Name, You have transfered ETB 5,000.00 to Ali Mohamud on 17/03/2026 at 10:52:06 from your account 1*********2222. Your account has been debited with a S.charge of ETB 10.00 and VAT(15%) of ETB1.50 and Disaster Fund (5%) of ETB0.50, with a total of ETB 5012.00. Your Current Balance is ETB 4,183.14. Thank you for Banking with CBE! https://apps.cbe.com.et:100/?id=FT26076N2J9G74602222 For feedback click the link https://forms.gle/R1s9nkJ6qZVCxRVu9",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5012.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "Ali Mohamud",
                    accountLast4 = "2222",
                    balance = BigDecimal("4183.14")
                )
            ),
            ParserTestCase(
                name = "Large amount debit",
                message = "Dear Name your Account 1*********2222 has been debited with ETB10,000.00. Service charge of  ETB 10.00 and VAT(15%) of ETB1.50 and Disaster Fund (5%) of ETB0.50 with a total of ETB 10012.00. Your Current Balance is ETB 10,215.54. Thank you for Banking with CBE! https://apps.cbe.com.et:100/?id=FT260708LN6D7462222",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10012.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = null,
                    accountLast4 = "2222",
                    balance = BigDecimal("10215.54")
                )
            ),
            ParserTestCase(
                name = "amount with no merchant debit",
                message = "Dear Name your Account 1*********2222 has been debited with ETB5,000.00. Service charge of  ETB 10.00 and VAT(15%) of ETB1.50 and Disaster Fund (5%) of ETB0.50 with a total of ETB 5012.00. Your Current Balance is ETB 3,167.54. Thank you for Banking with CBE! https://apps.cbe.com.et:100/?id=FT26081LGXZ922222222",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5012.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = null,
                    accountLast4 = "2222",
                    balance = BigDecimal("3167.54")
                )
            ),
            ParserTestCase(
                name = "amount with no merchant debit alternate",
                message = "Dear Name your Account 1*********2222 has been Credited with ETB 22,000.00. Your Current Balance is ETB 802,566.16 Thank you for Banking with CBE! https://apps.cbe.com.et:100/?id=FT26093CG49C74622222",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("22000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = null,
                    accountLast4 = "2222",
                    balance = BigDecimal("802566.16")
                )
            ),
            ParserTestCase(
                name = "amount with no merchant debit alternate",
                message = "Dear Mr NAME your Account 1********2222 has been credited by PERSON NAME &/OROTHER PERSON with ETB 75000.00. Your Current Balance is ETB 727554.16. Thank you for Banking with CBE! for Reciept https://apps.cbe.com.et:100/BranchReceipt/FT260942DCC3&22222222",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "PERSON NAME &/OROTHER PERSON",
                    accountLast4 = "2222",
                    balance = BigDecimal("727554.16")
                )
            ),
            ParserTestCase(
                name = "card payment within network",
                message = "Dear Mr Name your Account 1********2222 has been debited for COMPANY NAME HERE PLC with ETB 5230. Your Current Balance is ETB 928975.62. Thank you for Banking with CBE!. For feedback https://shorturl.at/auUX0 for Reciept https://apps.cbe.com.et:100/BranchReceipt/FT26109KYXLZ&22222222",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5230"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = "COMPANY NAME HERE PLC",
                    accountLast4 = "2222",
                    balance = BigDecimal("928975.62")
                )
            ),
            ParserTestCase(
                name = "card payment outside network",
                message = "Dear Mr Name your Account 1********2222 has been debited with ETB 2860. Your Current Balance is ETB 926115.62. Thank you for Banking with CBE!. For feedback https://shorturl.at/auUX0 for Reciept https://apps.cbe.com.et:100/BranchReceipt/FT26109M2YHV&22222222",
                sender = "CBE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2860"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    merchant = null,
                    accountLast4 = "2222",
                    balance = BigDecimal("926115.62")
                )
            ),
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
