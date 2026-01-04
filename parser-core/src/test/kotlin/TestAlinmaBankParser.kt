package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class AlinmaBankParserTest {
    @TestFactory
    fun `test Alinma Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = AlinmaBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Alinma Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Example 1: Samsung Wallet POS purchase
            ParserTestCase(
                name = "Samsung Wallet POS Purchase - Generic",
                message = """شراء محلي من نقاط البيع
عبر Samsung Wallet
بمبلغ: 50 SAR
البطاقة: **1234
حساب: **5678
من: Establishment Name
في: 2025-10-10 20:00:00
الرصيد: 500.50 SAR""",
                sender = "Alinma",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50"),
                    currency = "SAR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Establishment Name",
                    accountLast4 = "5678",
                    balance = BigDecimal("500.50"),
                    isFromCard = true
                )
            ),

            // Example 2: Meed Express purchase
            ParserTestCase(
                name = "Samsung Wallet POS Purchase - Meed Express",
                message = """شراء محلي من نقاط البيع
عبر Samsung Wallet
بمبلغ: 3 SAR
البطاقة: **0000
حساب: **0000
من: Meed Express
في: 2025-10-15 08:44:32
الرصيد: 101.00 SAR""",
                sender = "Alinma",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3"),
                    currency = "SAR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Meed Express",
                    accountLast4 = "0000",
                    balance = BigDecimal("101.00"),
                    isFromCard = true
                )
            ),

            // Example 3: Credit card POS purchase
            ParserTestCase(
                name = "Credit Card POS Purchase",
                message = """شراء عبر: POS
البطاقة الائتمانية: **9999
مبلغ: SAR 125.50
لدى: Commercial Self-Technolog
في: 12:00 2025-10-20
الرصيد: 875.75 ريال""",
                sender = "Alinma",
                expected = ExpectedTransaction(
                    amount = BigDecimal("125.50"),
                    currency = "SAR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Commercial Self-Technolog",
                    accountLast4 = "9999",
                    balance = BigDecimal("875.75"),
                    isFromCard = true
                )
            ),

            // Example 4: Mada card purchase with reversed card format
            ParserTestCase(
                name = "Mada Card Purchase - Samsung Wallet",
                message = """شراء عبر Samsung Wallet
مبلغ: ريال سعودى 75.25
بطاقة مدى: 7777*
حساب: *8888
من: Establishment Name
في: 2025-10-10 21:00""",
                sender = "Alinma",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.25"),
                    currency = "SAR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Establishment Name",
                    accountLast4 = "8888",
                    isFromCard = true
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "Alinma" to true,
            "ALINMA" to true,
            "alinma" to true,
            "الإنماء" to true,
            "HDFC" to false,
            "SBI" to false,
            "" to false
        )

        val result =
            return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Alinma Bank Parser Tests")

    }
}
