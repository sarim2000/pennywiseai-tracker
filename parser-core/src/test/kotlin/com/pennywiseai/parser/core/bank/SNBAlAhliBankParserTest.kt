package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SNBAlAhliBankParserTest {

    private val parser = SNBAlAhliBankParser()

    @TestFactory
    fun `snb alahli parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "POS purchase with Samsung Pay (Mada)",
                message = "شراء نقاط بيع SamsungPay\nبـSAR 12.34\nمن SYNTHETIC STORE\nمدى *0007\nفي 00:00 01/01/30",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC STORE",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "OTP message is ignored",
                message = "رمز التحقق الخاص بك هو <CODE>. لا تشاركه مع أحد.",
                sender = "SNB-AlAhli",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Amount-first SAR layout is parsed",
                message = "شراء انترنت\nبـ23.45 SAR\nمن SYNTHETIC WALLET\nمدى-ابل *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC WALLET",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Refund takes precedence over purchase wording",
                message = "استرجاع شراء\nبـ34.56 SAR\nمن SYNTHETIC RETURN STORE\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC RETURN STORE",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Refund for earlier declined purchase is accepted",
                message = "استرجاع عملية شراء سابقة مرفوضة\nبـ12.34 SAR\nمن SYNTHETIC MERCHANT\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC MERCHANT",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Emergency cash correction returns funds",
                message = "تصحيح سحب نقدي\nمبلغ 45.67 SAR\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Arabic PIN authentication with amount is ignored",
                message = "الرقم السري لتأكيد شراء عبر الانترنت: <CODE>\nمبلغ 56.78 SAR\nبطاقة *0007",
                sender = "SNB-AlAhli",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined Arabic purchase is ignored",
                message = "عملية مرفوضة\nشراء-POS\nبـ67.89 SAR\nرصيد غير كافي",
                sender = "SNB-AlAhli",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Incidental SAR note is not treated as amount",
                message = "شراء\nملاحظة: SAR 99.99 هو الحد المتاح\nمن SYNTHETIC INFORMATION",
                sender = "SNB-AlAhli",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves snb alahli`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Saudi National Bank",
                sender = "SNB-AlAhli",
                currency = "SAR",
                message = "شراء نقاط بيع SamsungPay\nبـSAR 12.34\nمن SYNTHETIC STORE\nمدى *0007\nفي 00:00 01/01/30",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
