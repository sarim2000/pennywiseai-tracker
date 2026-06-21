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
                name = "Jordan card spend, JOD transaction (EXPENSE, 3 decimals)",
                message = "A Trx using Card XXXX9915 from ADANI CORNER for JOD 2.750 " +
                    "on 20-Jun-2026 at 14:21 GMT+3. Available balance is JOD 1649.832.",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.750"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "ADANI CORNER",
                    accountLast4 = "9915",
                    balance = BigDecimal("1649.832"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Jordan CliQ transfer debit (EXPENSE, suffix balance, not a card)",
                message = "JOD50.000 has been debited from 0156*500 to John Smith " +
                    "as CliQ transfer Balance 37.920JOD",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.000"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "John Smith",
                    accountLast4 = "6500",
                    balance = BigDecimal("37.920"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Jordan CliQ transfer credit (INCOME, no-space to/from, not a card)",
                message = "JOD172.000 has been credited to 0156*500from Jane Doe " +
                    "as CliQ transfer Balance 959.370JOD",
                sender = "ArabBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("172.000"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "Jane Doe",
                    accountLast4 = "6500",
                    balance = BigDecimal("959.370"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "OTP message is rejected",
                message = "Your OTP for Arab Bank is 123456. Do not share it with anyone.",
                sender = "ArabBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Arabic OTP message is rejected",
                // "Your verification code for Arab Bank is 123456" — رمز التحقق = verification code.
                message = "رمز التحقق الخاص بك في Arab Bank هو 123456",
                sender = "ArabBank",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "ArabBank" to true,
            "ARABBANK" to true,
            "AD-ARABBANK" to true,
            "AB-ARABBK-S" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Arab Bank Parser")
    }
}
