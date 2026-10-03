package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class Trading212ParserTest {

    @TestFactory
    fun `trading 212 app notifications`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Interest on uninvested cash (GBP)",
                message = "💸 You earned £0.24 interest on uninvested cash!",
                sender = "Trading212",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.24"),
                    currency = "GBP",
                    type = TransactionType.INCOME,
                    merchant = "Trading 212 Interest"
                )
            ),
            ParserTestCase(
                name = "Interest on a EUR account",
                message = "💸 You earned €1.05 interest on uninvested cash!",
                sender = "Trading212",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1.05"),
                    currency = "EUR",
                    type = TransactionType.INCOME,
                    merchant = "Trading 212 Interest"
                )
            )
        )
        val handleCases = listOf("Trading212" to true, "TRADING 212" to true, "Chase" to false)
        return ParserTestUtils.runTestSuite(Trading212Parser(), cases, handleCases, "Trading 212")
    }
}
