import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BandhanBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class BandhanBankParserTest {

    @TestFactory
    fun `bandhan bank parser handles provided samples`(): List<DynamicTest> {
        val parser = BandhanBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Bandhan Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val transactionCases = listOf(
            // User-provided test cases from GitHub issue
            ParserTestCase(
                name = "UPI Debit to Amazon Pay (User Issue)",
                message = "INR 180.00 debited from A/c XXXXXXXXXX1234 towards UPI/DR/D123013240123/Amazon Pa Value 16-NOV-2025 . Clear Bal is INR 9999.99. Bandhan Bank",
                sender = "XY-BDNSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("180.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Amazon Pa",
                    reference = "D123013240123",
                    accountLast4 = "1234",
                    balance = BigDecimal("9999.99"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "UPI Debit to Tanuska E (User Issue)",
                message = "INR 5.00 debited from A/c XXXXXXXXXX1234 towards UPI/DR/D123290818021/Tanuska E Value 10-NOV-2025 . Clear Bal is INR 9999.76. Bandhan Bank",
                sender = "XY-BDNSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Tanuska E",
                    reference = "D123290818021",
                    accountLast4 = "1234",
                    balance = BigDecimal("9999.76"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "UPI Debit to Amazon In (User Issue)",
                message = "INR 92.00 debited from A/c XXXXXXXXXX1234 towards UPI/DR/D512361791780/Amazon In Value 10-OCT-2025 . Clear Bal is INR 9999.70. Bandhan Bank",
                sender = "XY-BDNSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("92.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Amazon In",
                    reference = "D512361791780",
                    accountLast4 = "1234",
                    balance = BigDecimal("9999.70"),
                    isFromCard = false
                )
            ),

            // Additional coverage cases
            ParserTestCase(
                name = "Interest credit",
                message = "Dear Customer, your account XXXXXXXXXX1234 is credited with INR 3.00 on 01-OCT-2025 towards interest. Bandhan Bank",
                sender = "XY-BDNSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Interest",
                    accountLast4 = "1234",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "UPI credit with balance",
                message = "INR 25,000.00 deposited to A/c XXXXXXXXXX1234 towards UPI/CR/C224513287910/JOHN DOE/u on 03-OCT-2025 . Clear Bal is INR 30,123.00 . Bandhan Bank.",
                sender = "XY-BDNSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "JOHN DOE",
                    reference = "C224513287910",
                    accountLast4 = "1234",
                    balance = BigDecimal("30123.00"),
                    isFromCard = false
                )
            ),

            // Large amount tests
            ParserTestCase(
                name = "Large Amount Debit",
                message = "INR 50,000.00 debited from A/c XXXXXXXXXX5678 towards UPI/DR/D999888777666/Big Purchase Value 18-NOV-2025 . Clear Bal is INR 100,000.50. Bandhan Bank",
                sender = "AD-BDNSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Big Purchase",
                    reference = "D999888777666",
                    accountLast4 = "5678",
                    balance = BigDecimal("100000.50"),
                    isFromCard = false
                )
            ),

            // Different sender patterns
            ParserTestCase(
                name = "Transaction with BANDHAN sender",
                message = "INR 1000.00 debited from A/c XXXXXXXXXX9876 towards UPI/DR/D111222333444/Store XYZ Value 20-NOV-2025 . Clear Bal is INR 15000.00. Bandhan Bank",
                sender = "BANDHAN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Store XYZ",
                    reference = "D111222333444",
                    accountLast4 = "9876",
                    balance = BigDecimal("15000.00"),
                    isFromCard = false
                )
            )
        )

        val rejectionCases = listOf(
            ParserTestCase(
                name = "OTP message",
                message = "Your Bandhan Bank OTP is 123456. Do not share with anyone.",
                sender = "XY-BDNSMS-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotional message",
                message = "Bandhan Bank offers 7% interest on fixed deposits. Visit branch today!",
                sender = "XY-BDNSMS-S",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = transactionCases + rejectionCases,
            handleCases = listOf(
                "XY-BDNSMS" to true,
                "AM-BDNSMS-S" to true,
                "BP-BANDHN-S" to true,
                "VM-BOIIND-S" to false
            ),
            suiteName = "Bandhan Bank Parser"
        )


    }
}

