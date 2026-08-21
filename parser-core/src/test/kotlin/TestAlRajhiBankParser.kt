import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.AlRajhiBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class AlRajhiBankParserTest {

    @TestFactory
    fun `al rajhi parser covers historical and safety variants`(): List<DynamicTest> {
        val parser = AlRajhiBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Historical English purchase with number-first SAR amount",
                message = "PoS Purchase\nBy:0007;mada\nAmount: 12.34 SAR\nAt: SYNTHETIC STORE\n01/01/2030 10:20",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC STORE",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Arabic purchase accepts number-first SR and labelled merchant",
                message = "شراء\nبـ5.75 SR\nالتاجر: SYNTHETIC MERCHANT\nرصيد: 88.88 SR\n01/01/2030 10:20",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.75"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MERCHANT",
                    balance = BigDecimal("88.88")
                )
            ),
            ParserTestCase(
                name = "Refund is classified as income",
                message = "Purchase Refund\nAmount: 7.89 SAR\nAt: SYNTHETIC REFUND STORE\n01/01/2030 10:20",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("7.89"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC REFUND STORE"
                )
            ),
            ParserTestCase(
                name = "Cashback credit is classified as income",
                message = "Cashback\nAmount: 2.50 SAR\nAt: SYNTHETIC CASHBACK\n01/01/2030 10:20",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.50"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC CASHBACK"
                )
            ),
            ParserTestCase(
                name = "Cashback reversal is classified as expense",
                message = "Cashback Reversal\nAmount: 2.50 SAR\nAt: SYNTHETIC CASHBACK\n01/01/2030 10:20",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.50"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC CASHBACK"
                )
            ),
            ParserTestCase(
                name = "PoS refund overrides purchase wording",
                message = "PoS Purchase Refund\nBy:0008;mada\nAmount: 3.25 SAR\nAt: SYNTHETIC POS REFUND\n01/01/2030 10:20",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3.25"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC POS REFUND",
                    accountLast4 = "0008",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Arabic cashback reversal is an expense",
                message = "كاش باك عكس\nمبلغ: SAR 2.50\nالتاجر: SYNTHETIC CASHBACK\n01/01/2030 10:20",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.50"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC CASHBACK"
                )
            ),
            ParserTestCase(
                name = "Operational refund wording is not a transaction",
                message = "Help: refunds are processed within 5 days.\nAmount: SAR 9.99\nAt: SYNTHETIC HELP DESK",
                sender = "AlRajhiBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Incidental number-first SAR text is not an amount field",
                message = "شراء\nمعلومة: ب 99.99 SR هو الحد المتاح\nالتاجر: SYNTHETIC INFORMATION\n01/01/2030 10:20",
                sender = "AlRajhiBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined historical purchase is ignored",
                message = "PoS Purchase Declined\nBy:0007;mada\nAmount: 12.34 SAR\nAt: SYNTHETIC STORE\n01/01/2030 10:20",
                sender = "AlRajhiBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Fee-only notice is not a transaction",
                message = "Fee Notice\nFee: SAR 0.50\nAt: SYNTHETIC SERVICE\n01/01/2030 10:20",
                sender = "AlRajhiBank",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            suiteName = "Al Rajhi Historical and Safety Suite"
        )
    }

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
            ),
            ParserTestCase(
                name = "English PoS purchase (alpha merchant)",
                message = "PoS Purchase\nBy:4196;mada(Google Pay)\nAmount:SR 2\nAt:FAST WAY\n26/6/19 23:54",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "FAST WAY",
                    accountLast4 = "4196",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "English PoS purchase (terminal id + city)",
                message = "PoS Purchase\nBy:4196;mada(Google Pay)\nAmount:SR 4\nAt:170658 riyadh\n26/6/18 11:48",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "riyadh",
                    accountLast4 = "4196",
                    isFromCard = true
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
