package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import java.math.BigDecimal

/** All sample account numbers, OTP values, and links are synthetic. */
class CityBankBangladeshParserTest {
    private val parser = CityBankBangladeshParser()

    private fun sms(date: String, channel: String, action: String, balance: String,
                    account: String = "7777***1234", feedback: Boolean = true): String =
        listOfNotNull(
            date,
            "$channel TXN",
            "Tk. $action",
            "Tk. $balance Balance",
            "A/C: $account",
            if (feedback) "Please rate us: https://example.invalid/rate" else null
        ).joinToString("\n")

    @TestFactory
    fun `observed purchase and withdrawal patterns`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "October 02 POS purchase and numeric balance",
                sender = "CITY BANK",
                message = sms("02-Oct-2026", "E-COMM/POS", "685 Purchased", "96,080"),
                expected = ExpectedTransaction(amount = BigDecimal("685"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234", balance = BigDecimal("96080"))
            ),
            ParserTestCase(
                name = "October 07 CITYTOUCH withdrawal Indian-formatted balance",
                sender = "CITY BANK",
                message = sms("07-Oct-2026", "CITYTOUCH", "8,200 Withdrawal", "1,57,937"),
                expected = ExpectedTransaction(amount = BigDecimal("8200"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234", balance = BigDecimal("157937"))
            ),
            ParserTestCase(
                name = "October 12 CITYTOUCH withdrawal",
                sender = "CITY BANK",
                message = sms("12-Oct-2026", "CITYTOUCH", "5,500 Withdrawal", "1,15,533"),
                expected = ExpectedTransaction(amount = BigDecimal("5500"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234", balance = BigDecimal("115533"))
            ),
            ParserTestCase(
                name = "October 14 ATM withdrawal",
                sender = "CITY BANK",
                message = sms("14-Oct-2026", "ATM", "10,000 Withdrawal", "1,05,533"),
                expected = ExpectedTransaction(amount = BigDecimal("10000"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234", balance = BigDecimal("105533"))
            ),
            ParserTestCase(
                name = "October 17 POS purchase with partially masked balance",
                sender = "CITY BANK",
                message = sms("17-Oct-2026", "E-COMM/POS", "3,000 Purchased", "1,***533", "***1234"),
                expected = ExpectedTransaction(amount = BigDecimal("3000"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234")
            ),
            ParserTestCase(
                name = "October 21 POS purchase with numeric balance and feedback link",
                sender = "CITY BANK",
                message = sms("21-Oct-2026", "E-COMM/POS", "1,000 Purchased", "1,01,533"),
                expected = ExpectedTransaction(amount = BigDecimal("1000"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234", balance = BigDecimal("101533"))
            ),
            ParserTestCase(
                name = "October 23 POS purchase",
                sender = "CITY BANK",
                message = sms("23-Oct-2026", "E-COMM/POS", "2,100 Purchased", "99,433"),
                expected = ExpectedTransaction(amount = BigDecimal("2100"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234", balance = BigDecimal("99433"))
            ),
            ParserTestCase(
                name = "October 27 CITYTOUCH withdrawal without rating link",
                sender = "CITY BANK",
                message = sms("27-Oct-2026", "CITYTOUCH", "25,000 Withdrawal", "74,433", feedback = false),
                expected = ExpectedTransaction(amount = BigDecimal("25000"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234", balance = BigDecimal("74433"))
            ),
            ParserTestCase(
                name = "Earlier odd masked balance does not prevent purchase",
                sender = "CITY BANK",
                message = sms("21-Oct-2026", "E-COMM/POS", "1,000 Purchased", "1,***,33", "***1234"),
                expected = ExpectedTransaction(amount = BigDecimal("1000"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234")
            ),
            ParserTestCase(
                name = "Earlier redacted Xxxxx balance does not prevent withdrawal",
                sender = "CITY BANK",
                message = sms("27-Oct-2026", "CITYTOUCH", "25,000 Withdrawal", "Xxxxx", "***1234", false),
                expected = ExpectedTransaction(amount = BigDecimal("25000"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234")
            ),
            ParserTestCase(
                name = "OTP for e-commerce transaction must be excluded",
                sender = "CITY BANK",
                message = "OTP: 0000 for BDT 1 000.00 transaction at EXAMPLE.COM on Card 777**123. Expires in 2 minutes.",
                shouldParse = false
            ),
            ParserTestCase(
                name = "OTP for Citytouch payment must be excluded",
                sender = "CITY BANK",
                message = "OTP is 0000 for Tk. 25,000.00 utility txn in Citytouch, validity 2 min.",
                shouldParse = false
            ),
            ParserTestCase(
                name = "balance-only alert excluded",
                sender = "CITY BANK",
                message = "Tk. 99,433 Balance A/C: 7777***1234",
                shouldParse = false
            ),
            ParserTestCase(
                name = "wrong action for ATM rejected",
                sender = "CITY BANK",
                message = sms("14-Oct-2026", "ATM", "10,000 Purchased", "1,05,533"),
                shouldParse = false
            ),
            ParserTestCase(
                name = "wrong action for POS rejected",
                sender = "CITY BANK",
                message = sms("14-Oct-2026", "E-COMM/POS", "10,000 Withdrawal", "1,05,533"),
                shouldParse = false
            ),
            ParserTestCase(
                name = "unsupported credit format rejected",
                sender = "CITY BANK",
                message = "21-Oct-2026\nCREDIT TXN\nTk. 1,000 Deposited\nTk. 12,000 Balance\nA/C: ***1234",
                shouldParse = false
            ),
            ParserTestCase(
                name = "malformed transaction amount rejected",
                sender = "CITY BANK",
                message = sms("02-Oct-2026", "E-COMM/POS", "1,00 Purchased", "96,080"),
                shouldParse = false
            ),
            ParserTestCase(
                name = "zero transaction amount rejected",
                sender = "CITY BANK",
                message = sms("02-Oct-2026", "E-COMM/POS", "0 Purchased", "96,080"),
                shouldParse = false
            ),
            ParserTestCase(
                name = "no account suffix excluded",
                sender = "CITY BANK",
                message = sms("02-Oct-2026", "E-COMM/POS", "685 Purchased", "96,080", "XXXXXXXX"),
                shouldParse = false
            ),
            ParserTestCase(
                name = "decimal purchase amount",
                sender = "CITY BANK",
                message = sms("02-Oct-2026", "E-COMM/POS", "685.50 Purchased", "96,080.25"),
                expected = ExpectedTransaction(amount = BigDecimal("685.50"), type = TransactionType.EXPENSE,
                    currency = "BDT", accountLast4 = "1234", balance = BigDecimal("96080.25"))
            )
        )
        return ParserTestUtils.runTestSuite(
            parser, cases,
            handleCases = listOf("CITY BANK" to true, "CITYBANK" to true, "CITY-BANK" to true,
                "CITI BANK" to false, "BKASH" to false, "" to false)
        )
    }

    @TestFactory
    fun `masked balances remain null and feedback links are not references`(): List<DynamicTest> = listOf(
        DynamicTest.dynamicTest("masked balance and feedback link") {
            val parsed = parser.parse(
                sms("17-Oct-2026", "E-COMM/POS", "3,000 Purchased", "1,***533"),
                "CITY BANK", 123456789L
            )
            assertNotNull(parsed)
            assertNull(parsed?.balance)
            assertNull(parsed?.reference)
            assertNull(parsed?.merchant)
            assertEquals(123456789L, parsed?.timestamp)
        },
        DynamicTest.dynamicTest("nonnumeric masked balance") {
            val parsed = parser.parse(
                sms("27-Oct-2026", "CITYTOUCH", "25,000 Withdrawal", "Xxxxx"),
                "CITY BANK", 123456789L
            )
            assertNotNull(parsed)
            assertNull(parsed?.balance)
        }
    )

    @TestFactory
    fun `factory dispatches City Bank messages`(): List<DynamicTest> =
        ParserTestUtils.runFactoryTestSuite(
            listOf(
                SimpleTestCase(
                    bankName = "City Bank (Bangladesh)", sender = "CITY BANK", currency = "BDT",
                    message = sms("14-Oct-2026", "ATM", "10,000 Withdrawal", "1,05,533"),
                    expected = ExpectedTransaction(amount = BigDecimal("10000"),
                        type = TransactionType.EXPENSE, currency = "BDT", accountLast4 = "1234",
                        balance = BigDecimal("105533")),
                    shouldHandle = true
                )
            ),
            "City Bank Bangladesh factory routing"
        )
}
