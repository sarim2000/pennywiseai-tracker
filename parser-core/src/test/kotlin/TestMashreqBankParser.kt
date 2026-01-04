package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class MashreqBankParserTest {
    @TestFactory
    fun `test Mashreq Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = MashreqBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Mashreq Bank (UAE)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Test case 1: User's provided example - NEO VISA Debit Card purchase
            ParserTestCase(
                name = "NEO VISA Debit Card Purchase (User Example)",
                message = "Thank you for using NEO VISA Debit Card Card ending XXXX for AED 5.99 at CARREFOUR on 26-AUG-2025 10:25 PM. Available Balance is AED X,480.15",
                sender = "Mashreq",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.99"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "CARREFOUR",
                    accountLast4 = null,  // XXXX is all masked, so null
                    balance = BigDecimal("0480.15"),  // X replaced with 0
                    reference = "26-AUG-2025 10:25 PM"
                )
            ),

            // Test case 2: Debit card purchase with visible card digits
            ParserTestCase(
                name = "Debit Card Purchase with Card Number",
                message = "Thank you for using NEO VISA Debit Card Card ending 1234 for AED 125.50 at DUBAI MALL on 15-JAN-2025 3:45 PM. Available Balance is AED 3,250.75",
                sender = "Mashreq",
                expected = ExpectedTransaction(
                    amount = BigDecimal("125.50"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "DUBAI MALL",
                    accountLast4 = "1234",
                    balance = BigDecimal("3250.75"),
                    reference = "15-JAN-2025 3:45 PM"
                )
            ),

            // Test case 3: Foreign currency transaction
            ParserTestCase(
                name = "Debit Card Purchase (Foreign Currency - USD)",
                message = "Thank you for using NEO VISA Debit Card Card ending 5678 for USD 89.99 at AMAZON on 20-FEB-2025 11:30 AM. Available Balance is AED 2,500.00",
                sender = "Mashreq",
                expected = ExpectedTransaction(
                    amount = BigDecimal("89.99"),
                    currency = "USD",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "AMAZON",
                    accountLast4 = "5678",
                    balance = BigDecimal("2500.00"),
                    reference = "20-FEB-2025 11:30 AM"
                )
            ),

            // Test case 4: Large amount with comma formatting
            ParserTestCase(
                name = "Large Amount Transaction",
                message = "Thank you for using NEO VISA Debit Card Card ending 9876 for AED 1,250.00 at ELECTRONICS STORE on 10-MAR-2025 5:15 PM. Available Balance is AED 8,750.50",
                sender = "Mashreq",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "ELECTRONICS STORE",
                    accountLast4 = "9876",
                    balance = BigDecimal("8750.50"),
                    reference = "10-MAR-2025 5:15 PM"
                )
            ),

            // Test case 5: Balance with X masking (thousands)
            ParserTestCase(
                name = "Balance with X Masking",
                message = "Thank you for using NEO VISA Debit Card Card ending 4321 for AED 45.00 at COFFEE SHOP on 05-APR-2025 8:20 AM. Available Balance is AED X,123.45",
                sender = "Mashreq",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.00"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "COFFEE SHOP",
                    accountLast4 = "4321",
                    balance = BigDecimal("0123.45"),  // X replaced with 0
                    reference = "05-APR-2025 8:20 AM"
                )
            ),

            // Test case 6: EUR currency transaction
            ParserTestCase(
                name = "Foreign Currency - EUR",
                message = "Thank you for using NEO VISA Debit Card Card ending 7890 for EUR 150.00 at PARIS STORE on 12-MAY-2025 2:30 PM. Available Balance is AED 4,500.00",
                sender = "Mashreq",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "EUR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "PARIS STORE",
                    accountLast4 = "7890",
                    balance = BigDecimal("4500.00"),
                    reference = "12-MAY-2025 2:30 PM"
                )
            ),

            // Negative test cases - should NOT parse

            ParserTestCase(
                name = "OTP Message (Should Not Parse)",
                message = "Your OTP for Mashreq transaction is 123456. Do not share with anyone. Valid for 5 minutes.",
                sender = "Mashreq",
                shouldParse = false,
                expected = ExpectedTransaction(
                    amount = BigDecimal.ZERO,
                    currency = "",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE
                )
            ),

            ParserTestCase(
                name = "Card Activation (Should Not Parse)",
                message = "Your Mashreq NEO card has been activated successfully. Thank you for banking with us.",
                sender = "Mashreq",
                shouldParse = false,
                expected = ExpectedTransaction(
                    amount = BigDecimal.ZERO,
                    currency = "",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE
                )
            ),

            ParserTestCase(
                name = "Failed Transaction (Should Not Parse)",
                message = "Your transaction for AED 500.00 has been declined due to insufficient balance. Please contact customer service.",
                sender = "Mashreq",
                shouldParse = false,
                expected = ExpectedTransaction(
                    amount = BigDecimal.ZERO,
                    currency = "",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE
                )
            ),

            ParserTestCase(
                name = "PIN Change Notification (Should Not Parse)",
                message = "Your Mashreq card PIN has been changed successfully on 15-JAN-2025. If you did not initiate this, please contact us.",
                sender = "Mashreq",
                shouldParse = false,
                expected = ExpectedTransaction(
                    amount = BigDecimal.ZERO,
                    currency = "",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "Mashreq" to true,
            "MASHREQ" to true,
            "Mshreq" to true,
            "MSHREQ" to true,
            "AE-MASHREQ-B" to true,
            "HDFC" to false,
            "SBI" to false,
            "FAB" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            testCases,
            handleCases,
            "Mashreq Bank Parser Tests"
        )

    }
}
