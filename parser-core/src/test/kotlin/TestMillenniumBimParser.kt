package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class MillenniumBimParserTest {
    @TestFactory
    fun `millennium bim parser handles common cases`(): List<DynamicTest> {
        val parser = MillenniumBimParser()

        val cases = listOf(
            ParserTestCase(
                name = "Debit - expense",
                message = "A conta 123456789 foi debitada no valor de 50.00 MZN as 10:14 do dia 31/05/26. Em caso de duvida, ligue 8003500. Millennium bim",
                sender = "Mbim",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "MZN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "6789"
                )
            ),
            ParserTestCase(
                name = "Credit - income",
                message = "A conta 123456789 recebeu o valor de 1000.00 MZN as 12:16 do dia 26/04/26. Em  caso de duvida, ligue 8003500. Millennium bim",
                sender = "Mbim",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "MZN",
                    type = TransactionType.INCOME,
                    accountLast4 = "6789"
                )
            ),
            ParserTestCase(
                name = "Debit with commission - expense",
                message = "A conta 123456789 foi debitada no valor de 1000.00 MZN as 12:25 do dia 27/01/26, comissao 30.00 MZN. Em caso de duvida, ligue 8003500. Millennium bim",
                sender = "Mbim",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "MZN",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "6789"
                )
            )
        )

        val handleChecks = listOf(
            "Mbim" to true,
            "MBIM" to true,
            "STDBank" to false,
            "eMola" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Millennium BIM Parser")
    }

    @Test
    fun `factory routes Mbim sender to this parser`() {
        Assertions.assertTrue(
            BankParserFactory.getParser("Mbim") is MillenniumBimParser,
            "Mbim should route to MillenniumBimParser"
        )
    }
}
