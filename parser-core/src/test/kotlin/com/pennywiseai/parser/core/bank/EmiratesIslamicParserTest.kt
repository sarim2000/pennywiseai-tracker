package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class EmiratesIslamicParserTest {

    private val parser = EmiratesIslamicParser()

    @TestFactory
    fun `emirates islamic parser handles all sample types`(): List<DynamicTest> {
        val cases = listOf(
            // Sample 1: Debit Card Purchase
            ParserTestCase(
                name = "Debit Card Purchase",
                message = """
                    Debit Card Purchase
                    Card Ending: 1111
                    At: talabat.com, DUBAI
                    Amount: AED 12.34
                    Date: 21/12/2024 20:18
                    Available Balance: AED 12,123.12
                """.trimIndent(),
                sender = "EI SMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    merchant = "talabat.com, DUBAI",
                    accountLast4 = "1111",
                    balance = BigDecimal("12123.12")
                )
            ),
            // Sample 2: Telegraphic Transfer Deducted (outgoing) -> EXPENSE
            ParserTestCase(
                name = "Telegraphic Transfer Deducted",
                message = "Telegraphic Transfer Deducted From Account: 123XXX12XXX12  Amount: AED 12.00 Date: 21/12/2024 20:18 Available Balance: AED 12,123.12",
                sender = "EI SMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1212",
                    balance = BigDecimal("12123.12")
                )
            ),
            // Sample 3: Payment towards Credit Card (money leaving account) -> EXPENSE
            ParserTestCase(
                name = "Payment towards Credit Card",
                message = """
                    Payment towards Credit Card
                    From Account: 12345XXXXX123
                    Amount: AED 1,123.12
                    Date: 21/12/2024 12:34
                    Available Balance: AED 12,123.12
                """.trimIndent(),
                sender = "EI SMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1123.12"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5123",
                    balance = BigDecimal("12123.12")
                )
            ),
            // Sample 4: Credit Card Purchase -> EXPENSE (uses "Available Limit")
            ParserTestCase(
                name = "Credit Card Purchase",
                message = """
                    Credit Card Purchase
                    Card Ending: 1234
                    At: ROXY CINEMA - Dubai Hi, Dubai
                    Amount: AED 123.00
                    Date: 21/12/2024, 20:12
                    Available Limit: AED 123,123.12
                """.trimIndent(),
                sender = "EI SMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("123.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    merchant = "ROXY CINEMA - Dubai Hi, Dubai",
                    accountLast4 = "1234",
                    balance = BigDecimal("123123.12")
                )
            ),
            // Sample 5: Credit Card payment receipt -> NON-transaction (return null)
            ParserTestCase(
                name = "Credit Card payment receipt (confirmation, not a transaction)",
                message = "This is to confirm receipt of your payment of AED 123.00 towards your Credit Card starting with 123456  on 21/12/2024. Available limit is   AED 123,123.12.",
                sender = "EI SMS",
                shouldParse = false
            ),
            // Sample 6: ATM Withdrawal -> EXPENSE
            ParserTestCase(
                name = "ATM Withdrawal",
                message = """
                    ATM Withdrawal
                    Debit Card Ending: 1234
                    From: ABU DHABI UAEAE, ABU DHABI
                    Amount: AED 123.00
                    Date: 21/12/2024, 20:12
                    Available Balance: AED 123.12
                """.trimIndent(),
                sender = "EI SMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("123.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal: ABU DHABI UAEAE, ABU DHABI",
                    accountLast4 = "1234",
                    balance = BigDecimal("123.12")
                )
            ),
            // Sample 7: Online Banking Transfer -> EXPENSE
            ParserTestCase(
                name = "Online Banking Transfer",
                message = "Online Banking Transfer From Account: 123XXX12XXX12 Amount: AED 12,123.00 Date: 21/12/2024 20:12 Available Balance: AED 123,123.12",
                sender = "EI SMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12123.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1212",
                    balance = BigDecimal("123123.12")
                )
            ),
            // Sample 8: Telegraphic Transfer Received -> INCOME
            ParserTestCase(
                name = "Telegraphic Transfer Received",
                message = "Telegraphic Transfer Received To Account: 123XXX12XXX12 Amount: AED 12.00 Date: 21/12/2024 00:12 Available Balance: AED 123,123.12",
                sender = "EI SMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.00"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    accountLast4 = "1212",
                    balance = BigDecimal("123123.12")
                )
            ),
            // Sample 9: Salary Deposited -> INCOME
            ParserTestCase(
                name = "Salary Deposited",
                message = "Salary Deposited Account: 123XXX12XXX12 Amount: AED 123,123.12 Date: 21/12/2024 22:44 Available Balance: AED 123,123.12",
                sender = "EI SMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("123123.12"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    accountLast4 = "1212",
                    balance = BigDecimal("123123.12")
                )
            ),
            // Non-transaction: OTP -> return null
            ParserTestCase(
                name = "OTP message is not a transaction",
                message = "Your OTP for Emirates Islamic online banking is 123456. Do not share it with anyone.",
                sender = "EI SMS",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "EI SMS" to true,
            "EISMS" to true,
            "OTHER" to false,
            "EMIRATESNBD" to false,
            "ENBD" to false,
            "ADCB" to false,
            "EI" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Emirates Islamic Bank Parser")
    }

    @TestFactory
    fun `factory resolves emirates islamic`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Emirates Islamic",
                sender = "EI SMS",
                currency = "AED",
                message = """
                    Debit Card Purchase
                    Card Ending: 1111
                    At: talabat.com, DUBAI
                    Amount: AED 12.34
                    Date: 21/12/2024 20:18
                    Available Balance: AED 12,123.12
                """.trimIndent(),
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "AED",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
