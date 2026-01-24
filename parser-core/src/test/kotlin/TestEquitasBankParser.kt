package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class EquitasBankParserTest {
    @TestFactory
    fun `test Equitas Small Finance Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = EquitasBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Equitas Small Finance Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // UPI Debit Transaction
            ParserTestCase(
                name = "UPI Debit Transaction",
                message = "INR 500.00 debited via UPI from Equitas A/c 1234 -Ref:571987071234 on 19-12-25 to JOHN DOE. Avl Bal is INR 15,000.50.Not U?Call 18001031222/SMS BLOCK UPI/BLOCK ACT 1233 to 7045030000.",
                sender = "CP-EQUTAS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN DOE",
                    accountLast4 = "1234",
                    balance = BigDecimal("15000.50"),
                    reference = "571987071234",
                    isFromCard = false
                )
            ),

            // UPI Debit with merchant name
            ParserTestCase(
                name = "UPI Debit to Merchant",
                message = "INR 1,250.00 debited via UPI from Equitas A/c 5678 -Ref:987654321098 on 15-01-26 to SWIGGY INDIA. Avl Bal is INR 8,500.25.Not U?Call 18001031222/SMS BLOCK UPI/BLOCK ACT 1233 to 7045030000.",
                sender = "CP-EQUTAS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "SWIGGY INDIA",
                    accountLast4 = "5678",
                    balance = BigDecimal("8500.25"),
                    reference = "987654321098",
                    isFromCard = false
                )
            ),

            // UPI Credit Transaction
            ParserTestCase(
                name = "UPI Credit Transaction",
                message = "INR 2,000.00 credited via UPI to Equitas A/c 9012 -Ref:123456789012 on 18-01-26 from EMPLOYER NAME. Avl Bal is INR 25,000.00.Not U?Call 18001031222.",
                sender = "CP-EQUTAS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "EMPLOYER NAME",
                    accountLast4 = "9012",
                    balance = BigDecimal("25000.00"),
                    reference = "123456789012",
                    isFromCard = false
                )
            ),

            // Small amount transaction
            ParserTestCase(
                name = "Small Amount UPI Debit",
                message = "INR 10.00 debited via UPI from Equitas A/c 3456 -Ref:111222333444 on 19-01-26 to CHAI WALA. Avl Bal is INR 500.00.Not U?Call 18001031222.",
                sender = "CP-EQUTAS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "CHAI WALA",
                    accountLast4 = "3456",
                    balance = BigDecimal("500.00"),
                    reference = "111222333444",
                    isFromCard = false
                )
            ),

            // Negative test cases - should NOT parse
            ParserTestCase(
                name = "OTP Message Should Not Parse",
                message = "Your Equitas Bank OTP for UPI transaction is 123456. Do not share with anyone.",
                sender = "CP-EQUTAS-S",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Promotional Message Should Not Parse",
                message = "Equitas Bank: Get 5% cashback offer on all UPI transactions! T&C apply.",
                sender = "CP-EQUTAS-S",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "CP-EQUTAS-S" to true,
            "VM-EQUTAS-S" to true,
            "AX-EQUITA-S" to true,
            "EQUTAS" to true,
            "HDFC" to false,
            "SBI" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            testCases,
            handleCases,
            "Equitas Small Finance Bank Parser Tests"
        )
    }
}
