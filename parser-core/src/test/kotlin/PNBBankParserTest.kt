package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.PNBBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest.dynamicTest
import java.math.BigDecimal

class PNBBankParserTest {

    @TestFactory
    fun `pnb parser handles transaction alerts`(): List<DynamicTest> {
        val parser = PNBBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "PNB Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit message with XX1234",
                message = "Ac XX1234 Debited with Rs.5000.00, 20-02-2026 07:47:16. Aval Bal Rs.27000.00 CR. Helpline 18001800/18002021-PNB",
                sender = "VM-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("27000.00")
                )
            ),
            ParserTestCase(
                name = "Debit message with card info",
                message = "A/c XX1234 debited with Rs.5000.00,21-11-2025 13:23:22 thru card XX9239  . Out of 5 free txn on PNB ATM, you utilized 1 txn. Chrgs applicable as per policy. Bal 27000.00 CR. If not done, fwd SMS to 9264192641 to block card/call 18001800/18002021-PNB",
                sender = "VM-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("27000.00"),
                    merchant = "Card XX9239"
                )
            ),
            ParserTestCase(
                name = "Debit message with VA sender",
                message = "Ac XX1234 Debited with Rs.5000.00, 16-02-2026 10:04:09. Aval Bal Rs.27000.00 CR. Helpline 18001800/18002021-PNB",
                sender = "VA-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("27000.00")
                )
            ),
            ParserTestCase(
                name = "Debit message with long account number",
                message = "Ac XXXXXXXX00341234 Debited with Rs.10000.00, 20-06-2025 08:18:35. Aval Bal Rs.27000.00 CR. Helpline 18001800/18002021-PNB",
                sender = "VK-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("10000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    balance = BigDecimal("27000.00")
                )
            ),
            ParserTestCase(
                name = "Auto-Pay activation message",
                message = "Dear Customer, auto pay facility has been successfully activated on your Punjab National Bank Card XX4356 for Rs. 75000.00, from Google Clouds. An initial amount of Rs. 2.00 has been debited from your account. Google Clouds can initiate subsequent transactions for a max amount upto Rs. 75000.00. You will receive notification with the transaction amount prior to any subsequent debits initiated by Google Clouds. Manage / cancel your Auto-Pay facility with ID RTy243262532g via https://www.sihub.in/man",
                sender = "VM-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4356",
                    merchant = "Google Clouds"
                )
            ),
            ParserTestCase(
                name = "IMPS transfer debit message",
                message = "Your a/c no XX1234 is debited for Rs 1000 on 01-01-25 12:00:00 and a/c XX456 credited (IMPS Ref no 123456789012) .If not done by you, pl. forward this SMS from registered mobile to 9264092640 to report unauthorized txn & block IBS/MBS. Download PNB ONE.-PNB",
                sender = "VM-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    reference = "123456789012",
                    merchant = "IMPS Transfer"
                )
            ),
            ParserTestCase(
                name = "IMPS transfer debit to different account",
                message = "Your a/c no XX847 is debited for Rs 3200 on 11-07-25 03:14:52 and a/c XX291 credited (IMPS Ref no 712845931206) .If not done by you, pl. forward this SMS from registered mobile to 9264092640 to report unauthorized txn & block IBS/MBS. Download PNB ONE.-PNB",
                sender = "VM-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3200"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "847",
                    reference = "712845931206",
                    merchant = "IMPS Transfer"
                )
            ),
            ParserTestCase(
                name = "IMPS transfer original message format",
                message = "Your a/c no XX847 is debited for Rs 6140 on 15-07-25 09:37:44 and a/c XX583 credited (IMPS Ref no 713092476185) .If not done by you, pl. forward this SMS from registered mobile to 9264092640 to report unauthorized txn & block IBS/MBS. Download PNB ONE.-PNB",
                sender = "VM-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("6140"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "847",
                    reference = "713092476185",
                    merchant = "IMPS Transfer"
                )
            ),
            ParserTestCase(
                name = "UPI credit message with Ref ID",
                message = "Ac XX7582 Credited with Rs.4750.00 11-07-2025 08:53:21 thru UPI . Aval Bal Rs.82431.67 CR. (UPI Ref ID:714628305917) Helpline 18001800/18002021-PNB",
                sender = "VM-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4750.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    accountLast4 = "7582",
                    reference = "714628305917",
                    balance = BigDecimal("82431.67"),
                    merchant = "UPI Transaction"
                )
            ),
            ParserTestCase(
                name = "UPI debit includes payee",
                message = "A/c X0000 debited INR 125.00 Dt 01-01-26 12:00:00 to Swiggy thru UPI:000000000001..Bal INR 9000.00-PNB",
                sender = "VM-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("125.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0000",
                    reference = "000000000001",
                    balance = BigDecimal("9000.00"),
                    merchant = "Swiggy"
                )
            ),
            ParserTestCase(
                name = "UPI debit strips legal suffix from payee",
                message = "A/c X0000 debited INR 250.00 Dt 02-01-26 12:00:00 to Swiggy Ltd thru UPI:000000000002.Bal INR 8750.00-PNB",
                sender = "VM-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0000",
                    reference = "000000000002",
                    balance = BigDecimal("8750.00"),
                    merchant = "Swiggy"
                )
            ),
            ParserTestCase(
                name = "Credit card UPI spend",
                message = "PNB Credit Card 1234 debited with Rs.270 [CODE:VG9918] at ombk.aaeh000000abcd@mbk on 04-10-2026 20:44 through UPI: 627768901810 Avl limit Rs. 48882.5. -PNB",
                sender = "VA-PNBCCD-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("270"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "ombk.aaeh000000abcd",
                    reference = "627768901810",
                    accountLast4 = "1234",
                    creditLimit = BigDecimal("48882.5"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Credit card spend at a multi-word merchant",
                message = "PNB Credit Card 1234 debited with Rs.1,499 [CODE:AB1234] at AMAZON INDIA on 05-10-2026 10:12 through UPI: 123456789012 Avl limit Rs. 47383.5. -PNB",
                sender = "VA-PNBCCD-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1499"),
                    currency = "INR",
                    type = TransactionType.CREDIT,
                    merchant = "AMAZON INDIA",
                    accountLast4 = "1234",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                // The account is debited, not the card — paying the card bill is an
                // account expense, not a card purchase.
                name = "Bank-account debit towards a PNB credit card payment stays an expense",
                message = "Ac XX1111 debited with Rs.270.00 on 05-10-2026 towards PNB credit card payment. Avl Bal Rs.5000.00 -PNB",
                sender = "VM-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("270.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "Credit card bill payment received is ignored (bank-side debit records it)",
                message = "Thank you Rs.668.79/- has been received as payment towards your PNB credit card  XX1234 via Online Payment. Your available credit limit is Rs.50000. - PNB",
                sender = "VA-PNBCCD-S",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "VA-PNBCCD-S" to true,
            "VM-PNBCCD-S" to true,
            "JD-PNBCCD-T" to true,
            "AD-PNBCCD" to true,
            "VA-PNBCCDX-S" to false,
            "VA-XPNBCCD-S" to false,
            "VM-PNBSMS-S" to true,
            "VA-PNBSMS-S" to true,
            "VK-PNBSMS-S" to true,
            "AX-PNBSMS-S" to true,
            "PNBBNK" to true,
            "UNKNOWN" to false
        )


        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "PNB Parser"
        )
    }

    @TestFactory
    fun `pnb mandate creation is treated as subscription not expense`(): List<DynamicTest> {
        val parser = PNBBankParser()
        val sender = "AX-PNBSMS-S"
        val message =
            "Your UPI-Mandate is successfully created towards Google for Rs.1500.00 from A/c No.XXXXXX4356. UMN:1d478c77808c410281f435rer5qwerty6@ybl-PNB"

        return listOf(
            dynamicTest("detect mandate notification") {
                assertEquals(true, parser.isUPIMandateNotification(message))
            },
            dynamicTest("do not parse mandate creation as transaction") {
                assertNull(parser.parse(message, sender, System.currentTimeMillis()))
            },
            dynamicTest("parse mandate subscription details") {
                val mandate = parser.parseUPIMandateSubscription(message)
                assertNotNull(mandate)
                assertEquals(BigDecimal("1500.00"), mandate?.amount)
                assertEquals("Google", mandate?.merchant)
                assertEquals("1d478c77808c410281f435rer5qwerty6@ybl-PNB", mandate?.umn)
                assertEquals("4356", mandate?.accountLast4)
            }
        )
    }
}
