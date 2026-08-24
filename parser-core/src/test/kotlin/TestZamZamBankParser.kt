import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.ZamZamBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class ZamZamBankParserTest {

    @TestFactory
    fun `zamzam parser handles credit and debit`(): List<DynamicTest> {
        val parser = ZamZamBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "ZamZam Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Credit transaction with counterparty name",
                message = "Dear Customer, your account ****1234 has been credited by John Doe with ETB 5,000.00 on 2025-12-02. Your current balance is ETB 5,000.00. Thank you for banking with us!",
                sender = "ZamZam Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "John Doe",
                    accountLast4 = "1234",
                    balance = BigDecimal("5000.00")
                )
            ),
            ParserTestCase(
                name = "Debit transaction with whole amount",
                message = "Dear John Doe, your account ***1234 has been debited with ETB 32000 on 2026-01-14 00:00:00.0. Your current balance is ETB 2000. Thank you for banking with us!",
                sender = "ZamZam Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("32000.00"),
                    currency = "ETB",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("2000.00")
                )
            )
        )

        val handleChecks = listOf(
            "ZamZam Bank" to true,
            "ZAMZAMBANK" to true,
            "AB-ZAMZAM-S" to true,
            "DASHENBANK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "ZamZam Bank Parser"
        )
    }
}
