package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class MellatBankParserTest {

    private val parser = MellatBankParser()

    @TestFactory
    fun `mellat bank parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Mellat Bank withdrawal transaction",
                message = """
                    حساب1234567890
                    برداشت1,250,000
                    مانده18,750,000
                    04/04/21-21:40
                """.trimIndent(),
                sender = "Bank Mellat",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "7890",
                    balance = BigDecimal("18750000")
                )
            ),
            ParserTestCase(
                name = "Mellat Bank deposit transaction",
                message = """
                    حساب1234567890
                    واریز2,500,000
                    مانده21,250,000
                    04/07/15-08:42
                """.trimIndent(),
                sender = "Bank Mellat",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500000"),
                    currency = "IRR",
                    type = TransactionType.INCOME,
                    accountLast4 = "7890",
                    balance = BigDecimal("21250000")
                )
            ),
            ParserTestCase(
                name = "Mellat Bank short term interest deposit transaction",
                message = """
                    واریز سود کوتاه مدت
                    حساب1234567890
                    مبلغ45,670
                    04/06/02
                """.trimIndent(),
                sender = "Bank Mellat",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45670"),
                    currency = "IRR",
                    type = TransactionType.INCOME,
                    accountLast4 = "7890",
                    balance = null
                )
            )
        )

        val handleCases = listOf(
            Pair("Bank Mellat", true),
            Pair("BANKMELLAT", true),
            Pair("MELLAT BANK", true),
            Pair("MELLAT", true),
            Pair("MELLATBANK", true),
            Pair("OTHER", false)
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases)
    }

    @TestFactory
    fun `mellat bank parser ignores non-transaction messages`(): List<DynamicTest> {
        val ignoredMessages = listOf(
            "OTP verification",
            """
                هشدار
                مشتری گرامی  شما درحال برداشت مبلغ 100,000,000 ریال از حساب خود جهت انتقال وجه پایا  به شبای IR990000000000000000000000 به نام IR990000000000000000000000# می باشید. 
                توجه نمایید كه عدد محرمانه: 1234567 یكباررمز شما جهت تایید انتقال وجه پایا  می باشد.
            """.trimIndent(),
            """
                مشتری گرامی
                سلام؛
                جشنواره حساب های قرض الحسنه پس انداز بانک ملت تا پایان شهریور تمدید شد.
                1404 جایزه نقدی 250 میلیون تومانی
                4404 جایزه نقدی 25 میلیون تومانی و هزاران جایزه نقدی دیگر
                بانک ملت، تجربه ای متمایز
            """.trimIndent(),
            """
                به سامانه بانكداری اینترنتی ملت خوش آمدید
                  1404/07/12
                07:49
            """.trimIndent(),
            """
                انتقال به کارت
                603799*1234
                مبلغ 1,000,000
                رمز پویا 12345
            """.trimIndent()
        )

        return ignoredMessages.map { msg ->
            DynamicTest.dynamicTest("Should ignore: ${msg.take(30)}...") {
                org.junit.jupiter.api.Assertions.assertNull(parser.parse(msg, "Bank Mellat", System.currentTimeMillis()))
            }
        }
    }

    @TestFactory
    fun `factory resolves mellat bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Mellat Bank",
                sender = "Bank Mellat",
                currency = "IRR",
                message = """
                    حساب1234567890
                    برداشت1,250,000
                    مانده18,750,000
                    04/04/21-21:40
                """.trimIndent(),
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250000"),
                    currency = "IRR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Mellat Bank",
                sender = "BANKMELLAT",
                currency = "IRR",
                message = """
                    حساب1234567890
                    واریز2,500,000
                    مانده21,250,000
                    04/07/15-08:42
                """.trimIndent(),
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500000"),
                    currency = "IRR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Mellat Bank factory tests")
    }
}
