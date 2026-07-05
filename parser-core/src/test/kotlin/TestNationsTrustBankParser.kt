import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.NationsTrustBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class NationsTrustBankParserTest {

    @TestFactory
    fun `nations trust bank parser handles card transactions`(): List<DynamicTest> {
        val parser = NationsTrustBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Card purchase - bill payment",
                message = "Transaction Approved on your Card **************** for LKR 500.00 at BILL PAYMENT VIA NATIONS Available Bal LKR 12345.73  Call 0114315315 for any inquiry.",
                sender = "NationsSMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "LKR",
                    type = TransactionType.CREDIT,
                    merchant = "BILL PAYMENT VIA NATIONS",
                    balance = BigDecimal("12345.73")
                )
            ),
            ParserTestCase(
                name = "Card purchase - ride",
                message = "Transaction Approved on your Card **************** for LKR 771.51 at PICKME RIDE Available Bal LKR 16730.45  Call 0114315315 for any inquiry.",
                sender = "NationsSMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("771.51"),
                    currency = "LKR",
                    type = TransactionType.CREDIT,
                    merchant = "PICKME RIDE",
                    balance = BigDecimal("16730.45")
                )
            ),
            ParserTestCase(
                name = "Card bill settlement is skipped (not a spend)",
                message = "Thank you for your payment of LKR 57,018.67 made to Card # **************** on 27-06-2026.",
                sender = "NationsSMS",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "NationsSMS" to true,
            "AD-NATIONSSMS" to true,
            "HDFCBK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Nations Trust Bank Parser"
        )
    }
}
