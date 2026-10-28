package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class OrangeMoneyJordanParserTest {

    @TestFactory
    fun `orange money jordan parser handles common cases`(): List<DynamicTest> {
        val parser = OrangeMoneyJordanParser()

        val cases = listOf(
            ParserTestCase(
                name = "Purchase from wallet",
                message = "تمت عملية شراء من محفظتك 07XXXXXXXX بمبلغ 10 دينار بتاريخ 16/09/2026 الساعة 10:39:21 PM من OJO Payment Channel  بالرقم المرجعي OM20260916223921000000000000 رصيد المحفظة المتاح هو 0.000دينار أردني شكراً لاستخدامك Orange Money",
                sender = "OrangeMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "OJO Payment Channel",
                    reference = "OM20260916223921000000000000",
                    balance = BigDecimal("0.000"),
                    expectNullAccountLast4 = true,
                    isMobileWallet = true
                )
            ),
            ParserTestCase(
                name = "Transfer in from a bank (Arabic)",
                message = "تم استقبال حوالة مالية من TEST USER NAME  من مزود الخدمة:  البنك الإسلامي الأردني إلى محفظتك " +
                    "بمبلغ 10  دينار بتاريخ 16/09/2026 الساعة 10:37:12 PM بالرقم المرجعي JIBA1609103712000000000  " +
                    "رصيد المحفظة المتاح هو 10 دينار أردني  شكراً لاستخدامك Orange Money",
                sender = "OrangeMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "TEST USER NAME",
                    reference = "JIBA1609103712000000000",
                    balance = BigDecimal("10"),
                    isMobileWallet = true
                )
            ),
            ParserTestCase(
                name = "Transfer in from a bank (English)",
                message = "A money transfer transaction from TEST USER NAME  with Service Provider: Jordan Islamic Bank " +
                    "has been credited to your wallet 0770000000 with the amount 150 JD on 30/05/2026 at 08:30:14 PM " +
                    "with reference number JIBA3005083012000000000. Available Wallet Balance: 150 JD  " +
                    "Thank you for using Orange Money",
                sender = "OrangeMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150"),
                    currency = "JOD",
                    type = TransactionType.INCOME,
                    merchant = "TEST USER NAME",
                    reference = "JIBA3005083012000000000",
                    balance = BigDecimal("150"),
                    isMobileWallet = true
                )
            ),
            ParserTestCase(
                name = "Transfer out to another wallet",
                message = "تمت عملية التحويل المالي الى المحفظة TESTWALLET1 بمبلغ 150 دينار بتاريخ 03/06/2026 الساعة 08:28:03 PM بالرقم المرجعي OJM-PAY-20260603202803000000000000 رصيد المحفظة المتاح هو 25.500 دينار أردني شكراً لاستخدامك Orange Money",
                sender = "OrangeMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150"),
                    currency = "JOD",
                    type = TransactionType.EXPENSE,
                    merchant = "TESTWALLET1",
                    reference = "OJM-PAY-20260603202803000000000000",
                    balance = BigDecimal("25.500"),
                    isMobileWallet = true
                )
            ),
            ParserTestCase(
                name = "Purchase OTP is ignored",
                message = "كلمة السر لمرة واحدة الخاصة بعملية الشراء من OJO Payment Channel  بمبلغ 10دينار هي: 1234." +
                    "رسوم العملية هي: 0.000 دينار أردني",
                sender = "OrangeMoney",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Login OTP is ignored",
                message = "كلمة السر لمرة واحدة لتسجيل الدخول إلى محفظتك هي: 123456.",
                sender = "OrangeMoney",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promo is ignored",
                message = "اليوم وبس!\nاستمتع بـ 50% كاش باك لغاية 5 دنانير لما تشحن خطوط Orange من خلال محفظة " +
                    "Orange Money ، ما تفوّت العرض ! تطبق الشروط والأحكام https://example.com",
                sender = "OrangeMoney",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "OrangeMoney" to true,
            "Orange Money" to true,
            "Orange" to false,
            "Orange-bill" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Orange Money Jordan Parser")
    }
}
