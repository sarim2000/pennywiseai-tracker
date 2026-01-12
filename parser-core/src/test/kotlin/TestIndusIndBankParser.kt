import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.IndusIndBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.*
import java.math.BigDecimal

class IndusIndBankParserTest {

    @TestFactory
    fun `indusind parser basic flows`(): List<DynamicTest> {
        val parser = IndusIndBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "IndusInd Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit with merchant and balance",
                message = "Rs. 1,234.00 debited from A/c XX1234 at ZOMATO Ref 998877. Avl Bal: Rs 10,000.00",
                sender = "VM-INDUSB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "ZOMATO",
                    accountLast4 = "1234",
                    balance = BigDecimal("10000.00"),
                    reference = "998877"
                )
            ),
            ParserTestCase(
                name = "UPI debit with RRN and VPA",
                message = "A/c *XX1234 debited by Rs 1234.00 towards xxxx.yyyy@icici. RRN: 510048508040. Not You? call 18602677777- IndusInd Bank.",
                sender = "AD-INDUSIND-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1234.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "xxxx.yyyy",
                    accountLast4 = "1234",
                    reference = "510048508040"
                )
            ),
            ParserTestCase(
                name = "UPI credit with RRN and VPA",
                message = "A/C *XX1234 credited by Rs 25000.00 from xxxx.yyyy@ybl. RRN:510048508040. Avl Bal:105502.12. Not you? Call 18602677777 - IndusInd bank.",
                sender = "AD-INDUSIND-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "xxxx.yyyy",
                    accountLast4 = "1234",
                    reference = "510048508040",
                    balance = BigDecimal("105502.12")
                )
            ),
            ParserTestCase(
                name = "Deposit interest - should not parse",
                message = "Net interest INR 248.07 paid on your IndusInd Deposit No 300***123456 on 17/09/25. Call 18602677777 for assistance - IndusInd Bank",
                sender = "AD-INDUSIND-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "IMPS debit - should parse",
                message = "Your IndusInd Account 20XXXXX1234 has been debited for INR 6440 towards IMPS/12345678901. Call 18602677777 to report issue-IndusInd Bank.",
                sender = "AD-INDUSIND-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6440"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "IMPS",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "Balance-only - should not parse",
                message = "Your A/C 2134***12345 has Avl BAL of INR 1,234.56 as on 05/10/25 04:10 AM. Download IndusMobile from PlayStore - IndusInd Bank",
                sender = "AD-INDUSIND-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "ACH debit with Ref and trailing Bal",
                message = "IndusInd A/C  Debited; INR 4,500.00 Ref-ACH DR INW PAY/0000WD2CEFDT2Z58B2202320321456/Grow.Bal INR 141,999.93.Dispute-Call 18602677777-IndusInd Bank.",
                sender = "AD-INDUSIND-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Grow",
                    balance = BigDecimal("141999.93"),
                    isFromCard = false,
                    accountLast4 = null
                )
            ),
            ParserTestCase(
                name = "Debit card purchase masked account - only last4",
                message = "INR 1,101.53 debited from your A/C 201***123456 towards Debit Card Purchase. Avl BAL INR 400.20 - Not you? Call 18602677777 to report issue - IndusInd Bank.",
                sender = "AD-INDUSIND-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1101.53"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "3456",
                    balance = BigDecimal("400.20"),
                    isFromCard = false
                )
            ),
            ParserTestCase(
                name = "UPI credit from various sender IDs - JK",
                message = "A/C *XX0000 credited by Rs 300.00 from abcd@upiid. RRN:123456789098. Avl Bal:00.00. Not you? Call 18602677777 - IndusInd bank",
                sender = "JK-INDUSB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "abcd",
                    accountLast4 = "0000",
                    reference = "123456789098",
                    balance = BigDecimal("0.00")
                )
            ),
            ParserTestCase(
                name = "UPI credit from various sender IDs - JX",
                message = "A/C *XX0000 credited by Rs 890.00 from abcd@upiid. RRN:123456789098. Avl Bal:00.00. Not you? Call 18602677777 - IndusInd bank",
                sender = "JX-INDUSB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("890.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "abcd",
                    accountLast4 = "0000",
                    reference = "123456789098",
                    balance = BigDecimal("0.00")
                )
            ),
            ParserTestCase(
                name = "UPI credit from various sender IDs - JD",
                message = "A/C *XX0000 credited by Rs 890.00 from abcd@upiid. RRN:123456789098. Avl Bal:00.00. Not you? Call 18602677777 - IndusInd bank",
                sender = "JD-INDUSB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("890.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "abcd",
                    accountLast4 = "0000",
                    reference = "123456789098",
                    balance = BigDecimal("0.00")
                )
            ),
            ParserTestCase(
                name = "Deposit interest from JM sender - should not parse",
                message = "Net interest INR 96.61 paid on your IndusInd Deposit No 371***060020 on 30/06/25. Call 18602677777 for assistance - IndusInd Bank",
                sender = "JM-INDUSB-S",
                shouldParse = false
            ),
            ParserTestCase(
                name = "IMPS credit",
                message = "Your IndusInd Account 15XXXXX0000 has been credited for INR 116.56 towards IMPS/500200000290. Call 18602677777 to report issue-IndusInd Bank",
                sender = "JM-INDUSB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("116.56"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "IMPS",
                    accountLast4 = "0000"
                )
            ),
            ParserTestCase(
                name = "IMPS credit with from account/merchant pattern",
                message = "Your account XXXXXXX1234 is credited by Rs.54321 on 07-11-25 received from account XXXXXXX4321/MADMONEY (IMPS Ref no. 123456789). Call 18602677777 to report issue-IndusInd Bank",
                sender = "VM-INDUSB-T",
                expected = ExpectedTransaction(
                    amount = BigDecimal("54321"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "MADMONEY",
                    accountLast4 = "1234",
                    reference = "123456789"
                )
            )
        )

        val handleChecks = listOf(
            "AD-INDUSB-S" to true,
            "VM-INDUSB-T" to true,
            "VM-INDUSIND-S" to true,
            "JK-INDUSB-S" to true,
            "JX-INDUSB-S" to true,
            "JD-INDUSB-S" to true,
            "JM-INDUSB-S" to true,
            "INDUSB" to true,
            "INDUSIND" to true,
            "AX-HDFC-S" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "IndusInd Bank Parser"
        )


    }

    @Test
    fun `indusind balance update parsing`() {
        val parser = IndusIndBankParser()

        val message =
            "Your A/C 2134***12345 has Avl BAL of INR 1,234.56 as on 05/10/25 04:10 AM. Download IndusMobile from PlayStore - IndusInd Bank"

        // Should be detected as a balance update (not a transaction)
        org.junit.jupiter.api.Assertions.assertTrue(parser.isBalanceUpdateNotification(message))

        val info = parser.parseBalanceUpdate(message)
        org.junit.jupiter.api.Assertions.assertNotNull(info)
        info!!

        // Bank name matches IndusInd
        org.junit.jupiter.api.Assertions.assertEquals("IndusInd Bank", info.bankName)

        // Account last4 should be last 4 of 12345 -> 2345
        org.junit.jupiter.api.Assertions.assertEquals("2345", info.accountLast4)

        // Balance parsed
        org.junit.jupiter.api.Assertions.assertEquals(java.math.BigDecimal("1234.56"), info.balance)

        // Date parsed (dd/MM/yy hh:mm a)
        val expectedDate = java.time.LocalDateTime.of(2025, 10, 5, 4, 10)
        org.junit.jupiter.api.Assertions.assertEquals(expectedDate, info.asOfDate)
    }

    @TestFactory
    fun `factory resolves indusind`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "IndusInd Bank",
                sender = "AD-INDUSB-S",
                currency = "INR",
                message = "Rs. 250.00 debited from A/c XX9876 at SWIGGY Ref TXN123",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests - IndusInd")

    }

}
