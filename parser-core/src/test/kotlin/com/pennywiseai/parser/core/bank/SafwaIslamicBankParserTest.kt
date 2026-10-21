package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SafwaIslamicBankParserTest {

    @TestFactory
    fun `safwa islamic bank parser handles common cases`(): List<DynamicTest> {
        val parser = SafwaIslamicBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "Cash deposit at self-service branch",
                message = "دفعة نقدية(فرع آلي) بمبلغ 150.000 دينار الرصيد المتوفر 175.250 دينار",
                sender = "Safwa Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.000"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "Cash Deposit",
                    balance = BigDecimal("175.250"),
                    expectNullAccountLast4 = true,
                    // Numberless SMS: balance kept on one bank-level account.
                    isMobileWallet = true
                )
            ),
            ParserTestCase(
                name = "Bank fees",
                message = "تغطية عمولات البنك بمبلغ 0.500 دينار الرصيد المتوفر 174.750 دينار",
                sender = "Safwa Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.500"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "Bank Fees",
                    balance = BigDecimal("174.750"),
                    isMobileWallet = true
                )
            ),
            ParserTestCase(
                name = "OTP is ignored",
                message = "متعاملنا الكريم،\nيرجى استخدام رمز التحقق (OTP) لإتمام العملية المطلوبة. لا\n" +
                    "تشارك هذا الرمز مع أي شخص.\nرمز التحقق OTP 123456",
                sender = "Safwa Bank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Card purchase code is ignored",
                message = "Your Code for your transaction of 01-01-2026 12:00:00 with amount 9.99 USD is : 123456 , " +
                    "please don’t share this code to anyone.",
                sender = "Safwa Bank",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "Safwa Bank" to true,
            "SafwaBank" to true,
            "Safwa" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Safwa Islamic Bank Parser")
    }
}
