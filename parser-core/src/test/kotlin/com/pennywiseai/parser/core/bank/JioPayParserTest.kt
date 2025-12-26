package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class JioPayParserTest {

    private val parser = JioPayParser()

    @TestFactory
    fun `jiopay parser basic flows`(): List<DynamicTest> {
        val testCases = listOf(
            ParserTestCase(
                name = "JioPay recharge successful",
                message = "Recharge Successful for Jio Number : 9876543210. Rs. 249.00 paid. Transaction ID : BR000CAUBYON",
                sender = "JIOPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("249.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "Jio Recharge - 9876****",
                    reference = "BR000CAUBYON"
                )
            ),
            ParserTestCase(
                name = "JioPay payment successful to merchant",
                message = "Payment successful to ZOMATO. Rs. 500.00 paid. Transaction ID : BR000CAUBYON",
                sender = "JIOPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "ZOMATO",
                    reference = "BR000CAUBYON"
                )
            ),
            ParserTestCase(
                name = "JioPay bill payment",
                message = "Bill Payment of Rs. 1,234.56 for Electricity bill successful. Rs. 1,234.56 paid. Transaction ID : BR000CAUBYON",
                sender = "JIOPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.56"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "Electricity Bill",
                    reference = "BR000CAUBYON"
                )
            )
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = listOf("JIOPAY" to true, "HDFCBK" to false),
            suiteName = "JioPay Parser"
        )
    }

    @Test
    fun `bill notification should be rejected`() {
        val billMessage = """Your 14-Dec-2025 e-bill for Jio Number 7593988738 has been sent to Techexplorers2020@gmail.com.
        Bill Summary :
        Bill period : 30-Nov-2025 to 13-Dec-2025
        Total Amount payable : Rs. 242.38
        Payment due date: 23-DEC-2025
        To view and download your detailed bill, click https://www.jio.com/dl/my_bills
        It?s easy to understand your Jio Postpaid Mobile Bill. Click http://tiny.jio.com/readpstbill to know more."""

        // Verify parser returns null for bill notifications (not actual transactions)
        val result = parser.parse(billMessage, "JD-JIOPAY-S", System.currentTimeMillis())
        Assertions.assertNull(result, "Parser should return null for bill notifications")
    }

    @TestFactory
    fun `factory resolves jiopay`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "JioPay",
                sender = "JIOPAY",
                currency = "INR",
                message = "Recharge Successful for Jio Number : 9876543210. Rs. 249.00 paid",
                expected = ExpectedTransaction(
                    amount = BigDecimal("249.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests - JioPay")
    }
}
