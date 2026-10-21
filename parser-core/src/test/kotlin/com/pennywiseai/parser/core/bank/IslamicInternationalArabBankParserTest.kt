package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class IslamicInternationalArabBankParserTest {

    @TestFactory
    fun `islamic international arab bank parser handles common cases`(): List<DynamicTest> {
        val parser = IslamicInternationalArabBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "Card purchase, city and country stripped",
                message = "حركة شراء ABDEEN HOME CENTER AMMAN JO\nالمبلغ: 12.500 JOD\nحساب: 4321*1234\n" +
                    "تاريخ: 01/09/2026 18:10:05\nالرصيد: 2,345.670 JOD",
                sender = "ARABIISLAMI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.500"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "ABDEEN HOME CENTER",
                    accountLast4 = "4321",
                    balance = BigDecimal("2345.670"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Card purchase, foreign merchant with phone number",
                message = "حركة شراء APPLE.COM/US 800-000-0000 US\nالمبلغ: 7.102 JOD\nحساب: 4321*1234\n" +
                    "تاريخ: 21/07/2026 11:25:39\nالرصيد: 218.610 JOD",
                sender = "ARABIISLAMI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("7.102"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "APPLE.COM/US",
                    balance = BigDecimal("218.610")
                )
            ),
            ParserTestCase(
                name = "Card purchase, unmasked account number agrees on last four",
                message = "حركة شراء Amman Vision Transport 2800*0096JO\nالمبلغ: 0.500 JOD\nحساب: 1234000004321\n" +
                    "تاريخ: 02/09/2026 08:00:00\nالرصيد: 100.000 JOD",
                sender = "ARABIISLAMI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.500"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "Amman Vision Transport",
                    accountLast4 = "4321"
                )
            ),
            ParserTestCase(
                name = "Purchase reversal is income",
                message = "عكس حركة شراء من GOOGLE*TEMPORARY HOLD MOUNTAIN VIEWUS\nالمبلغ: 5.150 JOD\n" +
                    "حساب: 4321*1234\nتاريخ: 22/09/2026 21:52:55\nالرصيد: 3,169.920 JOD",
                sender = "ARABIISLAMI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.150"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "GOOGLE*TEMPORARY HOLD MOUNTAIN VIEW",
                    balance = BigDecimal("3169.920")
                )
            ),
            ParserTestCase(
                name = "CliQ transfer out",
                message = "حركة كليك\nالمبلغ: 15.000 JOD\nحساب: 4321*1234\nإلى: JOHN SMITH\n" +
                    "تاريخ: 10/09/2026 14:00:00\nالرصيد: 1,000.000 JOD",
                sender = "ARABIISLAMI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.000"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN SMITH",
                    accountLast4 = "4321",
                    balance = BigDecimal("1000.000")
                )
            ),
            ParserTestCase(
                name = "CliQ purchase (balance without currency)",
                message = "مشتريات CliQ\nإلى SAMPLE SUPERMARKET\nالمبلغ: 45.000 JOD\nحساب: 4321*1234\n" +
                    "تاريخ: 27/09/2026 15:15:47\nالرصيد: 2,575.070",
                sender = "ARABIISLAMI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.000"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "SAMPLE SUPERMARKET",
                    balance = BigDecimal("2575.070")
                )
            ),
            ParserTestCase(
                name = "CliQ reversal is income",
                message = "عكس دفعة CliQ من JANE DOE\nالمبلغ: 1.000 JOD\nحساب: 4321*1234\n" +
                    "تاريخ: 31/05/2026 10:06:13\nالرصيد: 660.000",
                sender = "ARABIISLAMI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1.000"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "JANE DOE",
                    balance = BigDecimal("660.000")
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal",
                message = "سحب صراف Panorama Mall ATM Amman JO\nالمبلغ: 20.000 JOD\nحساب: 4321*1234\n" +
                    "تاريخ: 25/09/2026 17:00:25\nالرصيد: 3,140.320 JOD\nمرجع: 000000000000",
                sender = "ARABIISLAMI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.000"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    balance = BigDecimal("3140.320")
                )
            ),
            ParserTestCase(
                name = "ATM deposit is income",
                message = "إيداع صراف Jubeiha Branch ATM Amman JO\nالمبلغ: 100.000 JOD\nحساب: 4321*1234\n" +
                    "تاريخ: 15/09/2026 19:00:00\nالرصيد: 3,600.910 JOD",
                sender = "ARABIISLAMI",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.000"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "ATM Deposit",
                    balance = BigDecimal("3600.910")
                )
            ),
            ParserTestCase(
                name = "Online purchase code (OTP) is ignored",
                message = "Your online transaction code is 123456  for card number ending with #XXXX1234 " +
                    "for JOD 3.000 from ORANGE MOBILE. Do not share this code.",
                sender = "ARABIISLAMI",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Savings promo is ignored",
                message = "100 الف دينار الجائزة الكبرى صارت الك اقرب ، افتح أو غذ حساب التوفير وادخل السحب .",
                sender = "ARABIISLAMI",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "ARABIISLAMI" to true,
            "ArabiIslami" to true,
            "ArabBank" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Islamic International Arab Bank Parser")
    }
}
