import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.LaxmiBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class LaxmiBankParserTest {

    @TestFactory
    fun `laxmi sunrise parser handles debit and credit variants`(): List<DynamicTest> {
        val parser = LaxmiBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Laxmi Sunrise Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit transaction - eSewa load",
                message = "Dear Customer, Your #12344560 has been debited by NPR 720.00 on 05/09/25. Remarks:ESEWA LOAD/9763698550,127847587\n-Laxmi Sunrise",
                sender = "LAXMI_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("720.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "ESEWA",
                    accountLast4 = "4560",
                    reference = "05/09/25"
                )
            ),
            ParserTestCase(
                name = "Credit transaction - stipend",
                message = "Dear Customer, Your #12344560 has been credited by NPR 60,892.00 on 02/09/25. Remarks:(STIPEND PMT DM/MCH-SHRAWAN82).\n-Laxmi Sunrise",
                sender = "LAXMI_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("60892.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    merchant = "Stipend Payment",
                    accountLast4 = "4560",
                    reference = "02/09/25"
                )
            ),
            ParserTestCase(
                name = "Debit transaction - ATM withdrawal",
                message = "Dear Customer, Your #98765432 has been debited by NPR 1,500.00 on 10/09/25. Remarks:ATM WITHDRAWAL/KATHMANDU\n-Laxmi Sunrise",
                sender = "LAXMI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM WITHDRAWAL",
                    accountLast4 = "5432",
                    reference = "10/09/25"
                )
            ),
            ParserTestCase(
                name = "Credit transaction - salary",
                message = "Dear Customer, Your #1234 has been credited by NPR 5,000.00 on 15/09/25. Remarks:SALARY CREDIT\n-Laxmi Sunrise",
                sender = "LAXMI_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    merchant = "SALARY CREDIT\n-Laxmi Sunrise",
                    accountLast4 = "1234",
                    reference = "15/09/25"
                )
            )
        )

        val handleChecks = listOf(
            "LAXMI_ALERT" to true,
            "LAXMI" to true,
            "AD-LAXMI-A" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Laxmi Sunrise Bank Parser"
        )


    }
}
