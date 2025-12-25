package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EmiratesNBDParserTest {

    private val parser = EmiratesNBDParser()

    @Test
    fun `emirates nbd parser handles key paths`() {
        ParserTestUtils.printTestHeader(
            parserName = "Emirates NBD Bank (UAE)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Credit Card Purchase with Available Limit",
                message = "Purchase of AED 27.74 with Credit Card ending 9074 at Keeta, Dubai. Avl Cr. Limit is AED 30,978.13",
                sender = "EmiratesNBD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("27.74"),
                    currency = "AED",
                    type = TransactionType.CREDIT,
                    merchant = "Keeta, Dubai",
                    accountLast4 = "9074"
                )
            ),
            ParserTestCase(
                name = "Account Debit",
                message = "AED 500.00 debited from A/C xxxx1234 on 24-Dec-25. Avl Bal is AED 15,234.50",
                sender = "ENBD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Account Credit",
                message = "AED 2,500.00 credited to A/C xxxx5678 on 24-Dec-25. Available Balance: AED 25,750.00",
                sender = "EmiratesNB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678"
                )
            ),
            ParserTestCase(
                name = "Credit Card Purchase - Simple",
                message = "Purchase of AED 150.00 with Credit Card ending 4321 at Mall of Emirates. Avl Cr. Limit is AED 45,000.00",
                sender = "EmiratesNBD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "AED",
                    type = TransactionType.CREDIT,
                    merchant = "Mall of Emirates",
                    accountLast4 = "4321"
                )
            )
        )

        ParserTestUtils.runTestSuite(parser, cases)
    }

    @Test
    fun `factory resolves emirates nbd`() {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Emirates NBD",
                sender = "EmiratesNBD",
                currency = "AED",
                message = "Purchase of AED 27.74 with Credit Card ending 9074 at Keeta, Dubai. Avl Cr. Limit is AED 30,978.13",
                expected = ExpectedTransaction(
                    amount = BigDecimal("27.74"),
                    currency = "AED",
                    type = TransactionType.CREDIT
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Emirates NBD",
                sender = "ENBD",
                currency = "AED",
                message = "AED 500.00 debited from A/C xxxx1234 on 24-Dec-25. Avl Bal is AED 15,234.50",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Emirates NBD",
                sender = "EmiratesNB",
                currency = "AED",
                message = "AED 1,000.00 credited to your account",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "AED",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
