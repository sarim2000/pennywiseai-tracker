package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestBancolombiaParser {

    private val parser = BancolombiaParser()

    @Test
    fun `test bank name`() {
        assertEquals("Bancolombia", parser.getBankName())
    }

    @Test
    fun `test can handle valid senders`() {
        assertTrue(parser.canHandle("87400"))
        assertTrue(parser.canHandle("85540"))
        assertFalse(parser.canHandle("12345"))
        assertFalse(parser.canHandle("BANCOL"))
    }

    @Test
    fun `test currency is COP`() {
        assertEquals("COP", parser.getCurrency())
    }

    @Test
    fun `test transaction type detection`() {
        val transferMessage = "Transferiste $1.000 a Juan Perez"
        val purchaseMessage = "Compraste $500,50 en Supermercado"
        val paymentMessage = "Pagaste $2.000.000 de tu tarjeta"
        val receiveMessage = "Recibiste $100.000,25 de Maria"

        val transfer = parser.parse(transferMessage, "87400", System.currentTimeMillis())
        val purchase = parser.parse(purchaseMessage, "87400", System.currentTimeMillis())
        val payment = parser.parse(paymentMessage, "87400", System.currentTimeMillis())
        val receive = parser.parse(receiveMessage, "87400", System.currentTimeMillis())

        assertEquals(TransactionType.EXPENSE, transfer?.type)
        assertEquals(TransactionType.EXPENSE, purchase?.type)
        assertEquals(TransactionType.EXPENSE, payment?.type)
        assertEquals(TransactionType.INCOME, receive?.type)
    }

    @Test
    fun `test Colombian currency format parsing with thousand separators`() {
        // Test with dots as thousand separators
        val message1 = "Transferiste $1.000.000 a cuenta"
        val parsed1 = parser.parse(message1, "87400", System.currentTimeMillis())

        assertNotNull(parsed1)
        assertEquals(BigDecimal("1000000"), parsed1?.amount)
        assertEquals(TransactionType.EXPENSE, parsed1?.type)
    }

    @Test
    fun `test Colombian currency format parsing with decimals`() {
        // Test with comma as decimal separator
        val message2 = "Compraste $500,50 en tienda"
        val parsed2 = parser.parse(message2, "87400", System.currentTimeMillis())

        assertNotNull(parsed2)
        assertEquals(BigDecimal("500.50"), parsed2?.amount)
        assertEquals(TransactionType.EXPENSE, parsed2?.type)
    }

    @Test
    fun `test Colombian currency format parsing with both separators`() {
        // Test with both thousand separator (dot) and decimal separator (comma)
        val message3 = "Pagaste $1.000.000,75 de tarjeta credito"
        val parsed3 = parser.parse(message3, "85540", System.currentTimeMillis())

        assertNotNull(parsed3)
        assertEquals(BigDecimal("1000000.75"), parsed3?.amount)
        assertEquals(TransactionType.EXPENSE, parsed3?.type)
    }

    @Test
    fun `test Colombian currency format parsing income transaction`() {
        // Test income with Colombian format
        val message4 = "Recibiste $2.500.000,00 transferencia de empresa"
        val parsed4 = parser.parse(message4, "87400", System.currentTimeMillis())

        assertNotNull(parsed4)
        assertEquals(BigDecimal("2500000.00"), parsed4?.amount)
        assertEquals(TransactionType.INCOME, parsed4?.type)
    }

    @Test
    fun `test amount without decimals`() {
        val message = "Transferiste $5000 a Pedro"
        val parsed = parser.parse(message, "87400", System.currentTimeMillis())

        assertNotNull(parsed)
        assertEquals(BigDecimal("5000"), parsed?.amount)
    }

    @Test
    fun `test small amounts with decimals`() {
        val message = "Compraste $25,99 en cafeteria"
        val parsed = parser.parse(message, "85540", System.currentTimeMillis())

        assertNotNull(parsed)
        assertEquals(BigDecimal("25.99"), parsed?.amount)
    }

    @Test
    fun `test merchant extraction`() {
        val transfer = "Transferiste $1.000 a Juan"
        val purchase = "Compraste $500 en tienda"
        val payment = "Pagaste $2.000 tarjeta"
        val receive = "Recibiste $100.000 de empresa"

        val parsedTransfer = parser.parse(transfer, "87400", System.currentTimeMillis())
        val parsedPurchase = parser.parse(purchase, "87400", System.currentTimeMillis())
        val parsedPayment = parser.parse(payment, "87400", System.currentTimeMillis())
        val parsedReceive = parser.parse(receive, "87400", System.currentTimeMillis())

        assertEquals("Transferencia", parsedTransfer?.merchant)
        assertEquals("Compra", parsedPurchase?.merchant)
        assertEquals("Pago", parsedPayment?.merchant)
        assertEquals("Dinero recibido", parsedReceive?.merchant)
    }

    @Test
    fun `test non-transaction messages return null`() {
        val balanceMessage = "Tu saldo es $50.000,00"
        val promoMessage = "Aprovecha nuestras ofertas"

        assertNull(parser.parse(balanceMessage, "87400", System.currentTimeMillis()))
        assertNull(parser.parse(promoMessage, "87400", System.currentTimeMillis()))
    }
}