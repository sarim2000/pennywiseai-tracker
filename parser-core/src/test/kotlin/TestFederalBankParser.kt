package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ExpectedTransaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import java.math.BigDecimal

class FederalBankParserTest {
    @Test
    fun `test Federal Bank Parser comprehensive test suite`() {
        val parser = FederalBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Federal Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // UPI Debit Transactions
            ParserTestCase(
                name = "UPI Debit to Individual VPA",
                message = "Rs 150.00 debited via UPI on 15-08-2024 10:30:25 to VPA john.doe123@okbank.Ref No 987654321098.Small txns?Use UPI Lite!-Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "john.doe123@okbank",
                    reference = "987654321098",
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "UPI Debit to Merchant VPA",
                message = "Rs 450.75 debited via UPI on 16-08-2024 14:22:10 to VPA swiggy.food@paytm.Ref No 876543210987.Small txns?Use UPI Lite!-Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("450.75"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Swiggy",
                    reference = "876543210987",
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "UPI Payment to Indigo via Paytm",
                message = "Rs 3500.00 debited via UPI on 20-08-2024 12:30:45 to VPA indigo.paytm@hdfcbank.Ref No 987654321099.Small txns?Use UPI Lite!-Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Indigo",
                    reference = "987654321099",
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "UPI Debit with Complex VPA",
                message = "Rs 1250.00 debited via UPI on 17-08-2024 09:15:45 to VPA merchant.store.98765@hdfcbank.Ref No 765432109876.Small txns?Use UPI Lite!-Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1250.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "merchant.store.98765@hdfcbank",
                    reference = "765432109876",
                    isFromCard = false
                )
            ),

