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
    fun `slice parser classifies bank vs card debits correctly`(): List<DynamicTest> {
        val testCases = listOf(
            // --- Modern Slice Bank (post-2022 RBI PPI pivot): UPI / savings account ---
            ParserTestCase(
                name = "Modern Slice UPI transfer (sent to) should be EXPENSE, not CREDIT",
                message = "Sent Rs.500 to MERCHANT NAME (UPI transaction success). Sent from slice.",
                sender = "JK-SLICEIT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "MERCHANT NAME",
                    accountLast4 = null,
                    reference = null
                )
            ),
            ParserTestCase(
                name = "Modern Slice debited (bank account)",
                message = "Rs.250 debited from your slice account via UPI. Txn ID 1234567890.",
                sender = "AD-SLCEIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = null,
                    reference = null
                )
            ),
            ParserTestCase(
                name = "Modern Slice paid via UPI (no card context) is EXPENSE",
                message = "Rs.100 paid to MERCHANT via slice UPI. Txn ID 9876543210.",
                sender = "AD-SLCEIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = null,
                    reference = null
                )
            ),

            // --- Legacy Slice credit card (kept for regression: must remain CREDIT) ---
            ParserTestCase(
                name = "Legacy slice credit card transaction on amazon.in (CREDIT)",
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
                name = "Legacy slice credit card transaction with decimal amount (CREDIT)",
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
                name = "Slice card 'spent' wording with card context stays CREDIT",
                message = "Rs.350 spent on your slice credit card at MERCHANT. Available limit: Rs.10000.",
                sender = "AD-SLCEIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("350"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    accountLast4 = null,
                    reference = null
                )
            ),

            // --- Income side (regression: must remain INCOME) ---
            ParserTestCase(
                name = "Cashback credited to slice account (INCOME)",
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

            // --- Non-transaction / failure regressions ---
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
                name = "Date phrase should not be extracted as merchant (legacy card path)",
                message = "Your slice credit card transaction of RS. 50000 on Feb 15 is successful.",
                sender = "AD-SLCEIT-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = null,
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
