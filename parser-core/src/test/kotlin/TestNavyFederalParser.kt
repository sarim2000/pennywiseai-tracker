import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.NavyFederalParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class NavyFederalParserTest {

    @TestFactory
    fun `navy federal parser handles primary formats`(): List<DynamicTest> {
        val parser = NavyFederalParser()

        ParserTestUtils.printTestHeader(
            parserName = "Navy Federal Credit Union",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit card transaction at Google One",
                message = "NFCU: Transaction for \$3.26 was approved on debit card 1234 at Google One at 08:19 PM EDT on 05/06/25.Txt STOP to opt-out. Txt HELP for help.",
                sender = "NFCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3.26"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    merchant = "Google One",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Small debit card transaction",
                message = "NFCU: Transaction for \$5.99 was approved on debit card 5678 at Amazon at 02:30 PM EST on 05/07/25.Txt STOP to opt-out. Txt HELP for help.",
                sender = "NFCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    merchant = "Amazon",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Large debit card transaction",
                message = "NFCU: Transaction for \$1,250.00 was approved on debit card 9012 at Best Buy at 11:45 AM PST on 05/08/25.Txt STOP to opt-out. Txt HELP for help.",
                sender = "NFCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9012",
                    merchant = "Best Buy",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Credit card transaction",
                message = "NFCU: Transaction for \$45.75 was approved on credit card 3456 at Target at 04:15 PM EDT on 05/09/25.Txt STOP to opt-out. Txt HELP for help.",
                sender = "NFCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.75"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3456",
                    merchant = "Target",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Merchant with multiple words",
                message = "NFCU: Transaction for \$89.99 was approved on debit card 7890 at Whole Foods Market at 06:30 PM EST on 05/10/25.Txt STOP to opt-out. Txt HELP for help.",
                sender = "NFCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("89.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "7890",
                    merchant = "Whole Foods Market",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Transaction at gas station",
                message = "NFCU: Transaction for \$65.00 was approved on debit card 2468 at Shell Gas Station at 07:45 AM EDT on 05/11/25.Txt STOP to opt-out. Txt HELP for help.",
                sender = "NFCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("65.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "2468",
                    merchant = "Shell Gas Station",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Restaurant transaction",
                message = "NFCU: Transaction for \$125.50 was approved on credit card 1357 at Olive Garden at 07:00 PM EST on 05/12/25.Txt STOP to opt-out. Txt HELP for help.",
                sender = "NFCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("125.50"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1357",
                    merchant = "Olive Garden",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Online subscription",
                message = "NFCU: Transaction for \$9.99 was approved on credit card 9753 at Netflix at 03:00 AM EDT on 05/13/25.Txt STOP to opt-out. Txt HELP for help.",
                sender = "NFCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("9.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9753",
                    merchant = "Netflix",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Alternative sender NAVYFED",
                message = "NAVYFED: Transaction for \$15.00 was approved on debit card 8642 at Starbucks at 08:30 AM EST on 05/14/25.Txt STOP to opt-out. Txt HELP for help.",
                sender = "NAVYFED",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "8642",
                    merchant = "Starbucks",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Grocery store transaction",
                message = "NFCU: Transaction for \$234.56 was approved on debit card 1111 at Kroger at 05:15 PM EDT on 05/15/25.Txt STOP to opt-out. Txt HELP for help.",
                sender = "NFCU",
                expected = ExpectedTransaction(
                    amount = BigDecimal("234.56"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1111",
                    merchant = "Kroger",
                    isFromCard = true
                )
            )
        )

        val handleChecks = listOf(
            "NFCU" to true,
            "NAVYFED" to true,
            "NAVY FEDERAL" to true,
            "NAVYFEDERAL" to true,
            "US-NFCU-A" to true,
            "UNKNOWN" to false,
            "HDFC" to false,
            "SCHWAB" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Navy Federal Credit Union Parser"
        )


    }

    @TestFactory
    fun `factory resolves navy federal`(): List<DynamicTest> {
        val cases = listOf(
            com.pennywiseai.parser.core.test.SimpleTestCase(
                bankName = "Navy Federal Credit Union",
                sender = "NFCU",
                currency = "USD",
                message = "NFCU: Transaction for \$25.00 was approved on debit card 1234 at Amazon at 02:00 PM EDT on 05/01/25.Txt STOP to opt-out. Txt HELP for help.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    merchant = "Amazon",
                    isFromCard = true
                ),
                shouldHandle = true
            ),
            com.pennywiseai.parser.core.test.SimpleTestCase(
                bankName = "Navy Federal Credit Union",
                sender = "NAVYFED",
                currency = "USD",
                message = "NAVYFED: Transaction for \$100.00 was approved on credit card 5678 at Target at 06:30 PM EST on 05/02/25.Txt STOP to opt-out. Txt HELP for help.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    merchant = "Target",
                    isFromCard = true
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Navy Federal Factory Tests")

    }
}
