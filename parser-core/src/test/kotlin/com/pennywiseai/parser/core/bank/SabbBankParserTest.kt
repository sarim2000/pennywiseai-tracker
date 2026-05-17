package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SabbBankParserTest {

    @TestFactory
    fun `sabb parser handles representative messages`(): List<DynamicTest> {
        val parser = SabbBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "POS purchase via Samsung Pay",
                message = """
                    شراء عبر نقاط البيع
                    بطاقة: ***1111;mada(Samsung Pay);
                    مبلغ: SAR 56.00
                    لدى: TANOOR ALTAHI REST×
                    في: 2026-05-06 20:02:46
                """.trimIndent(),
                sender = "SAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "TANOOR ALTAHI REST",
                    accountLast4 = "1111",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Online / internet purchase via Mada",
                message = """
                    شراء إنترنت
                    بطاقة: ***1111;مدى
                    من: ***999
                    مبلغ: 126.28 SAR
                    لدى: AMAZON SA××AL Madin
                    في: 2026-04-28 09:35:17
                """.trimIndent(),
                sender = "SAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("126.28"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "AMAZON SA××AL Madin",
                    accountLast4 = "1111",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Outgoing local transfer with fees",
                message = """
                    حوالة صادرة مقبولة
                    من: **9999
                    إلى: KHALID Ahmed
                    آيبان: **8888
                    بنك إس تي سي
                    مبلغ: SAR 200.00
                    رسوم: SAR 0.57
                    في: 2026-04-30 11:04:57
                """.trimIndent(),
                sender = "SAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "KHALID Ahmed",
                    accountLast4 = "9999"
                )
            ),
            ParserTestCase(
                name = "Incoming transfer / deposit",
                message = """
                    إيداع حوالة واردة
                    من: FAHAD Ahmed
                    إلى: **9999
                    آيبان: **7777
                    البنك العربي الوطني
                    مبلغ: SAR 75.00
                    في: 2026-05-05 00:59:09
                """.trimIndent(),
                sender = "SAB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.00"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "FAHAD Ahmed",
                    accountLast4 = "9999"
                )
            )
        )

        val handleCases = listOf(
            "SAB" to true,
            "JD-SAB-S" to true,
            "SABB" to true,
            "JD-HDFCBK-S" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "SABB Parser Suite"
        )
    }
}
