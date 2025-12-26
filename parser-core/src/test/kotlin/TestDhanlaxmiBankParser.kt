package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class DhanlaxmiBankParserTest {
    @TestFactory
    fun `test Dhanlaxmi Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = DhanlaxmiBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Dhanlaxmi Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Test case 1: UPI Debit transaction
            ParserTestCase(
                name = "UPI Debit Transaction",
                message = "INR 20.00 is debited from A/c XXXX1234 on 28-NOV-2025 - \"UPI TXN: /675325120952-MR /Payment from PhonePe/Q12345444@ybl/YESB0YBLUPI/4172120251128000100004392\". Aval Bal is INR 26,578.49. If not transacted call 044-42413000.-DhanlaxmiBank",
                sender = "TL-DHANBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "PhonePe",
                    accountLast4 = "1234",
                    balance = BigDecimal("26578.49"),
                    isFromCard = false
                )
            ),

            // Test case 2: UPI Credit transaction
            ParserTestCase(
                name = "UPI Credit Transaction",
                message = "INR 10.00 is credited to A/c XXXX1234 on 24-APR-2025 - \"UPI TXN: /398353431145-Paytm/payment on Myntra using UPI on Mar 25 2025/one97987\u00a1axisbank/911188478932/41721202504240001\". Aval Bal is INR 36,278.92 -DhanlaxmiBank",
                sender = "VM-DHANBK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Myntra",
                    accountLast4 = "1234",
                    balance = BigDecimal("36278.92"),
                    isFromCard = false
                )
            ),

            // Test case 3: Internal transfer (credit)
            ParserTestCase(
                name = "Internal Transfer Credit",
                message = "Your a/c no. XXXXXXXX1234 is credited for Rs.10.00 on 24-04-25 and debited from a/c no. XXXXXXXX0987 (UPI Ref no 398353431145).-DhanlaxmiBank",
                sender = "VM-DHANBK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Internal Transfer",
                    accountLast4 = "1234",
                    isFromCard = false
                )
            ),

            // Test case 4: Large amount debit
            ParserTestCase(
                name = "Large Amount Debit",
                message = "INR 50,000.00 is debited from A/c XXXX5678 on 15-DEC-2025 - \"UPI TXN: /123456789012-MR /Payment from GPay\". Aval Bal is INR 1,25,000.50. If not transacted call 044-42413000.-DhanlaxmiBank",
                sender = "TL-DHANBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "GPay",
                    accountLast4 = "5678",
                    balance = BigDecimal("125000.50"),
                    isFromCard = false
                )
            ),

            // Test case 5: Credit with simple merchant
            ParserTestCase(
                name = "UPI Credit Simple",
                message = "INR 500.00 is credited to A/c XXXX9999 on 01-JAN-2026 - \"UPI TXN: /111222333444-Salary/Payment from Employer\". Aval Bal is INR 75,500.00 -DhanlaxmiBank",
                sender = "VM-DHANBK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Employer",
                    accountLast4 = "9999",
                    balance = BigDecimal("75500.00"),
                    isFromCard = false
                )
            ),

            // Negative test cases - should NOT parse

            ParserTestCase(
                name = "OTP Message (Should Not Parse)",
                message = "Your Dhanlaxmi Bank OTP is 123456. Do not share this code with anyone. Valid for 5 minutes.",
                sender = "VM-DHANBK",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Promotional Message (Should Not Parse)",
                message = "Dhanlaxmi Bank: Get 5% cashback on all UPI transactions this festive season!",
                sender = "VM-DHANBK",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Balance Inquiry (Should Not Parse)",
                message = "Your Dhanlaxmi Bank account balance is INR 50,000.00 as of 28-NOV-2025.",
                sender = "VM-DHANBK",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "TL-DHANBK-S" to true,
            "VM-DHANBK" to true,
            "DHANBK" to true,
            "dhanbk" to true,
            "BM-DHANBK" to true,
            "DHANLAXMI" to true,
            "HDFC" to false,
            "SBI" to false,
            "ICICI" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            testCases,
            handleCases,
            "Dhanlaxmi Bank Parser Tests"
        )
    }
}
