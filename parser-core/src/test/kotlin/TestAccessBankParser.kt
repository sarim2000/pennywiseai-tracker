import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.AccessBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class AccessBankParserTest {

    @TestFactory
    fun `access bank parser handles common cases`(): List<DynamicTest> {
        val parser = AccessBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Debit transaction (EXPENSE)",
                message = """
                    Debit
                    Amt:NGN20,400.00
                    Acc:146******325
                    Desc:179AMHY2616000hU/MOBILE TRF TO PAY/ POS /ABDULHADI  HAMISU
                    Date:09/06/2026
                    Avail Bal:NGN224,408.56
                    Total:NGN2
                """.trimIndent(),
                sender = "AccessBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20400.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "179AMHY2616000hU/MOBILE TRF TO PAY/ POS /ABDULHADI  HAMISU",
                    accountLast4 = "325",
                    balance = BigDecimal("224408.56")
                )
            ),
            ParserTestCase(
                name = "Credit transaction (INCOME)",
                message = """
                    Credit
                    Amt:NGN260,000.00
                    Acc:146******325
                    Desc:179NIPL2616000QZ/Paystack/CowrywiseCowrywise Financial Tech
                    Date:09/06/2026
                    Avail Bal:NGN260,000.38
                    Total:N
                """.trimIndent(),
                sender = "AccessBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("260000.00"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "179NIPL2616000QZ/Paystack/CowrywiseCowrywise Financial Tech",
                    accountLast4 = "325",
                    balance = BigDecimal("260000.38")
                )
            )
        )

        val handleCases = listOf(
            "AccessBank" to true,
            "AD-ACCESSBANK-S" to true,
            "ZENITHBANK" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Access Bank Parser Suite"
        )
    }
}
