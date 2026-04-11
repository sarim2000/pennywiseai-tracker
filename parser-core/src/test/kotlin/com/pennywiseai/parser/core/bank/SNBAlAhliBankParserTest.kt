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
                message = "شراء نقاط بيع SamsungPay\nبـSAR 19.45\nمن filwah al\nمدى *2342\nفي 07:53 03/04/26",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("19.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "filwah al",
                    accountLast4 = "2342",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "OTP message is ignored",
                message = "رمز التحقق الخاص بك هو 123456. لا تشاركه مع أحد.",
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
                message = "شراء نقاط بيع SamsungPay\nبـSAR 19.45\nمن filwah al\nمدى *2342\nفي 07:53 03/04/26",
                expected = ExpectedTransaction(
                    amount = BigDecimal("19.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
