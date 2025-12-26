import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.FABParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class FABParserTest {

    @TestFactory
    fun `fab parser covers representative scenarios`(): List<DynamicTest> {
        val parser = FABParser()

        ParserTestUtils.printTestHeader(
            parserName = "First Abu Dhabi Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Credit card purchase",
                message = """
                Credit Card Purchase
                Debit
                Account XXXX0002
                Card XXXX1234
                AED 8.00
                TR              DUBAI           ARE
                23/09/25 16:17
                Available Balance AED 4530.16
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8.00"),
                    currency = "AED",
                    type = TransactionType.CREDIT,
                    merchant = "TR              DUBAI           ARE",
                    accountLast4 = "0002",
                    balance = BigDecimal("4530.16"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Inward remittance",
                message = """
                Inward Remittance
                Credit
                Account XXXX5678
                AED 444.00
                Value Date 18/09/2025
                Available Balance AED 5444.00
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("444.00"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("5444.00")
                )
            ),
            ParserTestCase(
                name = "Payment instructions",
                message = "Dear Customer, Your payment instructions of AED 250.00 to 5xxx**1xxx has been processed on 10/09/2025 15:28",
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "Debit card purchase (THB)",
                message = """
                Debit Card Purchase
                Debit
                Account XXXX9876
                Card XXXX2865
                THB 1500.50
                WWW.GRAB.COM          BANGKOK         TH
                26/07/25 17:55
                Available Balance AED 8500.25
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.50"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    merchant = "WWW.GRAB.COM          BANGKOK         TH",
                    accountLast4 = "9876",
                    balance = BigDecimal("8500.25"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ATM cash withdrawal (THB)",
                message = """
                ATM Cash withdrawal
                Debit
                Account XXXX4321
                Card XXXX8765
                THB 5000.00
                18/06/25 13:06
                Available Balance AED 15000.00
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    accountLast4 = "4321",
                    balance = BigDecimal("15000.00"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Grab payment (THB)",
                message = """
                Debit Card Purchase 
                Debit 
                Account XXXX0002 
                Card XXXX2865
                THB 283.00
                WWW.GRAB.COM          BANGKOK         TH 
                26/06/25 11:51 
                Available Balance AED 9999.00
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("283.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    merchant = "WWW.GRAB.COM          BANGKOK         TH",
                    accountLast4 = "0002",
                    balance = BigDecimal("9999.00"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Outward remittance",
                message = """
                Outward Remittance
                Debit
                Account XXXX0002
                AED 150.00
                Value Date 10/07/24
                Available Balance AED 6337.92
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0002",
                    balance = BigDecimal("6337.92")
                )
            ),
            ParserTestCase(
                name = "USD payment",
                message = """
                Debit Card Purchase
                Debit
                Account XXXX0002
                Card XXXX9879
                USD 20.00
                CLAUDE.AI SUBSCRIPTION+14152360599 CA US
                10/07/24 20:07
                Available Balance AED 5978.78
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "CLAUDE.AI SUBSCRIPTION+14152360599 CA US",
                    accountLast4 = "0002",
                    balance = BigDecimal("5978.78"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Grab payment in Thailand",
                message = """
                Debit Card Purchase 
                Debit 
                Account XXXX1234
                Card XXXX5678
                THB 636.00
                WWW.GRAB.COM          BANGKOK         TH 
                26/07/25 17:55 
                Available Balance AED 8888.30
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("636.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    merchant = "WWW.GRAB.COM          BANGKOK         TH",
                    accountLast4 = "1234",
                    balance = BigDecimal("8888.30"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Outward remittance without merchant",
                message = """
                Outward Remittance 
                Debit 
                Account XXXX9876
                AED 500.00
                Value Date 20/06/2025  
                Available Balance AED 12345.67
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9876",
                    balance = BigDecimal("12345.67")
                )
            ),
            ParserTestCase(
                name = "Cash deposit income",
                message = """
                Cash Deposit 
                Credit 
                Account XXXX4321
                AED 10000.00
                Date 20/06/25
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    accountLast4 = "4321"
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal in Thailand",
                message = """
                ATM Cash withdrawal 
                Debit 
                Account XXXX8888
                Card XXXX9999
                THB 3000.00
                18/06/25 13:06 
                Available Balance AED 5500.50
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "THB",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    accountLast4 = "8888",
                    balance = BigDecimal("5500.50"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Cheque credited",
                message = """
        Cheque Credited
        Cheque No 000020 for AED 9999.00 deposited in your account XXXX0002 has been credited on 01/10/2024 
        Your available balance is AED 7777.62.
    """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("9999.00"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    merchant = "Cheque Credited",
                    accountLast4 = "0002",
                    balance = BigDecimal("7777.62")
                )
            ),
            ParserTestCase(
                name = "Cheque returned",
                message = """
        Cheque Returned
        Cheque No 000020 for AED 8888.00 deposited in your account XXXX0002  has been returned unpaid.
        Please contact the branch.
    """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8888.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    merchant = "Cheque Returned",
                    accountLast4 = "0002"
                )
            ),
            ParserTestCase(
                name = "Unsuccessful transaction refund",
                message = """
        Dear customer, unsuccessful transaction of AED 42.13 has been credited to your account XXXX0002 Card XXXX2865 on 19/06/25 02:50 and your current available balance is AED 4444.13
    """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("42.13"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    merchant = "Refund",
                    accountLast4 = "0002",
                    balance = BigDecimal("4444.13"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Funds transfer with comma amount",
                message = """
        Dear Customer, your funds transfer request of  AED 3,555.00 to IBAN/Account/Card XXXX0001  has been processed successfully from your account/card XXXX0002 on 12/06/2025 16:43
    """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3555.00"),
                    currency = "AED",
                    type = TransactionType.TRANSFER,
                    merchant = "Transfer: 002 → 001",
                    accountLast4 = "0002",
                    fromAccount = "0002",
                    toAccount = "0001"
                )
            ),
            ParserTestCase(
                name = "Generic account credit",
                message = """
        An amount of AED 555.00 has been credited to your FAB account XXXX0002 on 08/06/25 .Your Available Balance is AED 5555.43
    """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("555.00"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    merchant = "Account Credited",
                    accountLast4 = "0002",
                    balance = BigDecimal("5555.43")
                )
            ),
            ParserTestCase(
                name = "Funds transfer to account XXXX0002",
                message = """
                Dear Customer, your funds transfer request of AED 130.00 from account XXXX0003 to account XXXX0002 has been processed on 24/02/2025 00:31. For more information please call 600525500 (+97126811511 if calling from overseas).
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("130.00"),
                    currency = "AED",
                    type = TransactionType.TRANSFER,
                    merchant = "Transfer: 003 → 002",
                    accountLast4 = "0003",
                    fromAccount = "0003",
                    toAccount = "0002"
                )
            ),
            ParserTestCase(
                name = "Funds transfer to account XXXX0003",
                message = """
                Dear Customer, your funds transfer request of AED 250.00 from account XXXX0001 to account XXXX0003 has been processed on 24/02/2025 14:45. For more information please call 600525500 (+97126811511 if calling from overseas).
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "AED",
                    type = TransactionType.TRANSFER,
                    merchant = "Transfer: 001 → 003",
                    accountLast4 = "0001",
                    fromAccount = "0001",
                    toAccount = "0003"
                )
            ),
            ParserTestCase(
                name = "Funds transfer AED 7,000.00",
                message = """
                Dear Customer, your funds transfer request of AED 7,000.00 from account XXXX0003 to account XXXX0002 has been processed on 11/02/2025 00:11. For more information please call 600525500 (+97126811511 if calling from overseas).
            """.trimIndent(),
                sender = "FAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("7000.00"),
                    currency = "AED",
                    type = TransactionType.TRANSFER,
                    merchant = "Transfer: 003 → 002",
                    accountLast4 = "0003",
                    fromAccount = "0003",
                    toAccount = "0002"
                )
            )
        )

        val handleChecks = listOf(
            "FAB" to true,
            "FABBANK" to true,
            "AD-FAB-A" to true,
            "HDFC" to false,
            "SBI" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "FAB Parser Comprehensive Suite"
        )


    }
}
