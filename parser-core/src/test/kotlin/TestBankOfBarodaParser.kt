package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class BankOfBarodaParserTest {
    @TestFactory
    fun `test Bank of Baroda Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = BankOfBarodaParser()

        ParserTestUtils.printTestHeader(
            parserName = "Bank of Baroda",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Transfer Transactions (New Pattern - reported issue)
            ParserTestCase(
                name = "Account Transfer to Loan Recovery",
                message = "Rs.29 transferred from A/c ...5494 to:Loan Recovery Fo. Total Bal:Rs.24898.57CR. Avlbl Amt:Rs.24898.57(04-11-2025 04:03:09) - Bank of Baroda",
                sender = "VM-BOBTXN-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("29"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Loan Recovery Fo",
                    accountLast4 = "5494",
                    balance = BigDecimal("24898.57"),
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "Account Transfer to Individual",
                message = "Rs.1500.00 transferred from A/c ...1234 to:John Smith. Total Bal:Rs.15000.00CR. Avlbl Amt:Rs.15000.00(10-11-2025 10:30:00) - Bank of Baroda",
                sender = "VM-BOBTXN-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "John Smith",
                    accountLast4 = "1234",
                    balance = BigDecimal("15000.00"),
                    isFromCard = false
                )
            ),

            // Debit Transactions
            ParserTestCase(
                name = "Debit from Account",
                message = "Rs.80.00 Dr. from A/c XX123456 on 12-11-2024. AvlBal:Rs1234.56cx. Ref:52211012345 -Bank of Baroda",
                sender = "VM-BOBTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("80.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "3456",
                    balance = BigDecimal("1234.56"),
                    reference = "52211012345",
                    isFromCard = false
                )
            ),

            // UPI Credit Transactions
            ParserTestCase(
                name = "UPI Credit to Account",
                message = "Rs.500.00 Cr. to redacted@ybl A/c XX789012 on 15-11-2024. AvlBal:Rs5678.90. Ref:987654321 -Bank of Baroda",
                sender = "VM-BOBSMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "UPI Payment",
                    accountLast4 = "9012",
                    balance = BigDecimal("5678.90"),
                    reference = "987654321",
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "UPI Credit with Real VPA",
                message = "Rs.1000.00 Cr. to merchant@okaxis A/c XX345678 on 16-11-2024. AvlBal:Rs10000.00. Ref:1234567890 -Bank of Baroda",
                sender = "VM-BOBSMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "merchant",
                    accountLast4 = "5678",
                    balance = BigDecimal("10000.00"),
                    reference = "1234567890",
                    isFromCard = false
                )
            ),

            // IMPS Transactions
            ParserTestCase(
                name = "IMPS Transfer by Person",
                message = "Rs.2500.00 credited to A/c XX456789 via IMPS/518233445566 by JOHN DOE. AvlBal:Rs25000.00 -Bank of Baroda",
                sender = "VM-BOBTXN-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "JOHN DOE",
                    accountLast4 = "6789",
                    balance = BigDecimal("25000.00"),
                    reference = "518233445566",
                    isFromCard = false
                )
            ),

            // Cash Deposit
            ParserTestCase(
                name = "Cash Deposit to Account",
                message = "Rs.10000.00 deposited in cash to A/c XX234567 on 20-11-2024. AvlBal:Rs45000.00 -Bank of Baroda",
                sender = "VM-BOBSMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "Cash Deposit",
                    accountLast4 = "4567",
                    balance = BigDecimal("45000.00"),
                    isFromCard = false
                )
            ),

            // Credit Card Transactions
            ParserTestCase(
                name = "Credit Card Purchase",
                message = "ALERT: INR 1,500.00 is spent on your BOBCARD ending 1234 at AMAZON on 25-11-2024 10:30:00. Available credit limit is Rs 42,981.46 -Bank of Baroda",
                sender = "VM-BOBCRD-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = null,
                    accountLast4 = "1234",
                    creditLimit = BigDecimal("42981.46"),
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Credit Card Spent Pattern",
                message = "ALERT: INR 250.50 spent on your BOBCARD ending 5678 at SWIGGY on 26-11-2024. -Bank of Baroda",
                sender = "VM-BOBCRD-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.50"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    accountLast4 = "5678",
                    isFromCard = true
                )
            ),

            // Credited with INR pattern
            ParserTestCase(
                name = "Account Credited with INR",
                message = "Your A/c XX987654 is credited with INR 70.00 on 30-11-2024. Total Bal:Rs.5000.00 -Bank of Baroda",
                sender = "VM-BOBSMS",
                expected = ExpectedTransaction(
                    amount = BigDecimal("70.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    accountLast4 = "7654",
                    balance = BigDecimal("5000.00"),
                    isFromCard = false
                )
            ),

            // Rs.XX Credited to pattern
            ParserTestCase(
                name = "Amount Credited to Account",
                message = "Rs.5000.00 Credited to A/c XX112233 on 01-12-2024. AvlBal:Rs12345.67 -Bank of Baroda",
                sender = "VM-BOBTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    accountLast4 = "2233",
                    balance = BigDecimal("12345.67"),
                    isFromCard = false
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "VM-BOBTXN-S" to true,
            "VM-BOBTXN" to true,
            "VM-BOBSMS" to true,
            "VM-BOBCRD-S" to true,
            "AD-BOBTXN-S" to true,
            "JM-BOB-S" to true,
            "BOB" to true,
            "BANKOFBARODA" to true,
            "BOBSMS" to true,
            "BOBTXN" to true,
            "BOBCRD" to true,
            "HDFC" to false,
            "ICICI" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser,
            testCases,
            handleCases,
            "Bank of Baroda Parser Tests"
        )

    }
}
