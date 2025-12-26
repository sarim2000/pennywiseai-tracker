package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class LivBankParserTest {
    @TestFactory
    fun `test Liv Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = LivBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Liv Bank (UAE)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Test case 1: User's provided example - Credit transaction
            ParserTestCase(
                name = "Credit to Account (User Example 1)",
                message = "AED 3,586.96 has been credited to account 095XXX71XXXO1. Current balance is AED 4,377.01.\n\nCredits post cut-offs will be available next day.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3586.96"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    merchant = "Account Credit",
                    accountLast4 = "O1",  // After filtering X's, last visible chars
                    balance = BigDecimal("4377.01"),
                    isFromCard = false
                )
            ),

            // Test case 2: User's provided example - Debit card purchase
            ParserTestCase(
                name = "Debit Card Purchase (User Example 2)",
                message = "Purchase of AED 33.00 with Debit Card ending 4878 at JABAL HAFEET HAIRDRESS, Sharjah. Avl Balance is AED 4,344.01.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("33.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    merchant = "JABAL HAFEET HAIRDRESS",
                    accountLast4 = "4878",
                    balance = BigDecimal("4344.01"),
                    isFromCard = true
                )
            ),

            // Test case 3: Large credit with commas
            ParserTestCase(
                name = "Large Credit Transaction",
                message = "AED 10,500.50 has been credited to account 095XXX71XXXO1. Current balance is AED 15,000.00.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10500.50"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    merchant = "Account Credit",
                    accountLast4 = "O1",
                    balance = BigDecimal("15000.00"),
                    isFromCard = false
                )
            ),

            // Test case 4: Small purchase amount
            ParserTestCase(
                name = "Small Debit Card Purchase",
                message = "Purchase of AED 5.50 with Debit Card ending 1234 at CAFE NERO, Dubai. Avl Balance is AED 1,234.56.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.50"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    merchant = "CAFE NERO",
                    accountLast4 = "1234",
                    balance = BigDecimal("1234.56"),
                    isFromCard = true
                )
            ),

            // Test case 5: Purchase with different merchant format
            ParserTestCase(
                name = "Purchase at Multi-word Merchant",
                message = "Purchase of AED 150.00 with Debit Card ending 5678 at DUBAI MALL PARKING. Avl Balance is AED 2,500.00.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    merchant = "DUBAI MALL PARKING",
                    accountLast4 = "5678",
                    balance = BigDecimal("2500.00"),
                    isFromCard = true
                )
            ),

            // Test case 6: Credit without multi-line
            ParserTestCase(
                name = "Simple Credit",
                message = "AED 1,000.00 has been credited to account 095XXX71XXXO1. Current balance is AED 5,000.00.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    merchant = "Account Credit",
                    accountLast4 = "O1",
                    balance = BigDecimal("5000.00"),
                    isFromCard = false
                )
            ),

            // Test case 7: Large purchase amount
            ParserTestCase(
                name = "Large Purchase",
                message = "Purchase of AED 5,999.99 with Debit Card ending 9999 at ELECTRONICS STORE, Abu Dhabi. Avl Balance is AED 10,000.00.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5999.99"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    merchant = "ELECTRONICS STORE",
                    accountLast4 = "9999",
                    balance = BigDecimal("10000.00"),
                    isFromCard = true
                )
            ),

            // Multi-currency test cases
            ParserTestCase(
                name = "Multi-currency Purchase - USD",
                message = "Purchase of USD 75.00 with Debit Card ending 4878 at AMAZON.COM. Avl Balance is AED 4,100.00.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.00"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "AMAZON.COM",
                    accountLast4 = "4878",
                    balance = BigDecimal("4100.00"),
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Multi-currency Purchase - EUR",
                message = "Purchase of EUR 50.00 with Debit Card ending 1234 at BOOKING.COM. Avl Balance is AED 3,800.00.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "EUR",
                    type = TransactionType.EXPENSE,
                    merchant = "BOOKING.COM",
                    accountLast4 = "1234",
                    balance = BigDecimal("3800.00"),
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Multi-currency Purchase - GBP",
                message = "Purchase of GBP 100.00 with Debit Card ending 5678 at MARKS AND SPENCER. Avl Balance is AED 3,200.00.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "GBP",
                    type = TransactionType.EXPENSE,
                    merchant = "MARKS AND SPENCER",
                    accountLast4 = "5678",
                    balance = BigDecimal("3200.00"),
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Multi-currency Credit - USD",
                message = "USD 200.00 has been credited to account 095XXX71XXXO1. Current balance is AED 6,000.00.",
                sender = "Liv",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "USD",
                    type = TransactionType.INCOME,
                    merchant = "Account Credit",
                    accountLast4 = "O1",
                    balance = BigDecimal("6000.00"),
                    isFromCard = false
                )
            ),

            // Negative test cases - should NOT parse

            ParserTestCase(
                name = "OTP Message (Should Not Parse)",
                message = "Your Liv Bank OTP is 123456. Valid for 5 minutes. Do not share with anyone.",
                sender = "Liv",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Promotional Message (Should Not Parse)",
                message = "Enjoy exclusive offers with Liv Bank! Download our app today for amazing rewards.",
                sender = "Liv",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Failed Transaction (Should Not Parse)",
                message = "Your purchase of AED 500.00 has failed due to insufficient balance. Please top up your account.",
                sender = "Liv",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Card Activation (Should Not Parse)",
                message = "Your Liv Debit Card has been activated successfully. Start enjoying seamless payments!",
                sender = "Liv",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Declined Transaction (Should Not Parse)",
                message = "Transaction declined. Your purchase of AED 200.00 was not processed.",
                sender = "Liv",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "Liv" to true,
            "LIV" to true,
            "liv" to true,
            "EmiratesNBD" to false,
            "FAB" to false,
            "Mashreq" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            testCases,
            handleCases,
            "Liv Bank Parser Tests"
        )
    }

    @TestFactory
    fun `factory resolves Liv Bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Liv Bank",
                sender = "Liv",
                currency = "AED",
                message = "AED 100.00 has been credited to account 095XXX71XXXO1. Current balance is AED 500.00.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "AED",
                    type = TransactionType.INCOME,
                    merchant = "Account Credit",
                    balance = BigDecimal("500.00"),
                    isFromCard = false
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Liv Bank",
                sender = "LIV",
                currency = "AED",
                message = "Purchase of AED 50.00 with Debit Card ending 1234 at TEST MERCHANT. Avl Balance is AED 450.00.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    merchant = "TEST MERCHANT",
                    accountLast4 = "1234",
                    balance = BigDecimal("450.00"),
                    isFromCard = true
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Liv Bank",
                sender = "LIV",
                currency = "AED",
                message = "Purchase of AED 99.00 with Debit Card ending 5678 at DUBAI MALL. Avl Balance is AED 1,200.00.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("99.00"),
                    currency = "AED",
                    type = TransactionType.EXPENSE,
                    merchant = "DUBAI MALL",
                    accountLast4 = "5678",
                    balance = BigDecimal("1200.00"),
                    isFromCard = true
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Liv Bank Factory Tests")
    }
}
