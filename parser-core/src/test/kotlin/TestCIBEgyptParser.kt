package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class CIBEgyptParserTest {
    @TestFactory
    fun `test CIB Egypt Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = CIBEgyptParser()

        ParserTestUtils.printTestHeader(
            parserName = "CIB (Commercial International Bank) Egypt",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Test case 1: Credit card charge (user example)
            ParserTestCase(
                name = "Credit Card Charge - User Example",
                message = "Your credit card ending with#8016 was charged for EGP 118.00 at SAOOD MARKET on 24/11/25  at 18:27. Card available limit is EGP  10000.21. For more details, please visit https://cib.eg/mb",
                sender = "CIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("118.00"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "SAOOD MARKET",
                    accountLast4 = "8016",
                    isFromCard = true
                )
            ),

            // Test case 2: Credit card refund (user example)
            ParserTestCase(
                name = "Credit Card Refund - User Example",
                message = "The transaction on your credit card#8016  from ORACLE IRELAND  with EUR .93 on 15/11/25  at 05:14 has been refunded. Please try again. Thank you",
                sender = "CIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.93"),
                    currency = "EUR",
                    type = TransactionType.INCOME,
                    merchant = "ORACLE IRELAND",
                    accountLast4 = "8016",
                    isFromCard = true
                )
            ),

            // Test case 3: Large amount transaction
            ParserTestCase(
                name = "Large Amount Transaction",
                message = "Your credit card ending with#1234 was charged for EGP 1,500.00 at CARREFOUR EGYPT on 10/12/25  at 14:30. Card available limit is EGP  25000.00. For more details, please visit https://cib.eg/mb",
                sender = "CIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "CARREFOUR EGYPT",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),

            // Test case 4: USD transaction
            ParserTestCase(
                name = "USD Transaction",
                message = "Your credit card ending with#5678 was charged for USD 99.99 at AMAZON on 05/01/26  at 09:15. Card available limit is EGP  15000.50. For more details, please visit https://cib.eg/mb",
                sender = "CIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("99.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "AMAZON",
                    accountLast4 = "5678",
                    isFromCard = true
                )
            ),

            // Test case 5: Refund with larger amount
            ParserTestCase(
                name = "Refund Transaction",
                message = "The transaction on your credit card#9876  from NETFLIX  with USD 15.99 on 20/11/25  at 12:00 has been refunded. Please try again. Thank you",
                sender = "CIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.99"),
                    currency = "USD",
                    type = TransactionType.INCOME,
                    merchant = "NETFLIX",
                    accountLast4 = "9876",
                    isFromCard = true
                )
            ),

            // Negative test cases - should NOT parse

            ParserTestCase(
                name = "OTP Message (Should Not Parse)",
                message = "Your CIB OTP is 123456. Do not share this code with anyone. Valid for 5 minutes.",
                sender = "CIB",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Promotional Message (Should Not Parse)",
                message = "CIB: Enjoy 10% cashback on all purchases this weekend! Terms and conditions apply.",
                sender = "CIB",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Account Balance (Should Not Parse)",
                message = "Your CIB account balance is EGP 50,000.00 as of 24/11/25.",
                sender = "CIB",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "CIB" to true,
            "cib" to true,
            "EG-CIB" to true,
            "EG-CIB-B" to true,
            "HDFC" to false,
            "SBI" to false,
            "FAB" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            testCases,
            handleCases,
            "CIB Egypt Parser Tests"
        )
    }
}
