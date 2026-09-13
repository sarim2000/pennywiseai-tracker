package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BancoCuscatlanParserTest {

    @TestFactory
    fun `banco cuscatlan parser handles common cases`(): List<DynamicTest> {
        val parser = BancoCuscatlanParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Credit to account",
                message = "Ha recibido un Credito en su Cuenta B. CUSCATLAN 1234 por USD10.00 el dia 2025-12-25 15:45. Mas inf. 22122000.",
                sender = "B.CUSCATLAN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "USD",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Debit card purchase",
                message = "Alerta de consumo con Tarjeta de Debito con Cuenta B.CUSCATLAN 1234 por USD10.00 en PAYPAL *STEAM GAMES el 2026-07-22 23:03. Mas inf. 22122000",
                sender = "B.CUSCATLAN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "PAYPAL *STEAM GAMES",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Outgoing Transfer365 transfer",
                message = "Su Op. Transfer365 CORRIENTE XXXXXX1234 a GIVEN NAME SUR NAME por USD 5.00 ha sido aplicada el dia 28/04/2026 03:04:53 P.M.",
                sender = "B.CUSCATLAN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "GIVEN NAME SUR NAME",
                    accountLast4 = "1234",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Debit / ATM withdrawal",
                message = "Se hizo un Debito en su Cuenta B. CUSCATLAN 1234 por USD15.00 el dia 2025-11-02 13:40. Mas inf. 22122000.",
                sender = "B.CUSCATLAN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Non-transaction message is ignored",
                message = "Su codigo de verificacion B. CUSCATLAN es 123456. No lo comparta.",
                sender = "B.CUSCATLAN",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            "B.CUSCATLAN" to true,
            "CUSCATLAN" to true,
            "Promerica" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Banco Cuscatlan Parser Tests")
    }
}
