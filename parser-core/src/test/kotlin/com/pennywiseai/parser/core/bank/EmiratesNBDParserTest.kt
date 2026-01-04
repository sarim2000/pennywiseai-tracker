package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class EmiratesNBDParserTest {

    private val parser = EmiratesNBDParser()

    @TestFactory
    fun `emirates nbd parser handles key paths`(): List<DynamicTest> {
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
            ),
            ParserTestCase(
                name = "Multi-currency Purchase - USD",
                message = "Purchase of USD 100.00 with Credit Card ending 9074 at Amazon.com. Avl Cr. Limit is USD 10,000.00",
                sender = "EmiratesNBD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "USD",
                    type = TransactionType.CREDIT,
                    merchant = "Amazon.com",
                    accountLast4 = "9074"
                )
            ),
            ParserTestCase(
                name = "Multi-currency Purchase - EUR",
                message = "Purchase of EUR 75.50 with Credit Card ending 4321 at Booking.com. Avl Cr. Limit is EUR 5,000.00",
                sender = "ENBD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.50"),
                    currency = "EUR",
                    type = TransactionType.CREDIT,
                    merchant = "Booking.com",
                    accountLast4 = "4321"
                )
            ),
            ParserTestCase(
                name = "Multi-currency Debit - GBP",
                message = "GBP 200.00 debited from A/C xxxx5678 on 25-Dec-25. Avl Bal is GBP 3,500.00",
                sender = "EmiratesNBD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "GBP",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678"
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves emirates nbd`(): List<DynamicTest> {
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

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
