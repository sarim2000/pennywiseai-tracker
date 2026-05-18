package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class CashfreeParserTest {

    @TestFactory
    fun `cashfree parser handles representative messages`(): List<DynamicTest> {
        val parser = CashfreeParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Outgoing payment confirmation",
                message = "Payment INR 50.00 (ID:5448114171) confirmed for order #735571_428_1777162938185 on AuraGold.\nPowered by Cashfree",
                sender = "JX-CSHfre-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "AuraGold",
                    reference = "5448114171"
                )
            ),
            ParserTestCase(
                name = "OTP message should be rejected",
                message = "OTP 123456 is your one time password. Do not share.",
                sender = "JX-CSHfre-S",
                shouldParse = false
            ),
            ParserTestCase(
                // Cashfree-sender message that lacks the "confirmed for order"
                // phrasing — must fall through isTransactionMessage to the
                // base class and ultimately return null (no amount extractable).
                name = "Cashfree sender without confirmation phrasing returns null",
                message = "Payment options are available on our portal. Powered by Cashfree",
                sender = "JX-CSHfre-S",
                shouldParse = false
            ),
            ParserTestCase(
                // Multi-word merchant containing a space — the merchant regex
                // (`[^.\n\r]+?` stopping at `.`) must capture the full name
                // and not bleed into the trailing "Powered by Cashfree" line.
                name = "Multi-word merchant is captured up to the period",
                message = "Payment INR 299.00 (ID:9988776655) confirmed for order #order_001 on Sun Direct.\nPowered by Cashfree",
                sender = "VK-CSHfre-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("299.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Sun Direct",
                    reference = "9988776655"
                )
            )
        )

        val handleCases = listOf(
            "JX-CSHfre-S" to true,
            "VK-CSHfre-T" to true,
            "JD-CSHfre-S" to true,
            "CSHFRE" to true,
            "HDFCBK" to false,
            "JD-HDFCBK-S" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Cashfree Parser Suite"
        )
    }
}
