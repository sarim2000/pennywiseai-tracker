package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class GPayParserTest {

    private val parser = GPayParser()

    @TestFactory
    fun `gpay parser handles key paths`(): List<DynamicTest> {
        val testCases = listOf(
            ParserTestCase(
                name = "GPay incoming UPI payment with ₹ symbol",
                message = "DAD paid you ₹1.00",
                sender = "GPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "DAD"
                )
            ),
            ParserTestCase(
                name = "GPay incoming UPI payment with Rs. symbol",
                message = "Mom paid you Rs. 150.50",
                sender = "com.google.android.apps.nbu.paisa.user",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.50"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Mom"
                )
            ),
            ParserTestCase(
                name = "GPay incoming UPI payment with plain amount",
                message = "John paid you 500",
                sender = "GPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "John"
                )
            ),
            ParserTestCase(
                name = "Non-matching message should not parse",
                message = "You paid DAD ₹1.00",
                sender = "GPay",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = listOf(
                "GPay" to true,
                "com.google.android.apps.nbu.paisa.user" to true,
                "GPAY" to true,
                "HDFCBK" to false
            ),
            suiteName = "GPay Parser"
        )
    }
}
