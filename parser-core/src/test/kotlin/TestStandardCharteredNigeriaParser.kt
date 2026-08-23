import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.StandardCharteredNigeriaParser
import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class StandardCharteredNigeriaParserTest {

    @TestFactory
    fun `standard chartered nigeria parser handles common cases`(): List<DynamicTest> {
        val parser = StandardCharteredNigeriaParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Credit alert (INCOME)",
                message = "Credit Alert! Acct:xxxxxx1234, Amt:NGN1000.00, Desc:ADAEZE CHINWENDU OKAFOR 100000000000000000000000000000TEST I, Date:2026-08-24, Bal:NGN1500000.00",
                sender = "StanChart",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "ADAEZE CHINWENDU OKAFOR 100000000000000000000000000000TEST I",
                    accountLast4 = "1234",
                    balance = BigDecimal("1500000.00")
                )
            ),
            ParserTestCase(
                name = "Large debit alert (EXPENSE)",
                message = "Debit Alert! Acct:xxxxxx1234, Amt:NGN1000000.00, Desc:OKAFOR SAMUEL CHIDUBEM LENDING NG-013-000000-000000000-000000-, Date:2026-08-14, Bal:NGN1400000.00",
                sender = "StanChart",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000000.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "OKAFOR SAMUEL CHIDUBEM LENDING NG-013-000000-000000000-000000-",
                    accountLast4 = "1234",
                    balance = BigDecimal("1400000.00")
                )
            ),
            ParserTestCase(
                name = "Debit alert with trailing space in description",
                message = "Debit Alert! Acct:xxxxxx1234, Amt:NGN15000.00, Desc:AMINA FOLASADE ADELEKE RICE NG-013-000000-000000000-000000-000 , Date:2026-08-06, Bal:NGN800000.00",
                sender = "StanChart",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "NGN",
                    type = TransactionType.EXPENSE,
                    merchant = "AMINA FOLASADE ADELEKE RICE NG-013-000000-000000000-000000-000",
                    accountLast4 = "1234",
                    balance = BigDecimal("800000.00")
                )
            ),
            ParserTestCase(
                name = "Paystack credit alert (INCOME)",
                message = "Credit Alert! Acct:xxxxxx1234, Amt:NGN1200000.00, Desc:PAYSTACK 0000000000000TRANSFER FROM SAVINGSAPPSAVINGSAPP IL000000, Date:2026-08-06, Bal:NGN1600000.00",
                sender = "StanChart",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1200000.00"),
                    currency = "NGN",
                    type = TransactionType.INCOME,
                    merchant = "PAYSTACK 0000000000000TRANSFER FROM SAVINGSAPPSAVINGSAPP IL000000",
                    accountLast4 = "1234",
                    balance = BigDecimal("1600000.00")
                )
            )
        )

        val handleCases = listOf(
            "StanChart" to true,
            "SCBANK" to true,
            "STANDARDCHARTERED" to true,
            "GTBank" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Standard Chartered Nigeria Parser Suite"
        )
    }

    @Test
    fun `does not claim india pakistan standard chartered formats`() {
        val parser = StandardCharteredNigeriaParser()
        val indiaMsg = "Your a/c XX3421 is debited for Rs. 302.00 on 03-12-2025 15:49 and credited to a/c XX1465 (UPI Ref no 487597904232)"
        val pakistanMsg = "Dear Customer, PKR 55,000.00 sent to SCB PK A/C ****9901 for FUNDSTRANSFER 001 on 06-Feb-26 14:22 via RAAST"

        Assertions.assertNull(
            parser.parse(indiaMsg, "SCBANK", 0L),
            "Nigeria parser must not claim the India UPI format"
        )
        Assertions.assertNull(
            parser.parse(pakistanMsg, "SCBANK", 0L),
            "Nigeria parser must not claim the Pakistan RAAST format"
        )
    }

    @Test
    fun `factory dispatch routes nigeria and india messages to the right parsers`() {
        val nigeriaMsg = "Credit Alert! Acct:xxxxxx1234, Amt:NGN1000.00, Desc:TEST, Date:2026-08-24, Bal:NGN1500000.00"
        val indiaMsg = "Your a/c XX3421 is debited for Rs. 302.00 on 03-12-2025 15:49 and credited to a/c XX1465 (UPI Ref no 487597904232)"

        val ng = BankParserFactory.parse(nigeriaMsg, "SCBANK", 0L)
        Assertions.assertNotNull(ng, "Nigeria message should parse")
        Assertions.assertEquals("Standard Chartered Bank Nigeria", ng!!.bankName)
        Assertions.assertEquals("NGN", ng.currency)

        val ind = BankParserFactory.parse(indiaMsg, "SCBANK", 0L)
        Assertions.assertNotNull(ind, "India message should still parse despite the shared sender")
        Assertions.assertEquals("Standard Chartered Bank", ind!!.bankName)
    }

    @Test
    fun `description containing a comma is not truncated early`() {
        val parser = StandardCharteredNigeriaParser()
        val message = "Debit Alert! Acct:xxxxxx1234, Amt:NGN500.00, Desc:ACME LTD, LAGOS BRANCH, Date:2026-08-06, Bal:NGN1000.00"

        val parsed = parser.parse(message, "StanChart", 0L)
        Assertions.assertNotNull(parsed)
        Assertions.assertEquals(
            "ACME LTD, LAGOS BRANCH",
            parsed!!.merchant,
            "Desc must run to the Date: field, not stop at the first comma"
        )
    }
}
