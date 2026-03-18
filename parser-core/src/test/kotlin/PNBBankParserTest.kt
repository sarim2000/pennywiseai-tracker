package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.PNBBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
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
                name = "UPI-Mandate creation message",
                message = "Your UPI-Mandate is successfully created towards Google for Rs.1500.00 from A/c No.XXXXXX4356. UMN:1d478c77808c410281f435rer5qwerty6@ybl-PNB",
                sender = "AX-PNBSMS-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4356",
                    merchant = "Google"
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
            )
        )

        val handleChecks = listOf(
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
}
