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
