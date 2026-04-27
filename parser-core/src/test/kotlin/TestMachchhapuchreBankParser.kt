import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.MachchhapuchreBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class MachchhapuchreBankParserTest {

    @TestFactory
    fun `machchhapuchre bank parser handles messages`(): List<DynamicTest> {
        val parser = MachchhapuchreBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Machchhapuchre Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit - medicine",
                message = "Dear ARUN,NPR 3,190.00 Withdrawn from your A/C ###0018 on 29/03/2026 Remarks: medicine,K Available Bal: 20532.09. For app: http://bit.ly/3QZrCFj",
                sender = "MBL_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3190.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "medicine,K",
                    accountLast4 = "0018",
                    balance = BigDecimal("20532.09")
                )
            ),
            ParserTestCase(
                name = "Credit - Salary (Ma",
                message = "Dear ARUN,NPR 50,000.00 Deposited in your A/C ###0018 on 26/02/2026 Remarks: Salary (Ma Available Bal: 55652.24. For app: http://bit.ly/3QZrCFj",
                sender = "MBL_ALERT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    merchant = "Salary (Ma",
                    accountLast4 = "0018",
                    balance = BigDecimal("55652.24")
                )
            )
        )

        val handleChecks = listOf(
            "MBL_ALERT" to true,
            "MACHHAPUCHHRE" to true,
            "MBL" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Machchhapuchre Bank Parser"
        )
    }
}
