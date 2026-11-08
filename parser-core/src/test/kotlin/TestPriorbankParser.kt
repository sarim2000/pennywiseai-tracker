package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class PriorbankParserTest {
    @TestFactory
    fun `test Priorbank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = PriorbankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Priorbank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Example 1: KFC payment with quoted merchant name
            ParserTestCase(
                name = "Card Payment - KFC (quoted merchant)",
                message = """Karta 6***6666 29-10-25 18:34:25. Oplata 12.90 BYN. BLR RBO N77 "KFC Zavod". Dostupno: 947.09 BYN. Tel. 7299090""",
                sender = "Priorbank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.90"),
                    currency = "BYN",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "KFC Zavod",
                    accountLast4 = "6666",
                    balance = BigDecimal("947.09")
                )
            ),

            // Example 2: Fuel station payment without quotes
            ParserTestCase(
                name = "Card Payment - Gas Station",
                message = """Karta 6***6666 28-10-25 15:31:29. Oplata 19.98 BYN. BLR AZS N55. Dostupno: 997.86 BYN. Tel. 7299090""",
                sender = "Priorbank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("19.98"),
                    currency = "BYN",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "AZS N55",
                    accountLast4 = "6666",
                    balance = BigDecimal("997.86")
                )
            ),

            // Example 3: Mobile bank payment
            ParserTestCase(
                name = "Card Payment - Mobile Bank",
                message = """Karta 6***6666 26-10-25 18:30:21. Oplata 8.00 BYN. BLR MOBILE BANK. Dostupno: 250.70 BYN. Tel. 7299090""",
                sender = "Priorbank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8.00"),
                    currency = "BYN",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "MOBILE BANK",
                    accountLast4 = "6666",
                    balance = BigDecimal("250.70")
                )
            ),

            // English format (#1047)
            ParserTestCase(
                name = "English - card payment",
                message = """Card 4***1234 31-10-26 10:39:14. Payment 5.00 BYN. BLR PT TSENTR TORGOVLI I U. Balance: 120.50 BYN Tel. 7299090""",
                sender = "Priorbank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.00"),
                    currency = "BYN",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "PT TSENTR TORGOVLI I U",
                    accountLast4 = "1234",
                    balance = BigDecimal("120.50")
                )
            ),
            ParserTestCase(
                name = "English - money transfer out",
                message = """Card 4***1234 31-10-26 10:38:12. Money transfer 70.00 BYN. BLR P2P SDBO NO FEE. Balance: 50.50 BYN Tel. 7299090""",
                sender = "Priorbank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("70.00"),
                    currency = "BYN",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "P2P SDBO NO FEE",
                    accountLast4 = "1234",
                    balance = BigDecimal("50.50")
                )
            ),
            ParserTestCase(
                name = "English - transfer received",
                message = """Card 4***1234 31-10-26 10:38:12. The transfer received to the card 70.00 BYN. BLR . Balance: 190.50 BYN. Tel. 7299090""",
                sender = "Priorbank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("70.00"),
                    currency = "BYN",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = null,
                    accountLast4 = "1234",
                    balance = BigDecimal("190.50")
                )
            ),
            ParserTestCase(
                name = "English - foreign-currency payment keeps its currency",
                message = """Card 4***1234 01-11-26 09:15:02. Payment 12.99 USD. USA SAMPLE STREAMING. Balance: 80.00 USD Tel. 7299090""",
                sender = "Priorbank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.99"),
                    currency = "USD",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("80.00")
                )
            ),
            ParserTestCase(
                name = "English - balance left blank still parses",
                message = """Card ******** 31-10-26 10:39:14. Payment 5.00 BYN. BLR SHOP. Balance:  BYN Tel. 7299090""",
                sender = "Priorbank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.00"),
                    currency = "BYN",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "SHOP"
                )
            ),
            ParserTestCase(
                name = "OTP is ignored",
                message = """Kod 123456 dlya podtverzhdeniya operatsii. Priorbank""",
                sender = "Priorbank",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "Priorbank" to true,
            "PRIORBANK" to true,
            "priorbank" to true,
            "HDFC" to false,
            "SBI" to false,
            "" to false
        )

        val result =
            return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Priorbank Parser Tests")

    }
}
