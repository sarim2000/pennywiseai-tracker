import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.AUBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class AUBankParserTest {

    @TestFactory
    fun `au bank parser covers representative scenarios`(): List<DynamicTest> {
        val parser = AUBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "UPI debit with DR format",
                message = "Debited INR 165.00 from A/c X7013 on 01-MAR-2026\n" +
                    "UPI/DR/651122781360/UK FOOD/UTIB/9180201\n" +
                    "Bal INR 900000000\n" +
                    "Not you? Call 180012001200 & dial 0\n" +
                    "-AU Bank",
                sender = "VM-AUBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("165.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "UK FOOD",
                    accountLast4 = "7013",
                    balance = BigDecimal("900000000")
                )
            ),
            ParserTestCase(
                name = "Basic debit without UPI",
                message = "Debited INR 500.00 from A/c 1234567890 on 15-FEB-2026. Bal INR 10000.00. -AU Bank",
                sender = "VM-AUBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "7890",
                    balance = BigDecimal("10000.00")
                )
            ),
            ParserTestCase(
                name = "Credit card spend",
                message = "INR 259.90 spent at TELEGRAM PREMIUM on AU Bank Credit Card x1234 21-03-2026 05:49:40 PM. Not you? Call 180012001500 or SMS PBLOCK 1234 to 5676767",
                sender = "VM-AUBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("259.90"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "TELEGRAM PREMIUM",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Short Dr form with placeholder merchant",
                message = "Dr INR 29,000.00 from A/c X7661 on 05-MAY-2026\n" +
                    "UPI/DR/012345678901/Bank Account XXXXX\n" +
                    "Bal INR 10,952.10\n" +
                    "Not you? Call 180012001200 & dial 0\n" +
                    "-AU Bank",
                sender = "VM-AUBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("29000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "7661",
                    balance = BigDecimal("10952.10")
                )
            ),
            ParserTestCase(
                name = "Short Dr form with merchant name",
                message = "Dr INR 10.00 - AU A/c X4541 12-MAY-2026\n" +
                    "UPI/DR/424941009000/ALADDIN SHAWARMA/Y\n" +
                    "Bal INR 18,838.10\n" +
                    "Fraud? Call 180012001200/SMS BLOCK UPI to 5676767",
                sender = "VM-AUBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "ALADDIN SHAWARMA",
                    accountLast4 = "4541",
                    balance = BigDecimal("18838.10")
                )
            ),
            ParserTestCase(
                name = "Short Cr form is INCOME",
                message = "Cr INR 5,000.00 to A/c X4541 12-MAY-2026\n" +
                    "UPI/CR/424941009999/JANE DOE/Y\n" +
                    "Bal INR 23,838.10\n" +
                    "-AU Bank",
                sender = "VM-AUBANK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "JANE DOE",
                    accountLast4 = "4541",
                    balance = BigDecimal("23838.10")
                )
            )
        )

        val handleChecks = listOf(
            "VM-AUBANK" to true,
            "AUBANK" to true,
            "HDFC" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "AU Bank Parser Suite"
        )
    }
}
