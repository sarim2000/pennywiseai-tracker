import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.CitizensBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class CitizensBankParserTest {

    @TestFactory
    fun `citizens bank parser handles messages`(): List<DynamicTest> {
        val parser = CitizensBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Citizens Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit - ATM withdrawal",
                message = "Dear ARUN, ###4041 is debited by NPR 5,000.00 on 29/03/2026, Remarks: ATM/459521x2018/CTZW Av Bal: 140270.04. Support Center:01-5970068",
                sender = "CTZN_Alert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    accountLast4 = "4041",
                    balance = BigDecimal("140270.04"),
                    reference = "459521x2018"
                )
            ),
            ParserTestCase(
                name = "Credit - connectIPS",
                message = "Dear ARUN, ###4041 is credited by NPR 150,000.00 on 25/03/2026, Remarks: cIPS/NP2603250066636 Av Bal: 153520.04. Support Center:01-5970068",
                sender = "CTZN_Alert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150000.00"),
                    currency = "NPR",
                    type = TransactionType.INCOME,
                    merchant = "connectIPS",
                    accountLast4 = "4041",
                    balance = BigDecimal("153520.04"),
                    reference = "NP2603250066636"
                )
            )
        )

        val handleChecks = listOf(
            "CTZN_Alert" to true,
            "CITIZENS_ALERT" to true,
            "CTZN" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Citizens Bank Parser"
        )
    }
}
