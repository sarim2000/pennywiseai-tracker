import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.NationalSavingsBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class NationalSavingsBankParserTest {

    @TestFactory
    fun `national savings bank parser handles credit debit and withdrawals`(): List<DynamicTest> {
        val parser = NationalSavingsBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "CEFT inward credit",
                message = "Dear MR MASKED NAME,LKR 10,000.00 Credited to your A/c XXXXXXXXXXXX on DD/MM/YYYY at HH:mm.AvlBal LKR 12,653.61.Transaction CEFT Inward Transfer Deposit.Thank you for banking with us.Call Centre 1972.",
                sender = "NSB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "LKR",
                    type = TransactionType.INCOME,
                    merchant = "CEFT Inward Transfer Deposit",
                    balance = BigDecimal("12653.61")
                )
            ),
            ParserTestCase(
                name = "POS debit",
                message = "Dear MR MASKED NAME,LKR 3,216.00 Debited from your A/c XXXXXXXXXXXX on DD/MM/YYYY at HH:mm.AvlBal LKR 9,437.61. @ Wetara Pharamcy & Groc Polgasowita. ATM POS Transaction.Thank you for banking with us.Call Centre 1972.",
                sender = "NSB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3216.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "Wetara Pharamcy & Groc Polgasowita",
                    balance = BigDecimal("9437.61")
                )
            ),
            ParserTestCase(
                name = "Internet-Mobile withdrawal",
                message = "Dear MR MASKED NAME,LKR 2,800.00 Debited from your A/c XXXXXXXXXXXX on DD/MM/YYYY at HH:mm.AvlBal LKR 24,037.61.Internet-Mobile Withdrawal.Thank you for banking with us.Call Centre 1972.",
                sender = "NSB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2800.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "Internet-Mobile Withdrawal",
                    balance = BigDecimal("24037.61")
                )
            )
        )

        val handleChecks = listOf(
            "NSB" to true,
            "AD-NSB-S" to true,
            "NSBI_ALERT" to false,
            "HDFCBK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "National Savings Bank Parser"
        )
    }
}
