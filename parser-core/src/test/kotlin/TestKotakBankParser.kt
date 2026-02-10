import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.KotakBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class KotakBankParserTest {

    @TestFactory
    fun `kotak parser handles UPI transactions with payment app QR codes`(): List<DynamicTest> {
        val parser = KotakBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Kotak Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Paytm QR code transaction",
                message = "Sent Rs.15.00 from Kotak Bank AC X1234 to paytmqr288005050101t74afkchmxjd@paytm on 14-10-25.UPI Ref 1234567890. Not you, https://kotak.com/KBANKT/Fraud",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Paytm",
                    reference = "1234567890",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "PhonePe QR code transaction",
                message = "Sent Rs.100.00 from Kotak Bank AC X5678 to phonepeqr123456789xyz@ybl on 15-10-25.UPI Ref 9876543210. Not you, https://kotak.com/KBANKT/Fraud",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "PhonePe",
                    reference = "9876543210",
                    accountLast4 = "5678"
                )
            ),
            ParserTestCase(
                name = "Person-to-person UPI with phone number",
                message = "Sent Rs.500.00 from Kotak Bank AC X9999 to 9876543210@paytm on 15-10-25.UPI Ref 1111111111. Not you, https://kotak.com/KBANKT/Fraud",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "9876543210",
                    reference = "1111111111",
                    accountLast4 = "9999"
                )
            ),
            ParserTestCase(
                name = "UPI received transaction",
                message = "Received Rs.250.00 in your Kotak Bank AC X3333 from john.doe@oksbi on 14-10-25.UPI Ref 2222222222. Not you, https://kotak.com/KBANKT/Fraud",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "john.doe",
                    reference = "2222222222",
                    accountLast4 = "3333"
                )
            ),
            ParserTestCase(
                name = "Standard debit message",
                message = "Rs.1000.00 debited from your Kotak Bank AC X4444 on 15-10-25. Avl Bal Rs.10000.00",
                sender = "JD-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4444",
                    balance = BigDecimal("10000.00")
                )
            ),
            ParserTestCase(
                name = "Credit card spending with available limit",
                message = "INR 20 spent on Kotak Credit Card x5236 on 23-JAN-2026 at UPI-638903921672-CORN. Avl limit INR 73733.02 Fraud? https://www.kotak.bank.in/KBANKT/querytxn",
                sender = "TX-KOTAKB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "CORN",
                    accountLast4 = "5236",
                    isFromCard = true,
                    creditLimit = BigDecimal("73733.02")
                )
            )
        )

        val handleChecks = listOf(
            "JD-KOTAKB-S" to true,
            "JD-KOTAKB-T" to true,
            "VM-KOTAKB" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Kotak Bank Parser"
        )


    }
}
