package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BankMuscatParserTest {

    @TestFactory
    fun `test Bank Muscat parser`(): List<DynamicTest> {
        val parser = BankMuscatParser()

        ParserTestUtils.printTestHeader(
            parserName = "Bank Muscat",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit card purchase - merchant with leading ID",
                message = "تم خصم 0.650 OMR من حسابك رقم XXXXX9999 بإستخدام بطاقة الخصم المباشر في 757487-MASAKEN AL RAHA LLC KHOOM بتاريخ 2026/03/02 14:43:57. رصيدك الحالي هو 9999.740 OMR.",
                sender = "BankMuscat",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.650"),
                    currency = "OMR",
                    type = TransactionType.EXPENSE,
                    merchant = "MASAKEN AL RAHA LLC KHOOM",
                    accountLast4 = "9999",
                    balance = BigDecimal("9999.740"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Debit card purchase - merchant with trailing ID",
                message = "تم خصم OMR 0.100 من حسابك رقم XXXXXXX9999 بإستخدام بطاقة الخصم المباشر في Break Point QURU-650068 بتاريخ 2026/04/01 17:54:40. رصيدك الحالي هو 9999.740 OMR.",
                sender = "BankMuscat",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.100"),
                    currency = "OMR",
                    type = TransactionType.EXPENSE,
                    merchant = "Break Point QURU",
                    accountLast4 = "9999",
                    balance = BigDecimal("9999.740"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Debit card purchase - merchant with middle ID",
                message = "تم خصم OMR 0.300 من حسابك رقم XXXXXXX9999 بإستخدام بطاقة الخصم المباشر في MASAKEN AL RAHA LLC-833468 KHOOM بتاريخ 2026/04/02 16:15:38. رصيدك الحالي هو 9999.740 OMR.",
                sender = "BankMuscat",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.300"),
                    currency = "OMR",
                    type = TransactionType.EXPENSE,
                    merchant = "MASAKEN AL RAHA LLC KHOOM",
                    accountLast4 = "9999",
                    balance = BigDecimal("9999.740"),
                    isFromCard = true
                )
            )
        )

        val handleCases = listOf(
            "BankMuscat" to true,
            "BKMUSCAT" to true,
            "bank muscat" to true,
            "HSBC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Bank Muscat Parser Tests")
    }
}
