import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.SampathBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class SampathBankParserTest {

    @TestFactory
    fun `sampath bank parser handles account and card transactions`(): List<DynamicTest> {
        val parser = SampathBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Account credit (LKR)",
                message = "LKR 5,000.00 credited to AC **8758 for PICKME - 37419306\n22/05/2026 23:36:52\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "LKR",
                    type = TransactionType.INCOME,
                    merchant = "PICKME",
                    reference = "37419306",
                    accountLast4 = "8758"
                )
            ),
            ParserTestCase(
                name = "Account debit (LKR)",
                message = "LKR 4,025.00 debited from AC **8758 for WPY_TAOA_041772_WLT@0347310\n18/05/2026 06:56:52\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4025.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "WPY_TAOA_041772_WLT@0347310",
                    accountLast4 = "8758"
                )
            ),
            ParserTestCase(
                name = "International credit (GBP)",
                message = "GBP 1,000.00 credited to AC **5012 for Remittance ID : [IR26GBP36623] : REALIZE\n14/05/2026 13:55:02\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "GBP",
                    type = TransactionType.INCOME,
                    merchant = "REALIZE",
                    reference = "IR26GBP36623",
                    accountLast4 = "5012"
                )
            ),
            ParserTestCase(
                name = "Credit card payment (LKR)",
                message = "Cr Crd no..**0282 Auth Pmt LKR 2,100.00 at SPICE ASIA - DELIVERY Avl Bal LKR 250,000.00 Enq Call 0112300604\n- Sampath Bank 23-MAY",
                sender = "SAMPCCTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2100.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "SPICE ASIA - DELIVERY",
                    accountLast4 = "0282",
                    balance = BigDecimal("250000.00")
                )
            )
        )

        val handleChecks = listOf(
            "SAMPATHTXN" to true,
            "SAMPCCTXN" to true,
            "AD-SAMPATHTXN" to true,
            "HDFCBK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Sampath Bank Parser"
        )
    }
}
