package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class ADCBParserTest {
    @TestFactory
    fun `test ADCB Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = ADCBParser()

        ParserTestUtils.printTestHeader(
            parserName = "Abu Dhabi Commercial Bank (ADCB)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit Card Purchase (AED)",
                message = "Your debit card XXX1234 linked to acc. XXX810001 was used for AED100.50 on Jul 10 2024  5:49PM at MERCHANT123,AE. Avl.Bal AED 200.75.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.50"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "MERCHANT123",
                    accountLast4 = "810001",
                    balance = BigDecimal("200.75")
                )
            ),

            ParserTestCase(
                name = "Debit Card Purchase (Foreign Currency)",
                message = "Your debit card XXX1234 linked to acc. XXX810001 was used for USD50.25 on Jul 11 2024  1:00PM at ONLINEPLATFORM,GE. Avl.Bal AED 150.50.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.25"),
                    currency = "USD",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "ONLINEPLATFORM",
                    accountLast4 = "810001",
                    balance = BigDecimal("150.50")
                )
            ),

            ParserTestCase(
                name = "ATM Deposit",
                message = "AED5000.00 has been deposited via ATM in your account XXX810001 on Jan 16 2025 16:56 at LOCATION123. Available Balance is AED5200.25.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "ATM Deposit: LOCATION123",
                    accountLast4 = "810001",
                    balance = BigDecimal("5200.25")
                )
            ),

            ParserTestCase(
                name = "Bank Transfer",
                message = "AED750.50 transferred via ADCB Personal Internet Banking / Mobile App from acc. no. XXX810001 on Feb  4 2025 12:49PM. Avl. bal. AED 2000.00.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.50"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.TRANSFER,
                    merchant = "Transfer via ADCB Banking",
                    accountLast4 = "810001",
                    balance = BigDecimal("2000.00")
                )
            ),

            ParserTestCase(
                name = "Account Credit",
                message = "A Cr. transaction of AED 200.00 on your account number XXX810001 was successful.Available balance is AED 220.25.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "Account Credit",
                    accountLast4 = "810001",
                    balance = BigDecimal("220.25")
                )
            ),

            ParserTestCase(
                name = "Account Debit",
                message = "A Dr. transaction of AED 2.10 on your account number XXX810001 was successful.Available balance is 13697.16.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.10"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Account Debit",
                    accountLast4 = "810001",
                    balance = BigDecimal("13697.16")
                )
            ),

            ParserTestCase(
                name = "ATM Withdrawal (AED)",
                message = "AED1500.50 withdrawn from acc. XXX810001 on Jan 8 2025 3:07PM at ATM-BANK123. Avl.Bal.AED1200.75. Be cautious with large amt. of cash.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.50"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal: BANK123",
                    accountLast4 = "810001",
                    balance = BigDecimal("1200.75")
                )
            ),

            ParserTestCase(
                name = "ATM Withdrawal (Foreign Currency)",
                message = "EUR350.25 withdrawn from acc. XXX810001 on Jun 16 2025 5:07PM at ATM-SHOPPINGMALL. Avl.Bal.AED150.50. Be cautious with large amt. of cash.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350.25"),
                    currency = "EUR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal: SHOPPINGMALL",
                    accountLast4 = "810001",
                    balance = BigDecimal("150.50")
                )
            ),

            ParserTestCase(
                name = "ATM Withdrawal with Numeric ID",
                message = "GBP4500.75 withdrawn from acc. XXX810001 on Dec 24 2024 11:14PM at ATM-123456LOCATION123. Avl.Bal.AED250.25. Be cautious with large amt. of cash.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4500.75"),
                    currency = "GBP",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal: LOCATION123",
                    accountLast4 = "810001",
                    balance = BigDecimal("250.25")
                )
            ),

            ParserTestCase(
                name = "Debit Card Purchase (THB - No Space)",
                message = "Your debit card XXX0830 linked to acc. XXX810001 was used for THB28.25 on Jun 16 2025  5:02PM at SHOPPING MALL,TH. Avl.Bal AED 321.56.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("28.25"),
                    currency = "THB",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "SHOPPING MALL",
                    accountLast4 = "810001",
                    balance = BigDecimal("321.56")
                )
            ),

            ParserTestCase(
                name = "Debit Card Purchase (AED - No Space)",
                message = "Your debit card XXX0830 linked to acc. XXX810001 was used for AED26.80 on Jun 13 2025  4:28PM at TRANSPORT SERVICE,AE. Avl.Bal AED 2928.77.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("26.80"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "TRANSPORT SERVICE",
                    accountLast4 = "810001",
                    balance = BigDecimal("2928.77")
                )
            ),

            ParserTestCase(
                name = "TouchPoints Redemption",
                message = "TouchPoints Redemption Request registered successfully on 11-06-2025 15:58:58 Amount Paid: AED 100.00 TouchPoints Remaining: 344.",
                sender = "ADCBAlert",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "AED",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "TouchPoints Redemption",
                    accountLast4 = null,
                    balance = null
                )
            ),

            // Test cases that should NOT be parsed (non-transaction messages)
            ParserTestCase(
                name = "Failed Transaction (Should Not Parse)",
                message = "Transaction of USD 75.50 made on Jul 13, 2024 12:30AM on your Debit Card XXX1234 could not be completed due to insufficient funds. For assistance, please call BANK_HOTLINE",
                sender = "ADCBAlert",
                shouldParse = false,
                expected = ExpectedTransaction(
                    amount = BigDecimal.ZERO,
                    currency = "",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE
                )
            ),

            ParserTestCase(
                name = "OTP Message (Should Not Parse)",
                message = "Do not share your OTP with anyone. If not initiated by you, please call BANK_HOTLINE. OTP for transaction at RETAILER for THB 25.00 on your ADCB Debit Car...",
                sender = "ADCBAlert",
                shouldParse = false,
                expected = ExpectedTransaction(
                    amount = BigDecimal.ZERO,
                    currency = "",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE
                )
            ),

            ParserTestCase(
                name = "Card Management (Should Not Parse)",
                message = "Your digital card assigned to ADCB Debit Card XXX1234 for PAYMENT_SERVICE has been de-activated. Please call BANK_HOTLINE if you have not initiated this request.",
                sender = "ADCBAlert",
                shouldParse = false,
                expected = ExpectedTransaction(
                    amount = BigDecimal.ZERO,
                    currency = "",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE
                )
            ),

            ParserTestCase(
                name = "Activation Message (Should Not Parse)",
                message = "Activation Key for your ADCB App is ACTIVATION_CODE Valid for 24 hours. Do not share with anyone. If not initiated by you, please call BANK_HOTLINE",
                sender = "ADCBAlert",
                shouldParse = false,
                expected = ExpectedTransaction(
                    amount = BigDecimal.ZERO,
                    currency = "",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "ADCBAlert" to true,
            "ADCBBank" to true,
            "ADCB-UAE" to true,
            "HDFC" to false,
            "SBI" to false,
            "" to false
        )
        val result =
            return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "ADCB Parser Tests")

    }
}