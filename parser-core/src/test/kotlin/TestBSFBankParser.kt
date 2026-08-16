import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BSFBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class BSFBankParserTest {

    @TestFactory
    fun `bsf parser handles common cases`(): List<DynamicTest> {
        val parser = BSFBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "Outgoing transfer (debit) — amount not fee",
                message = "عملية حوالة مالية صادرة مقبولة\n" +
                        "خصمت من حساب ****0136\n" +
                        "إلى RECIPIENT NAME****\n" +
                        "بنك إس تي سي\n" +
                        "آيبان ****8287\n" +
                        "القيمة SAR 505.00\n" +
                        "الرسوم SAR 0.58\n" +
                        "في28-07-2026 14:33:53",
                sender = "BSF",
                expected = ExpectedTransaction(
                    amount = BigDecimal("505.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "RECIPIENT NAME",
                    reference = "****8287",
                    accountLast4 = "0136"
                )
            ),
            ParserTestCase(
                name = "Incoming transfer (credit)",
                message = "حوالة واردة\n" +
                        "مبلغ: 359.0 SAR\n" +
                        "لـ:SA**********R1******8\n" +
                        "من: ACME TRADING COMPANY\n" +
                        "آيبان: ********************4318\n" +
                        "GULF\n" +
                        "في: 04-08-2026 15:51",
                sender = "BSF",
                expected = ExpectedTransaction(
                    amount = BigDecimal("359.0"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "ACME TRADING COMPANY",
                    reference = "********************4318"
                )
            )
        )

        val handleChecks = listOf(
            "BSF" to true,
            "AD-BSF-S" to true,
            "STC" to false,
            "SAB" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "BSF Bank Parser")
    }
}
