package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class IDFCFirstBankParserTest {
    @TestFactory
    fun `test IDFC First Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = IDFCFirstBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "IDFC First Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Foreign currency credit card transactions
            ParserTestCase(
                name = "EUR Credit Card Transaction",
                message = "Transaction Successful! EUR 500.00 spent on your IDFC FIRST Bank Credit Card ending XX1234 at AMAZON EU on 08-FEB-2025 at 01:28 PM Avbl Limit: INR 4074.10 If not done by you, call 180010888",
                sender = "BM-IDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "EUR",
                    type = TransactionType.EXPENSE,
                    merchant = "AMAZON EU",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "USD Credit Card Transaction",
                message = "Transaction Successful! USD 99.99 spent on your IDFC FIRST Bank Credit Card ending XX5678 at NETFLIX on 15-MAR-2025 at 10:00 AM Avbl Limit: INR 25000.00",
                sender = "BM-IDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("99.99"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "NETFLIX",
                    accountLast4 = "5678",
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "GBP Credit Card Transaction",
                message = "Transaction Successful! GBP 150.00 spent on your IDFC FIRST Bank Credit Card ending XX9999 at LONDON SHOP on 20-APR-2025 at 03:45 PM Avbl Limit: INR 10000.00",
                sender = "AX-IDFCBK-T",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "GBP",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9999",
                    isFromCard = true
                )
            ),

            // Regular INR transactions (ensure no regression)
            ParserTestCase(
                name = "INR Debit Transaction",
                message = "Your A/C XXXXXXX1234 is debited by INR 68.00 on 06/08/25 17:36. New Bal :INR 5000.00",
                sender = "BM-IDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("68.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("5000.00"),
                    isFromCard = false
                )
            ),

            // Debit with merchant credited pattern (e.g., travel booking)
            ParserTestCase(
                name = "Debit with Merchant Credited Pattern (REDBUS)",
                message = "Your A/c XX4614 debited by Rs. 1,172.06 on 15/01/26; REDBUS credited. RRN 060649915527. Available balance Rs. 9,134.15. Team IDFC FIRST Bank",
                sender = "JM-IDFCFB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1172.06"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "REDBUS",
                    accountLast4 = "4614",
                    balance = BigDecimal("9134.15"),
                    reference = "060649915527",
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "INR Credit Transaction",
                message = "Your A/C XXXXXXX5678 is credited by INR 500.00 on 06/08/25 17:36. New Bal :INR 10000.00",
                sender = "BM-IDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("10000.00"),
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "Monthly Interest Credit",
                message = "Your A/C XXXXXXX1234 is credited by INR 125.50 on 01/01/25 for monthly interest. New Bal :INR 15125.50",
                sender = "BM-IDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("125.50"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Interest Credit",
                    accountLast4 = "1234",
                    balance = BigDecimal("15125.50"),
                    isFromCard = false
                )
            ),

            // Negative test cases - should NOT parse
            ParserTestCase(
                name = "OTP Message (Should Not Parse)",
                message = "Your IDFC First Bank OTP is 123456. Do not share this code with anyone.",
                sender = "BM-IDFCBK-S",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Promotional Message (Should Not Parse)",
                message = "IDFC First Bank: Get 10% cashback offer on all online purchases!",
                sender = "BM-IDFCBK-S",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "BM-IDFCBK-S" to true,
            "AX-IDFCBK-T" to true,
            "AD-IDFCB-S" to true,
            "IDFCBK" to true,
            "IDFC" to true,
            "HDFC" to false,
            "SBI" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            testCases,
            handleCases,
            "IDFC First Bank Parser Tests"
        )
    }
}
