package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BancoPromericaParserTest {

    @TestFactory
    fun `banco promerica parser handles common cases`(): List<DynamicTest> {
        val parser = BancoPromericaParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Rejected transfer is ignored",
                message = "Su Op. Transfer365 de Transferencia de Fondos a GIVEN NAME SUR NAME por $ 26.00 ha sido rechazada",
                sender = "Promerica",
                shouldParse = false,
                description = "The successful sample says \"aplicada exitosamente\", so unsuccessful " +
                    "variants exist; no money moved, so nothing is recorded."
            ),
            ParserTestCase(
                name = "Payment request is ignored",
                message = "Ha recibido una solicitud de pago de GIVEN NAME por $ 15.00",
                sender = "Promerica",
                shouldParse = false,
                description = "A request to pay is not a payment — \"recibido\" would otherwise read as income."
            ),
            ParserTestCase(
                name = "Incoming Transfer365 credit",
                message = "Ha recibido un abono Transferencia de Fondos a cuenta corriente de GIVEN NAME SURNAME por $ 80.00 a traves de Transfer365 el 15/07/2026 20:37:06",
                sender = "Promerica",
                expected = ExpectedTransaction(
                    amount = BigDecimal("80.00"),
                    currency = "USD",
                    type = TransactionType.INCOME,
                    // "de GIVEN NAME SURNAME" is whose current account was credited, not who
                    // paid; this sample names no payer, so none is recorded.
                    merchant = null,

                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Outgoing Transfer365 transfer",
                message = "Su Op. Transfer365 de Transferencia de Fondos a GIVEN NAME SUR NAME por $ 26.00 ha sido aplicada exitosamente",
                sender = "Promerica",
                expected = ExpectedTransaction(
                    amount = BigDecimal("26.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "GIVEN NAME SUR NAME",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Card purchase",
                message = "Promerica te informa que tu TTA *1234: ha realizado una compra por USD 2.50 en NAME OF THE STORE AND CITY. Consulta al 25135000",
                sender = "Promerica",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.50"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "NAME OF THE STORE AND CITY",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            )
        )

        val handleCases = listOf(
            "Promerica" to true,
            "PROMERICA" to true,
            "B.CUSCATLAN" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Banco Promerica Parser Tests")
    }
}
