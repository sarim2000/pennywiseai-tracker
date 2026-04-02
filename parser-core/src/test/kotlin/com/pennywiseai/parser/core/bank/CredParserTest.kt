package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class CredParserTest {

    private val parser = CredParser()

    @TestFactory
    fun `cred parser handles credit card payments`(): List<DynamicTest> {
        val testCases = listOf(
            ParserTestCase(
                name = "CRED payment to ICICI credit card",
                message = "Payment of Rs.50000 has been successfully credited towards your ICICI Bank Credit Card. Your payment was settled in 3 seconds - CRED",
                sender = "JK-CREDIN-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000"),
                    currency = "INR",
                    type = TransactionType.TRANSFER,
                    merchant = "ICICI Bank Credit Card",
                    accountLast4 = null,
                    reference = null
                )
            ),
            ParserTestCase(
                name = "CRED payment with decimal amount",
                message = "Payment of Rs.1234.56 has been successfully credited towards your HDFC Credit Card. Your payment was settled in 3 seconds - CRED",
                sender = "AX-CREDIN-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.56"),
                    currency = "INR",
                    type = TransactionType.TRANSFER,
                    merchant = "HDFC Credit Card",
                    accountLast4 = null,
                    reference = null
                )
            ),
            ParserTestCase(
                name = "Non-CRED message (should not parse)",
                message = "Your OTP for transaction is 123456. Do not share.",
                sender = "JK-CREDIN-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Failed CRED payment (should not parse)",
                message = "Payment of Rs.50000 could not be processed. Please try again later.",
                sender = "JK-CREDIN-S",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            "JK-CREDIN-S" to true,
            "AX-CREDIN-S" to true,
            "CREDIN" to true,
            "CRED" to true,
            "HDFCBK" to false,
            "VK-JTEDGE-S" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "CRED Parser"
        )
    }
}