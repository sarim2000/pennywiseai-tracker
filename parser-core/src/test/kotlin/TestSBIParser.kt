import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.SBIBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class SBIBankParserTest {

    @TestFactory
    fun `sbi parser handles debit alerts`(): List<DynamicTest> {
        val parser = SBIBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "SBI Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit card transaction",
                message = "Dear Customer, transaction number 1234 for Rs.383.00 by SBI Debit Card 0000 done at merchant on 13Sep25 at 21:38:26. Your updated available balance is Rs.999999999. If not done by you, forward this SMS to 7400165218/ call 1800111109/9449112211 to block card. GOI helpline for cyber fraud 1930.",
                sender = "ATMSBI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("383.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0000"
                )
            ),
            ParserTestCase(
                name = "Standard debit message",
                message = "Rs.500 debited from A/c X1234 on 13Sep25. Avl Bal Rs.999999999",
                sender = "ATMSBI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234"
                )
            )
        )

        val handleChecks = listOf(
            "ATMSBI" to true,
            "SBICRD" to true,
            "SBIBK" to true,
            "SBI CARDS AND PAYMENT SERVICES" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "SBI Parser"
        )
    }

    @TestFactory
    fun `sbi card parser handles unicode math sans-serif SMS`(): List<DynamicTest> {
        val parser = SBIBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "SBI Card Unicode Math Sans-Serif spending SMS",
                message = "Rs.90.00 𝗌𝗉𝖾𝗇𝗍 𝗈𝗇 𝗒𝗈𝗎𝗋 𝖲𝖡𝖨 𝖢𝗋𝖾𝖽𝗂𝗍 𝖢𝖺𝗋𝖽 𝖾𝗇𝖽𝗂𝗇𝗀 5667 at SUPREMEGOURMET on 13/02/26. 𝖳𝗋𝗑𝗇. 𝗇𝗈𝗍 𝖽𝗈𝗇𝖾 𝖻𝗒 𝗒𝗈𝗎? 𝖱𝖾𝗉𝗈𝗋𝗍 𝖺𝗍 https://sbicard.com/Dispute",
                sender = "SBI CARDS AND PAYMENT SERVICES",
                expected = ExpectedTransaction(
                    amount = BigDecimal("90.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "SUPREMEGOURMET",
                    accountLast4 = "5667",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "SBI Card standard credit card spending",
                message = "Rs.259.00 spent on your SBI Credit Card ending with 1234 on 15Jan26. Your available limit is Rs.1,235.00. If not done by you, call 39 02 02 02.",
                sender = "SBICRD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("259.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    accountLast4 = "1234",
                    isFromCard = true,
                    creditLimit = BigDecimal("1235.00")
                )
            ),
            ParserTestCase(
                name = "SBI Card payment credited",
                message = "Your payment of Rs.1,644.55 has been credited to your SBI Credit Card ending with 5667. Your available limit is Rs.48,355.45.",
                sender = "SBICRD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1644.55"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "5667",
                    isFromCard = true,
                    creditLimit = BigDecimal("48355.45")
                )
            )
        )

        val handleCases = emptyList<Pair<String, Boolean>>()

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "SBI Card"
        )
    }

    @TestFactory
    fun `factory resolves sbi card senders`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "State Bank of India",
                sender = "SBI CARDS AND PAYMENT SERVICES",
                currency = "INR",
                message = "Rs.90.00 spent on your SBI Credit Card ending 5667 at SUPREMEGOURMET on 13/02/26.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("90.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "SBI Card factory tests")
    }
}
