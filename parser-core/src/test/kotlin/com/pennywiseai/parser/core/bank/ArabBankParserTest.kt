package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class ArabBankParserTest {

    @TestFactory
    fun `arab bank parser handles common cases`(): List<DynamicTest> {
        val parser = ArabBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "English card spend, EGP transaction (EXPENSE)",
                message = "A Trx using Card XXXX2020 from Top Up ETISALAT Egypt for EGP 123.45 " +
                    "on 18-Jun-2026 at 13:59 GMT+3. Available balance is EGP 9876.54.",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("123.45"),
                    currency = "EGP",
                    type = TransactionType.EXPENSE,
                    merchant = "Top Up ETISALAT Egypt",
                    accountLast4 = "2020",
                    balance = BigDecimal("9876.54"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "English card spend, USD transaction with EGP balance (EXPENSE)",
                message = "A Trx using Card XXXX2020 from PORKBUN COM for USD 9.84 " +
                    "on 05-Oct-2025 at 07:48 GMT+2. Available balance is EGP 5432.10.",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("9.84"),
                    currency = "USD",
                    type = TransactionType.EXPENSE,
                    merchant = "PORKBUN COM",
                    accountLast4 = "2020",
                    balance = BigDecimal("5432.10"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Arabic credit to the card (INCOME)",
                message = "تم قيد مبلغ 500.00 جنيه لبطاقتك الائتمانية رقم #2020",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "EGP",
                    type = TransactionType.INCOME,
                    accountLast4 = "2020",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "OTP message is rejected",
                message = "Your OTP for Arab Bank is 123456. Do not share it with anyone.",
                sender = "ArabBank",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "ArabBank" to true,
            "ARABBANK" to true,
            "AD-ARABBANK" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Arab Bank Parser")
    }
}
