import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.OldHickoryParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class OldHickoryParserTest {

    @TestFactory
    fun `old hickory parser handles expected scenarios`(): List<DynamicTest> {
        val parser = OldHickoryParser()

        ParserTestUtils.printTestHeader(
            parserName = "Old Hickory Credit Union",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Standard transaction alert",
                message = "A transaction for $27.00 has posted to ACCOUNT NAME (part of ACCOUNT#), which is above the $0.00 value you set.",
                sender = "(877) 590-7589",
                expected = ExpectedTransaction(
                    amount = BigDecimal("27.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Account: ACCOUNT NAME",
                    accountLast4 = "ACCOUNT#",
                    reference = "Alert threshold: $0.00"
                )
            ),
            ParserTestCase(
                name = "Numeric sender format",
                message = "A transaction for $150.50 has posted to SAVINGS ACCOUNT (part of SAV1234), which is above the $100.00 value you set.",
                sender = "8775907589",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.50"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Account: SAVINGS ACCOUNT",
                    accountLast4 = "1234",
                    reference = "Alert threshold: $100.00"
                )
            ),
            ParserTestCase(
                name = "Text sender format",
                message = "A transaction for $75.25 has posted to CHECKING ACCOUNT (part of CHK5678), which is above the $50.00 value you set.",
                sender = "OLDHICKORY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.25"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Account: CHECKING ACCOUNT",
                    accountLast4 = "5678",
                    reference = "Alert threshold: $50.00"
                )
            ),
            ParserTestCase(
                name = "Large amount with commas",
                message = "A transaction for $1,250.00 has posted to BUSINESS CHECKING (part of BUS9999), which is above the $1,000.00 value you set.",
                sender = "OHCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Account: BUSINESS CHECKING",
                    accountLast4 = "9999",
                    reference = "Alert threshold: $1,000.00"
                )
            ),
            ParserTestCase(
                name = "Alternative sender format",
                message = "A transaction for $42.99 has posted to CREDIT CARD (part of CC1111), which is above the $25.00 value you set.",
                sender = "US-HICKORY-A",
                expected = ExpectedTransaction(
                    amount = BigDecimal("42.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Account: CREDIT CARD",
                    accountLast4 = "1111",
                    reference = "Alert threshold: $25.00"
                )
            ),
            ParserTestCase(
                name = "Zero threshold",
                message = "A transaction for $5.00 has posted to YOUTH SAVINGS (part of YS2222), which is above the $0.00 value you set.",
                sender = "(877) 590-7589",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Account: YOUTH SAVINGS",
                    accountLast4 = "2222",
                    reference = "Alert threshold: $0.00"
                )
            )
        )

        val handleChecks = listOf(
            "(877) 590-7589" to true,
            "8775907589" to true,
            "OLDHICKORY" to true,
            "OHCU" to true,
            "US-HICKORY-A" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Old Hickory Parser Tests"
        )


    }
}
