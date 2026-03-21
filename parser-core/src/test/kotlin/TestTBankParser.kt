import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.TBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class TBankParserTest {

    @TestFactory
    fun `t-bank parser covers representative scenarios`(): List<DynamicTest> {
        val parser = TBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "ATM deposit",
                message = "Пополнение, счет RUB. 5000 ₽. Банкомат. Доступно 10028,05 ₽",
                sender = "Tinkoff",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000"),
                    currency = "RUB",
                    type = TransactionType.INCOME,
                    merchant = "Банкомат",
                    balance = BigDecimal("10028.05")
                )
            ),
            ParserTestCase(
                name = "Card purchase at gas station",
                message = "Покупка, счет карты *1023. 3267 ₽. AZS 09117. Доступно 30672,14 ₽",
                sender = "Tinkoff",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3267"),
                    currency = "RUB",
                    type = TransactionType.EXPENSE,
                    merchant = "AZS 09117",
                    accountLast4 = "1023",
                    balance = BigDecimal("30672.14"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Outgoing transfer",
                message = "Перевод. Счет RUB. 250 ₽. Милана Н. Баланс 0 ₽",
                sender = "TBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250"),
                    currency = "RUB",
                    type = TransactionType.EXPENSE,
                    merchant = "Милана Н",
                    balance = BigDecimal("0")
                )
            )
        )

        val handleCases = listOf(
            "Tinkoff" to true,
            "TBANK" to true,
            "T-BANK" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "T-Bank Parser Suite"
        )
    }
}
