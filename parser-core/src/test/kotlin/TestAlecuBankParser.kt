import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.AlecuBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class AlecuBankParserTest {

    @TestFactory
    fun `alecu parser covers representative scenarios`(): List<DynamicTest> {
        val parser = AlecuBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Debit transaction - withdrawal",
                message = "ALEC Alert - A debit transaction from WITHDRAWAL for \$100.00 on account *1=01 was posted on Mar 30, 2026. Reply STOP to stop all texts.",
                sender = "39872",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "WITHDRAWAL",
                    accountLast4 = "101"
                )
            ),
            ParserTestCase(
                name = "Debit transaction - autopay with semicolons",
                message = "ALEC Alert - A debit transaction from WE EGIES     ;12345 ;AUTOPAY for \$177.33 on account *1=01 was posted on Mar 30, 2026. Reply STOP to stop all texts.",
                sender = "39872",
                expected = ExpectedTransaction(
                    amount = BigDecimal("177.33"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "WE EGIES",
                    accountLast4 = "101"
                )
            )
        )

        val handleCases = listOf(
            "39872" to true,
            "ALECU" to true,
            "ALEC" to true,
            "CHASE" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "ALECU Bank Parser Suite"
        )
    }
}