            // IMPS Credit Transactions
            ParserTestCase(
                name = "IMPS Credit to Account",
                message = "Rs 3500.50 credited to your A/c XX4567 via IMPS on 18AUG2024 11:45:30 IMPS Ref no 654321098765 Bal:Rs 25000.75 -Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3500.50"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "IMPS Credit",
                    accountLast4 = "4567",
                    balance = BigDecimal("25000.75"),
                    reference = "654321098765",
                    isFromCard = false
                )
            ),

            // "You've received" Credit Transactions
            ParserTestCase(
                name = "You've Received from Individual",
                message = "John, you've received INR 10,509.09 in your Account XXXXXXXX1896. Woohoo! It was sent by TESTUSER on March 19, 2025. -Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10509.09"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "TESTUSER",
                    accountLast4 = "1896",
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "You've Received from Person",
                message = "Jane, you've received INR 50,000.00 in your Account XXXXXXXX1896. Woohoo! It was sent by SAMPLE PERSON on July 24, 2024. -Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50000.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "SAMPLE PERSON",
                    accountLast4 = "1896",
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "You've Received from 0000 - Bank Transfer",
                message = "John, you've received INR 17,179.95 in your Account XXXXXXXX1896. Woohoo! It was sent by 0000 on July 25, 2024. -Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("17179.95"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "Bank Transfer",
                    accountLast4 = "1896",
                    isFromCard = false
                )
            ),

            ParserTestCase(
                name = "IMPS Credit Large Amount",
                message = "Rs 15000.00 credited to your A/c XX7890 via IMPS on 19AUG2024 16:20:15 IMPS Ref no 543210987654 Bal:Rs 42500.80 -Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.INCOME,
                    merchant = "IMPS Credit",
                    accountLast4 = "7890",
                    balance = BigDecimal("42500.80"),
                    reference = "543210987654",
                    isFromCard = false
                )
            ),

            // Successful E-mandate Payments
            ParserTestCase(
                name = "Successful E-mandate Payment - Netflix",
                message = "Hi, payment of INR 199.00 for Netflix via e-mandate ID: NX789XYZABC on Federal Bank Debit Card 3456 is processed successfully. To manage, visit: https://www.sihub.in/managesi/federal T&CA - Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("199.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Netflix via e-mandate ID: NX789XYZABC",
                    accountLast4 = "3456",
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Successful E-mandate Payment - Spotify",
                message = "Hi, payment of INR 119.00 for Spotify via e-mandate ID: SP456DEF123 on Federal Bank Debit Card 7890 is processed successfully. To manage, visit: https://www.sihub.in/managesi/federal T&CA - Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("119.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "Spotify via e-mandate ID: SP456DEF123",
                    accountLast4 = "7890",
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Successful E-mandate Payment - Insurance",
                message = "Hi, payment of INR 2500.00 for LifeInsurance via e-mandate ID: LI789GHI456 on Federal Bank Debit Card 1234 is processed successfully. To manage, visit: https://www.sihub.in/managesi/federal T&CA - Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "LifeInsurance via e-mandate ID: LI789GHI456",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),

            // Card Transactions (testing detectIsCard)
            ParserTestCase(
                name = "Credit Card Transaction - Amazon",
                message = "INR 1200.00 spent on your credit card ending with 5678 at AMAZON on 09-05-2025 15:30:15. Available limit Rs.38000.00 -Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1200.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.CREDIT,
                    merchant = "AMAZON",
                    accountLast4 = "5678",
                    creditLimit = BigDecimal("38000.00"),
                    isFromCard = true
                )
            ),

            ParserTestCase(
                name = "Debit Card Transaction - Masked Card",
                message = "Rs 1500.00 debited via card XX**3456 at MERCHANT on 14-05-2025 15:30:45. Current Bal: Rs.250.75 -Federal Bank",
                sender = "AD-FEDBNK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "INR",
                    type = com.pennywiseai.parser.core.TransactionType.EXPENSE,
                    merchant = "MERCHANT",
                    accountLast4 = "3456",
                    balance = BigDecimal("250.75"),
                    isFromCard = true
                )
            ),

            // Messages that should not parse
            ParserTestCase(
                name = "Failed Transaction Should Not Parse",
                message = "Hi, txn of Rs. 1500.00 using card XX**3456 failed due to insufficient funds. Current Bal: Rs.250.75. Call 18004251199 if txn not initiated by you -Federal Bank",
                sender = "AD-FEDBNK",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Declined E-mandate Should Not Parse",
                message = "Hi, payment of INR 199.00 via e-mandate declined for ID: NX789XYZABC on Federal Bank Debit Card 3456. To manage, visit: https://www.sihub.in/managesi/federal T&CA - Federal Bank",
                sender = "AD-FEDBNK",
                shouldParse = false
            ),

            ParserTestCase(
                name = "Account Notification Should Not Parse",
                message = "Hi, your Federal Bank Savings Account is currently debit frozen due to incomplete Video KYC. Please complete the VKYC by 30/09/2024 to avoid account closure.",
                sender = "AD-FEDBNK",
                shouldParse = false
            ),

            ParserTestCase(
                name = "OTP Message Should Not Parse",
                message = "Dear Customer, your FedMobile registration has been initiated. If not initiated by you, please call 18004201199. Please do not share your card details/OTP/CVV to anyone -Federal Bank",
                sender = "AD-FEDBNK",
                shouldParse = false
            )
        )
        
        val handleCases: List<Pair<String, Boolean>> = listOf(
            "AD-FEDBNK" to true,
            "JM-FEDBNK" to true,
            "AX-FEDBNK-S" to true,
            "ADCBAlert" to false,
            "SBI" to false,
            "" to false
        )

        val result = ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Federal Bank Parser Tests")
        ParserTestUtils.printTestSummary(
            totalTests = result.totalTests,
            passedTests = result.passedTests,
            failedTests = result.failedTests,
            failureDetails = result.failureDetails
        )
    }


    @Test
    fun `test Federal Bank mandate parsing`() {
        val parser = FederalBankParser()

        // Test mandate creation message (should return mandate info, not transaction)
        val mandateCreationMessage = "Dear Customer, You have successfully created a mandate on Netflix India for a MONTHLY frequency starting from 05-09-2024 for a maximum amount of Rs 199.00 Mandate Ref No- abc123def456@fifederal - Federal Bank"
        val result = parser.parse(mandateCreationMessage, "AX-FEDBNK-S", System.currentTimeMillis())

        // Mandate creation messages should not be parsed as regular transactions
        assertNull(result, "Mandate creation messages should not be parsed as regular transactions")

        // Test payment due message (should return mandate info, not transaction)
        val paymentDueMessage = "Hi, payment due for Netflix,INR 199.00 on 05/09/2024 will be processed using Federal Bank Debit Card 3456. To cancel, visit https://www.sihub.in/managesi/federal T&CA - Federal Bank"
        val paymentDueResult = parser.parse(paymentDueMessage, "AX-FEDBNK-S", System.currentTimeMillis())

        // Payment due messages should not be parsed as regular transactions
        assertNull(paymentDueResult, "Payment due messages should not be parsed as regular transactions")
    }

    @Test
    fun `test Federal Bank mandate API`() {
        val parser = FederalBankParser()

        // Test mandate creation API
        val mandateCreationMessage = "Dear Customer, You have successfully created a mandate on Netflix India for a MONTHLY frequency starting from 05-09-2024 for a maximum amount of Rs 199.00 Mandate Ref No- abc123def456@fifederal - Federal Bank"
        val mandateResult = parser.parseEMandateSubscription(mandateCreationMessage)

        assertNotNull(mandateResult, "Should parse mandate creation via API")
        assertEquals(BigDecimal("199.00"), mandateResult?.amount)
        assertEquals("05-09-2024", mandateResult?.nextDeductionDate)
        assertEquals("Netflix India", mandateResult?.merchant)
        assertEquals("abc123def456@fifederal", mandateResult?.umn)

        // Test payment due API
        val paymentDueMessage = "Hi, payment due for Netflix,INR 199.00 on 05/09/2024 will be processed using Federal Bank Debit Card 3456. To cancel, visit https://www.sihub.in/managesi/federal T&CA - Federal Bank"
        val paymentDueResult = parser.parseFutureDebit(paymentDueMessage)

        assertNotNull(paymentDueResult, "Should parse payment due via API")
        assertEquals(BigDecimal("199.00"), paymentDueResult?.amount)
        assertEquals("05/09/24", paymentDueResult?.nextDeductionDate)
        assertEquals("Netflix", paymentDueResult?.merchant)
        assertNull(paymentDueResult?.umn, "Payment due notifications don't have UMN")
    }


}