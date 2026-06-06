package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.math.BigDecimal

/**
 * Tests for M-Pesa Mozambique (Portuguese, MZN/"MT") parser, plus a coexistence
 * test proving content-aware dispatch routes shared "M-Pesa" senders correctly.
 *
 * All names/numbers are masked example values — no real PII.
 */
class MPesaMozambiqueParserTest {

    @TestFactory
    fun `m-pesa mozambique parser handles common cases`(): List<DynamicTest> {
        val parser = MPesaMozambiqueParser()

        val cases = listOf(
            ParserTestCase(
                name = "Transfer out (EXPENSE)",
                message = "Confirmado DF50KDFDHWK. Transferiste 1,234.56MT e a taxa foi de 1.23MT para 258841234567 - JOHNDOE aos 5/6/26 as 4:15 PM. O teu novo saldo M-Pesa e de 12,345.67MT. Continua a transferir SEM TAXAS de M-Pesa para M-Pesa. Em caso de duvida, liga 100.",
                sender = "M-Pesa",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.56"),
                    currency = "MZN",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHNDOE",
                    balance = BigDecimal("12345.67"),
                    reference = "DF50KDFDHWK"
                )
            ),
            ParserTestCase(
                name = "Purchase at entity (EXPENSE)",
                message = "Confirmado DF36KCPECLC. Registamos uma operacao de compra no valor de 1,234.56MT e a taxa foi de 0.00MT na entidade EDM com referencia  aos 3/6/26 as 10:37 PM. O teu novo saldo M-Pesa e de 1,234.56MT. Em caso de duvida, liga 100. M-Pesa e facil!",
                sender = "M-Pesa",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.56"),
                    currency = "MZN",
                    type = TransactionType.EXPENSE,
                    merchant = "EDM",
                    balance = BigDecimal("1234.56"),
                    reference = "DF36KCPECLC"
                )
            ),
            ParserTestCase(
                name = "Agent withdrawal (EXPENSE)",
                message = "Confirmado DF30KCJDIIA. Aos 3/6/26  as 3:57 PM levantaste 1,234.56MT no agente 425300 - BENJAMIM FERAGE. O novo saldo M-Pesa e de 1,234.56MT e a taxa foi de 12.34MT. Em caso de duvida, liga 100. M-Pesa e facil!",
                sender = "M-Pesa",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.56"),
                    currency = "MZN",
                    type = TransactionType.EXPENSE,
                    merchant = "BENJAMIM FERAGE",
                    balance = BigDecimal("1234.56"),
                    reference = "DF30KCJDIIA"
                )
            ),
            ParserTestCase(
                name = "Deposit at agent (INCOME)",
                message = "Confirmado DEV6KB6GAUI. Depositaste o valor de 12,345.67MT no agente JOHN DOE aos  31/5/26 as 12:04 PM. O teu novo saldo  M-Pesa e de 12,345.67MT. Aproveita e transfere SEM TAXAS de M-Pesa para M-Pesa. Em caso de duvida, liga 100.",
                sender = "M-Pesa",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12345.67"),
                    currency = "MZN",
                    type = TransactionType.INCOME,
                    merchant = "JOHN DOE",
                    balance = BigDecimal("12345.67"),
                    reference = "DEV6KB6GAUI"
                )
            ),
            ParserTestCase(
                name = "Received money (INCOME)",
                message = "Confirmado DET0KAIXP5E. Recebeste  12,345.67MT de 123456 - SIMO aos 29/5/26  as 6:22 PM o novo saldo  M-Pesa e de 1,234.56MT. Aproveita e transfere SEM TAXAS de M-Pesa para M-Pesa. Em caso de duvida, liga 100.",
                sender = "M-Pesa",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12345.67"),
                    currency = "MZN",
                    type = TransactionType.INCOME,
                    merchant = "SIMO",
                    balance = BigDecimal("1234.56"),
                    reference = "DET0KAIXP5E"
                )
            )
        )

        val handleChecks = listOf(
            "M-Pesa" to true,
            "MPESA" to true,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            cases,
            handleChecks,
            "M-Pesa Mozambique Parser"
        )
    }

    @Test
    fun `dispatch routes shared M-Pesa sender to correct country parser`() {
        val now = System.currentTimeMillis()

        // (a) Mozambique — Portuguese "Confirmado" + MT -> MZN -> Mozambique
        val mozMsg = "Confirmado DET0KAIXP5E. Recebeste  12,345.67MT de 123456 - SIMO aos 29/5/26  as 6:22 PM o novo saldo  M-Pesa e de 1,234.56MT. Aproveita e transfere SEM TAXAS de M-Pesa para M-Pesa. Em caso de duvida, liga 100."
        val moz = BankParserFactory.parse(mozMsg, "M-Pesa", now)
        assertNotNull(moz, "Mozambique message should parse")
        assertEquals("M-Pesa Mozambique", moz!!.bankName)
        assertEquals("MZN", moz.currency)

        // (b) Tanzania — English "Confirmed" + TZS -> Tanzania
        val tzMsg = "SGR1234567 Confirmed. You have received TZS 50,000.00 from JOHN DOE (255754XXXXXX) on 2025-05-12 at 10:30 AM. New M-Pesa balance is TZS 150,000.00."
        val tz = BankParserFactory.parse(tzMsg, "M-PESA", now)
        assertNotNull(tz, "Tanzania message should parse")
        assertEquals("M-Pesa Tanzania", tz!!.bankName)
        assertEquals("TZS", tz.currency)

        // (c) Kenya — English "Confirmed" + Ksh -> Kenya (proves Tanzania no longer shadows Kenya)
        val keMsg = "TJK6H7T3GA Confirmed. Ksh70.00 paid to person 1. on 20/10/24 at 4:21 PM.New M-PESA balance is Ksh123.12. Transaction cost, Ksh0.00. Amount you can transact within the day is 499,895.00."
        val ke = BankParserFactory.parse(keMsg, "M-PESA", now)
        assertNotNull(ke, "Kenya message should parse")
        assertEquals("M-PESA", ke!!.bankName)
        assertEquals("KES", ke.currency)
    }
}
