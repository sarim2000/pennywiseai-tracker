import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class AllNewParsersTest {

    @TestFactory
    fun `factory resolves new session parsers`(): List<DynamicTest> {
        val fabMessage = """
        Credit Card Purchase
        Card No XXXX
        AED 8.00
        T*** R** DUBAI ARE
        23/09/25 16:17
        Available Balance AED **30.16
    """.trimIndent()

        val citiMessage =
            "Citi Alert: A $3.01 transaction was made at BP#1234E on card ending in 1234. View details at citi.com/citimobileapp"

        val discoverMessage =
            "Discover Card Alert: A transaction of $25.00 at WWW.XXX.ORG on February 21, 2025. No Action needed. See it at https://app.discover.com/ACTVT. Text STOP to end"

        val laxmiMessage =
            "Dear Customer, Your #12344560 has been debited by NPR 720.00 on 05/09/25. Remarks:ESEWA LOAD/9763698550,127847587\n-Laxmi Sunrise"

        val cbeMessage =
            "Dear [Name] your Account 1*********9388 has been Credited with ETB 3,000.00 from Be***, on 13/09/2025 at 12:37:24 with Ref No ********* Your Current Balance is ETB 3,104.87. Thank you for Banking with CBE!"

        val everestMessage =
            "Dear Customer, Your A/c 12345678 is debited by NPR 520.00 For: 9843368/Mobile Recharge,Ncell. Never Share Password/OTP With Anyone"

        val oldHickoryMessage =
            "A transaction for $27.00 has posted to ACCOUNT NAME (part of ACCOUNT#), which is above the $0.00 value you set."

        val factoryCases = listOf(
            SimpleTestCase(
                bankName = "First Abu Dhabi Bank",
                sender = "FAB",
                currency = "AED",
                message = fabMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("8.00"),
                    currency = "AED",
                    type = TransactionType.CREDIT,
                    merchant = "T R DUBAI ARE"
                ),
                shouldHandle = true,
                description = "FAB sample"
            ),
            SimpleTestCase(
                bankName = "Citi Bank",
                sender = "692484",
                currency = "USD",
                message = citiMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("3.01"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "BP#1234E",
                    accountLast4 = "1234"
                ),
                shouldHandle = true,
                description = "Citi Bank sample"
            ),
            SimpleTestCase(
                bankName = "Discover Card",
                sender = "347268",
                currency = "USD",
                message = discoverMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "WWW.XXX.ORG",
                    reference = "February 21, 2025"
                ),
                shouldHandle = true,
                description = "Discover Card sample"
            ),
            SimpleTestCase(
                bankName = "Laxmi Sunrise Bank",
                sender = "LAXMI_ALERT",
                currency = "NPR",
                message = laxmiMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("720.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "ESEWA",
                    accountLast4 = "4560",
                    reference = "05/09/25"
                ),
                shouldHandle = true,
                description = "Laxmi Sunrise sample"
            ),
            SimpleTestCase(
                bankName = "Commercial Bank of Ethiopia",
                sender = "CBE",
                currency = "ETB",
                message = cbeMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "ETB",
                    type = TransactionType.INCOME,
                    merchant = "Be",
                    accountLast4 = "9388",
                    balance = BigDecimal("3104.87")
                ),
                shouldHandle = true,
                description = "CBE sample"
            ),
            SimpleTestCase(
                bankName = "Everest Bank",
                sender = "9843368",
                currency = "NPR",
                message = everestMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("520.00"),
                    currency = "NPR",
                    type = TransactionType.EXPENSE,
                    merchant = "Mobile Recharge",
                    accountLast4 = "5678"
                ),
                shouldHandle = true,
                description = "Everest Bank sample"
            ),
            SimpleTestCase(
                bankName = "Old Hickory Credit Union",
                sender = "(877) 590-7589",
                currency = "USD",
                message = oldHickoryMessage,
                expected = ExpectedTransaction(
                    amount = BigDecimal("27.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "Account: ACCOUNT NAME",
                    accountLast4 = "ACCOUNT#",
                    reference = "Alert threshold: $0.00"
                ),
                shouldHandle = true,
                description = "Old Hickory sample"
            )
        )

        return ParserTestUtils.runFactoryTestSuite(factoryCases, "Factory smoke tests")
    }
}
