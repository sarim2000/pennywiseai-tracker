package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BancoAgricolaParserTest {

    @TestFactory
    fun `banco agricola parser handles common cases`(): List<DynamicTest> {
        val parser = BancoAgricolaParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Transfer365 credit from the bank's own sender",
                message = "B.AGRICOLA-TRANSFER365: ha recibido un Abono a Cuenta de GIVEN NAME SURNAME por USD6.91 desde ANOTHER BANK NAME",
                sender = "Agricola",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6.91"),
                    currency = "USD",
                    type = TransactionType.INCOME,
                    merchant = "ANOTHER BANK NAME",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Transfer365 credit from the shared Transfer365 sender",
                message = "B.AGRICOLA-TRANSFER365: ha recibido un Abono a Cuenta de GIVEN NAME SURNAME por USD20.00 desde ANOTHER BANK NAME",
                sender = "Transfer365",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "USD",
                    type = TransactionType.INCOME,
                    merchant = "ANOTHER BANK NAME",
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Transfer365 sender but another bank's body is not claimed",
                message = "OTHER BANK-TRANSFER365: ha recibido un Abono a Cuenta de GIVEN NAME SURNAME por USD20.00 desde ANOTHER BANK NAME",
                sender = "Transfer365",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            "Agricola" to true,
            "Transfer365" to true,
            "Promerica" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Banco Agricola Parser Tests")
    }

    @Test
    fun `factory routes the shared Transfer365 sender to Banco Agricola only for its own messages`() {
        val agricola = "B.AGRICOLA-TRANSFER365: ha recibido un Abono a Cuenta de GIVEN NAME SURNAME por USD20.00 desde ANOTHER BANK NAME"
        val parsed = BankParserFactory.parse(agricola, "Transfer365", 0L)
        assertEquals("Banco Agricola", parsed?.bankName)

        val otherBank = "OTHER BANK-TRANSFER365: ha recibido un Abono a Cuenta de GIVEN NAME SURNAME por USD20.00 desde ANOTHER BANK NAME"
        assertNull(BankParserFactory.parse(otherBank, "Transfer365", 0L))
    }
}
