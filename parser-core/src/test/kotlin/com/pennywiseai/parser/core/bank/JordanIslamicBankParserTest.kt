package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class JordanIslamicBankParserTest {

    @TestFactory
    fun `jordan islamic bank parser handles common cases`(): List<DynamicTest> {
        val parser = JordanIslamicBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "Card authorisation, local merchant (fixed-width card field)",
                message = "تفويض حركة على حسابكم 1234567 - 003 بقيمة 3.59 دينار اردني من " +
                    "HYPERMAX - UNIV.STR.     AMMAN        JO الرصيد المتوفر 214.882 دينار اردني",
                sender = "JIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3.59"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "HYPERMAX - UNIV.STR.",
                    accountLast4 = "4567",
                    balance = BigDecimal("214.882"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "Card authorisation, name fills all 25 chars and touches the city",
                message = "تفويض حركة على حسابكم 1234567 - 003 بقيمة 12.4 دينار اردني من " +
                    "MILITARY CONSUMER EST SWIAMMAN JORDAN JO الرصيد المتوفر 98.5 دينار اردني",
                sender = "JIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.4"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "MILITARY CONSUMER EST SWI",
                    accountLast4 = "4567",
                    balance = BigDecimal("98.5")
                )
            ),
            ParserTestCase(
                name = "Card authorisation, foreign merchant converted to JOD",
                message = "تفويض حركة على حسابكم 1234567 - 003 بقيمة 0.802 دينار اردني من " +
                    "ORACLE IRELAND           35300000000  IE الرصيد المتوفر 41.27 دينار اردني",
                sender = "JIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.802"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "ORACLE IRELAND",
                    balance = BigDecimal("41.27")
                )
            ),
            ParserTestCase(
                name = "CliQ transfer out, long recipient name kept whole",
                message = "حوالة كليك صادرة بمبلغ 0.75 دينار اردني من حسابكم 1234567 - 003 الى " +
                    "JOHNATHAN ALEXANDER MICHAEL SMITH JOHNSON الرصيد المتوفر 7.884 دينار اردني",
                sender = "JIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.75"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHNATHAN ALEXANDER MICHAEL SMITH JOHNSON",
                    accountLast4 = "4567",
                    balance = BigDecimal("7.884")
                )
            ),
            ParserTestCase(
                name = "CliQ transfer in",
                message = "حوالة كليك واردة بمبلغ 25.0 دينار اردني لحسابكم 1234567 - 003 من " +
                    "JANE DOE الرصيد المتوفر 125.3 دينار اردني",
                sender = "JIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.0"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "JANE DOE",
                    accountLast4 = "4567",
                    balance = BigDecimal("125.3")
                )
            ),
            ParserTestCase(
                name = "Electronic transfer in",
                message = "تحويل الكتروني لحسابكم 1234567 - 003 بقيمة 46.0 دينار اردني الرصيد المتوفر 223.069 دينار اردني",
                sender = "JIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("46.0"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "Electronic Transfer",
                    accountLast4 = "4567",
                    balance = BigDecimal("223.069")
                )
            ),
            ParserTestCase(
                name = "Electronic transfer out (double space before المتوفر)",
                message = "تحويل الكتروني من حسابكم 1234567 - 003 بقيمة 50.0 دينار اردني الرصيد  المتوفر 1707.061 دينار اردني",
                sender = "JIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.0"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "Electronic Transfer",
                    balance = BigDecimal("1707.061")
                )
            ),
            ParserTestCase(
                name = "Electronic bill payment",
                message = "تسديد الكتروني من حسابكم 1234567 - 003 بقيمة 7.506 دينار اردني الرصيد المتوفر 495.278 دينار اردني",
                sender = "JIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("7.506"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "Electronic Payment",
                    balance = BigDecimal("495.278")
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal (ليصبح رصيدكم balance)",
                message = "حركة صراف آلي على حسابكم 1234567 - 003 بقيمة 25.0 دينار اردني بتاريخ 01012026  21:23:57 " +
                    "على الصراف الالي عريفة مول 2 رقم التفويض 123456 ليصبح رصيدكم 333.221 دينار اردني",
                sender = "JIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25.0"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal",
                    accountLast4 = "4567",
                    balance = BigDecimal("333.221")
                )
            ),
            ParserTestCase(
                name = "Profit credit with overdrawn (negative) balance",
                message = "قيد ارباح بمبلغ 1.865 دينار اردني  لحسابكم 1234567 - 003 الرصيد المتوفر -150.624 دينار اردني",
                sender = "JIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1.865"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "Profit Credit",
                    balance = BigDecimal("-150.624")
                )
            ),
            ParserTestCase(
                name = "Declined purchase is ignored",
                message = "رفض حركة مشتريات بقيمة  2.7 دينار اردني من SOME SHOP               AMMAN        JO " +
                    "بسبب عدم كفاية الرصيد، الرصيد المتوفر -152.496 دينار اردني",
                sender = "JIB",
                shouldParse = false
            ),
            ParserTestCase(
                name = "CliQ payment request is ignored",
                message = "متعاملنا الكريم تم ورود طلب دفع عبر خدمة كليك من حسابكم بمبلغ 0.5 JOD لصالح JANE DOE " +
                    "يرجى التحقق قبل اتمام العملية",
                sender = "JIB",
                shouldParse = false
            ),
            ParserTestCase(
                name = "OTP is ignored",
                message = "كن حذرا من الاحتيال، من فضلك لا تشارك الرمز المؤقت مع الاخرين , لإتمام معاملتك يرجى ادخال الرمز المؤقت ( 123456 )",
                sender = "JIB",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "JIB" to true,
            "jib" to true,
            "JIBX" to false,
            "ARABIISLAMI" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Jordan Islamic Bank Parser")
    }
}
