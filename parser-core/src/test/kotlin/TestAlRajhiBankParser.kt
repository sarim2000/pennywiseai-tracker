import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.AlRajhiBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class AlRajhiBankParserTest {

    @TestFactory
    fun `al rajhi parser covers representative scenarios`(): List<DynamicTest> {
        val parser = AlRajhiBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Card purchase via Google Pay",
                message = "شراء\nعبر:****;مدى-جوجل باي\nبـSAR 5.75\nلـKiwi food suppl\n26/3/9 16:46",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.75"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "Kiwi food suppl",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Online purchase via Mada",
                message = "شراء انترنت\nعبر:****;مدى\nمن:****\nبـSAR 140\nلـbarq\n6/3/26 04:06",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("140"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "barq",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal",
                message = "سحب:صراف آلي\nبطاقة:****;مدى\nمبلغ:SAR 100\nمكان السحب:NORTHERN REGION\n5/3/26 04:20",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "NORTHERN REGION",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Outgoing local transfer",
                message = "حوالة محلية صادرة\nمصرف:ANB\nمن:****\nمبلغ:SAR 100\nالى:BARQ SAFE ACCOUNT\nالى:****\nالرسوم:SAR 0.58\n26/3/3 22:44",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "BARQ SAFE ACCOUNT"
                )
            ),
            ParserTestCase(
                name = "Incoming internal transfer",
                message = "حوالة داخلية واردة\nبـSAR 1170\nلـ****\nمن****;Ahmad\n26/3/1 09:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1170"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "Ahmad"
                )
            ),
            ParserTestCase(
                name = "Loan installment deduction",
                message = "خصم: قسط تمويل\nالقسط: 2304.58 SAR\nمن: ****\nالمبلغ المتبقي: SAR 13827.48\n26/2/26 16:39",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2304.58"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("13827.48")
                )
            ),
            ParserTestCase(
                name = "Incoming local transfer (salary)",
                message = "حوالة محلية واردة\nعبر:SAUDI ARABIAN MONETARY AUTHORITY\nمبلغ:SAR 7714.80\nالى:****\nمن:ACME CORP\n26/2/26 00:42",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("7714.80"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "ACME CORP"
                )
            ),
            ParserTestCase(
                name = "Outgoing internal transfer",
                message = "حوالة داخلية صادرة\nمن****\nبـSAR 200\nلـ****; Ahmad\n26/3/2 23:56",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                )
            )
        )

        val handleCases = listOf(
            "AlRajhiBank" to true,
            "ALRAJHI" to true,
            "الراجحي" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Al Rajhi Bank Parser Suite"
        )
    }
}
