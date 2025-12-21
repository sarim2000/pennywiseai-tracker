package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

class JioPayParserTest {

    private val parser = JioPayParser()

    @Test
    fun `jiopay parser handles key paths`() {
        ParserTestUtils.printTestHeader(
            parserName = "JioPay Wallet",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val cases = listOf(
            ParserTestCase(
                name = "Jio Recharge Successful",
                message = "Your Recharge Successful for Jio Number : 9876543210. Plan Name : 249.00. Transaction ID : BR000CAUBYON. Download MyJio app",
                sender = "JA-JIOPAY-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("249.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "Jio Recharge - 9876****",
                    reference = "BR000CAUBYON"
                )
            )
        )

        ParserTestUtils.runTestSuite(parser, cases)
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
        assertNull(result, "Parser should return null for bill notifications")
    }

    @Test
    fun `sender pattern matching`() {
        // Should handle various sender patterns
        assertTrue(parser.canHandle("JA-JIOPAY-S"))
        assertTrue(parser.canHandle("JD-JIOPAY-S"))
        assertTrue(parser.canHandle("JE-JIOPAY-S"))
        assertTrue(parser.canHandle("XX-JIOPAY-S"))
        assertTrue(parser.canHandle("YY-JIOPAY-T"))
        assertTrue(parser.canHandle("JM-JIOPAY"))

        // Should not handle random senders
        assertFalse(parser.canHandle("HDFC"))
        assertFalse(parser.canHandle("RANDOM"))
    }

    @Test
    fun `factory resolves jiopay`() {
        val cases = listOf(
            SimpleTestCase(
                bankName = "JioPay",
                sender = "JD-JIOPAY-S",
                currency = "INR",
                message = "Your Recharge Successful for Jio Number : 9876543210. Plan Name : 249.00. Transaction ID : BR000CAUBYON.",
                expected = ExpectedTransaction(
                    amount = BigDecimal("249.00"),
                    currency = "INR",
                    type = TransactionType.CREDIT
                ),
                shouldHandle = true
            )
        )

        ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
