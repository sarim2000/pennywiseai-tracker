package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SparkasseRheinMaasParserTest {

    private val parser = SparkasseRheinMaasParser()

    @TestFactory
    fun `sparkasse rhein-maas parser handles common cases`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Kartenwecker - card purchase",
                message = """
                    Kartenwecker:
                    1 neuer Kartenumsatz auf dem Konto *1832:
                    KAUFLAND: -70,85 EUR
                    Neuer Saldo: 991,84 EUR
                    Ihre Sparkasse
                """.trimIndent(),
                sender = "Sparkasse",
                expected = ExpectedTransaction(
                    amount = BigDecimal("70.85"),
                    currency = "EUR",
                    type = TransactionType.EXPENSE,
                    merchant = "KAUFLAND",
                    accountLast4 = "1832",
                    balance = BigDecimal("991.84"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Gehaltswecker - salary credit with thousands separator",
                message = """
                    Gehaltswecker:
                    Gehalt ist auf Konto *1832 eingegangen:
                    Action De.: 1.415,62 EUR
                    Neuer Saldo: 1.415,67 EUR
                    Ihre Sparkasse
                """.trimIndent(),
                sender = "Sparkasse",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1415.62"),
                    currency = "EUR",
                    type = TransactionType.INCOME,
                    merchant = "Action De.",
                    accountLast4 = "1832",
                    balance = BigDecimal("1415.67"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Kontostandswecker - balance-only push is rejected",
                message = """
                    Kontostandswecker:
                    Konto *1832
                    Neuer Saldo: 1.246,69 EUR
                    Neue Umsaetze: 4
                    Ihre Sparkasse
                """.trimIndent(),
                sender = "Sparkasse",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "Sparkasse" to true,
            "SPARKASSE" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            cases,
            handleChecks,
            "Sparkasse Rhein-Maas Parser"
        )
    }
}
