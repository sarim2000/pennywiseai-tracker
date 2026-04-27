package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SliceParserTest {

    private val parser = SliceParser()

    @TestFactory
    fun `slice parser handles credit card transactions`(): List<DynamicTest> {
        val testCases = listOf(
            ParserTestCase(
                name = "Slice credit card transaction on amazon.in",
                message = "Your slice credit card transaction of RS. 50000 on amazon.in is successful. If not you, call 08048329999 - slice",
                sender = "AD-SLCEIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "amazon.in",
                    accountLast4 = null,
                    reference = null
                )
            ),
            ParserTestCase(
                name = "Slice credit card transaction with different amount",
                message = "Your slice credit card transaction of RS. 1234.56 on flipkart.com is successful.",
                sender = "AX-SLICEIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.56"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "flipkart.com",
                    accountLast4 = null,
                    reference = null
                )
            ),
            ParserTestCase(
                name = "Slice UPI transfer (sent to)",
                message = "Sent Rs.500 to John Doe (UPI transaction success). Sent from slice.",
                sender = "JK-SLICEIT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "John Doe",
                    accountLast4 = null,
                    reference = null
                )
            ),
            ParserTestCase(
                name = "Slice credit (credited)",
                message = "Rs.1000 credited to your slice account as cashback.",
                sender = "AD-SLCEIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "Slice Credit",
                    accountLast4 = null,
                    reference = null
                )
            ),
            ParserTestCase(
                name = "Non-transaction message (OTP)",
                message = "Your OTP for slice transaction is 123456. Do not share.",
                sender = "AD-SLCEIT-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined transaction should NOT be parsed",
                message = "Your slice credit card transaction of RS. 50000 on amazon.in was declined.",
                sender = "AD-SLCEIT-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Failed transaction should NOT be parsed",
                message = "Your slice credit card transaction of RS. 1234.56 on flipkart.com failed.",
                sender = "AX-SLICEIT-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Unsuccessful transaction should NOT be parsed",
                message = "Your slice credit card transaction of RS. 50000 on amazon.in was unsuccessful.",
                sender = "AD-SLCEIT-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Date phrase should not be extracted as merchant",
                message = "Your slice credit card transaction of RS. 50000 on Feb 15 is successful.",
                sender = "AD-SLCEIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = null,  // Date phrase should be rejected, fallback to super.extractMerchant which may return "Slice"
                    accountLast4 = null,
                    reference = null
                )
            )
        )

        val handleCases = listOf(
            "AD-SLCEIT-S" to true,
            "AX-SLICEIT-S" to true,
            "JK-SLICEIT" to true,
            "SLICEIT" to true,
            "SLCEIT" to true,
            "HDFCBK" to false,
            "VK-JTEDGE-S" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Slice Parser"
        )
    }
}