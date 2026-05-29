package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class StandardBankMozambiqueParserTest {
    @TestFactory
    fun `standard bank mozambique parser handles common cases`(): List<DynamicTest> {
        val parser = StandardBankMozambiqueParser()

        val cases = listOf(
            ParserTestCase(
                name = "Credit (MZN) - income",
                message = "Caro Cliente, ocorreu um credito de 1.234,56 MZN na sua conta 1234567890123 a 22/05/2026, 14:54, disponivel em 22/05/2026. Mais info: 800412412/21355700",
                sender = "STDBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.56"),
                    currency = "MZN",
                    type = TransactionType.INCOME,
                    accountLast4 = "0123"
                )
            ),
            ParserTestCase(
                name = "Purchase (MT -> MZN) - expense with merchant",
                message = "Caro Cliente, ocorreu uma operacao de compra de 1.234,56, MT na sua conta 1234567890123 a 09/05/2026, 13:41, MM INVESTMENTS S. Comissao: 0.00MT e Imposto de selo: 0.00MT. Mais info: 800412412/21355700",
                sender = "STDBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.56"),
                    currency = "MZN",
                    type = TransactionType.EXPENSE,
                    merchant = "MM INVESTMENTS S",
                    accountLast4 = "0123"
                )
            ),
            ParserTestCase(
                name = "Debit (USD) - expense with location",
                message = "Caro Cliente, ocorreu um debito de 1.234,56 USD na sua conta 1234567890123 a 15/05/2026, 13:30, C01 AGENCIA DA BEIRA. Comissao: 0.00USD e Imposto de selo: 0.00USD. Mais info: 800412412/21355700",
                sender = "7832265",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.56"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "C01 AGENCIA DA BEIRA",
                    accountLast4 = "0123"
                )
            )
        )

        val handleChecks = listOf(
            "STDBank" to true,
            "7832265" to true,
            "HDFCBK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Standard Bank Mozambique Parser")
    }
}
