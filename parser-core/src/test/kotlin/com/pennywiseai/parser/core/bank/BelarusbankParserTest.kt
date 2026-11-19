package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class BelarusbankParserTest {

    private val parser = BelarusbankParser()

    @TestFactory
    fun `belarusbank parser handles key paths`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Belarusbank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val messageCases = listOf(
            ParserTestCase(
                name = "Card payment - Green",
                message = "OPLATA 80.81 BYN  KARTA #1111 DATA 11.11.2011 11:11:11 SUPERMARKET 'GREEN' MCC:5411 OSTATOK 72.75 BYN",
                sender = "ASB.BY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("80.81"),
                    currency = "BYN",
                    type = TransactionType.EXPENSE,
                    merchant = "SUPERMARKET 'GREEN'",
                    accountLast4 = "1111"
                )
            ),
            ParserTestCase(
                name = "Cash deposit",
                message = "POPOLNENIE KARTOCHKI 500.00 BYN  KARTA #2222 DATA 07.11.2026 23:04:21 PERSON TO PERSON>MINSK>BY MCC:6012 OSTATOK 1000.00 BYN",
                sender = "ASB.BY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BYN",
                    type = TransactionType.INCOME,
                    accountLast4 = "2222"
                )
            ),
            ParserTestCase(
                name = "Cash withdrawal",
                message = "NALICHNYE 100.00 USD  KARTA #3333 DATA 26.10.2023 17:42:31 DANA MALL' RB.795 ATM>MINSK>BY MCC:6011 OSTATOK 1505.15 BYN",
                sender = "ASB.BY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "USD",
                    accountCurrency = "BYN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3333",
                    balance = BigDecimal("1505.15")
                )
            ),
            ParserTestCase(
                name = "Credit Card Purchase - Simple",
                message = "ZACHISLENIE ZARABOTNOY PLATY 3000.00 EUR KARTA #4444 DATA 31.12.2025 12:00:00 OSTATOK 23098.67 BYN",
                sender = "ASB.BY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "EUR",
                    accountCurrency = "BYN",
                    type = TransactionType.INCOME,
                    accountLast4 = "4444",
                    balance = BigDecimal("23098.67")
                )
            ),
        )

        val senderCases = listOf(
            "ASB.BY" to true,
            "ASB.by" to false,
            "Belarusbank" to false,
            "Беларусбанк" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, messageCases, senderCases)
    }
}
