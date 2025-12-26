import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.SouthIndianBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class SouthIndianBankParserTest {

    @TestFactory
    fun `south indian bank parser basic flows`(): List<DynamicTest> {
        val parser = SouthIndianBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "South Indian Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "IMPS credit with reference",
                message = "Dear Customer, Your A/c X7377 is credited with Rs.792.02 Info: IMPS/FDRL/528005821348/EPIFI ACCOUN. Final balance is Rs.793.02-South Indian Bank",
                sender = "SIBSMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("792.02"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "EPIFI ACCOUN",
                    accountLast4 = "7377",
                    balance = BigDecimal("793.02"),
                    reference = "528005821348"
                )
            ),
            ParserTestCase(
                name = "UPI debit with RRN and balance",
                message = "UPI debit:Rs.599.00 A/c X7477, 16-10-25 16:25:29 RRN: 565526068910 Bal:Rs.12345.89 Block A/c? Call18004251809/SMS BLK<A/c>to 9840777222-South Indian Bank",
                sender = "SIBSMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("599.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "UPI Transaction",
                    accountLast4 = "7477",
                    balance = BigDecimal("12345.89"),
                    reference = "565526068910"
                )
            ),
            ParserTestCase(
                name = "Debit card usage",
                message = "A/c X7477 DEBIT:Rs.983.75 SPICE KITCHEN MCT Bal:Rs.1234.67 Block A/c? call 18004251809/SMS BLK<full A/c>to 9840777222-South Indian Bank",
                sender = "VM-SIBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("983.75"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "SPICE KITCHEN MCT",
                    accountLast4 = "7477",
                    balance = BigDecimal("1234.67"),
                    reference = null
                )
            ),
            ParserTestCase(
                name = "UPI debit with comma separator and RRN",
                message = "UPI debit:Rs.42225.06, A/c X7477, 03-11-25 00:12:50 RRN:567304295699. Bal:Rs.35037.21 Block A/c? Cal118004251809/SMS BLK<A/c>to 9840777222-South Indian Bank",
                sender = "SIBSMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("42225.06"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "UPI Transaction",
                    accountLast4 = "7477",
                    balance = BigDecimal("35037.21"),
                    reference = "567304295699"
                )
            )
        )

        val handleChecks = listOf(
            "SIBSMS" to true,
            "AD-SIBSMS" to true,
            "CP-SIBSMS" to true,
            "AD-SIBSMS-S" to true,
            "SIBBANK" to true,
            "AX-HDFC-S" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "South Indian Bank Parser"
        )


    }

    @TestFactory
    fun `factory resolves south indian bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "South Indian Bank",
                sender = "SIBSMS",
                currency = "INR",
                message = "Dear Customer, Your A/c X7377 is credited with Rs.792.02 Info: IMPS/FDRL/528005821348/EPIFI ACCOUN. Final balance is Rs.793.02-South Indian Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("792.02"),
                    currency = "INR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests - South Indian Bank")

    }
}
