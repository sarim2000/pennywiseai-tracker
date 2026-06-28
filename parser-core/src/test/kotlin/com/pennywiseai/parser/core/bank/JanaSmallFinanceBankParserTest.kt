package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class JanaSmallFinanceBankParserTest {

    @TestFactory
    fun `jana small finance bank parser handles common cases`(): List<DynamicTest> {
        val parser = JanaSmallFinanceBankParser()

        val cases = listOf(
            ParserTestCase(
                // Reported "No transaction detected": JM-JANABK-S was unclaimed by any parser.
                name = "UPI credit from NPCI BHIM",
                message = "Dear Customer, Your acct XX005 is credited with INR 8.00 on 13-Jun-26 from NPCI BHIM. UPI Ref no 103475395201 . JANA SFB",
                sender = "JM-JANABK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("8.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = "NPCI BHIM",
                    reference = "103475395201",
                    accountLast4 = "005"
                )
            ),
            ParserTestCase(
                name = "UPI debit",
                message = "Dear Customer, Your acct XX005 is debited with INR 250.00 on 14-Jun-26 to merchant@okaxis. UPI Ref no 103475395202 . JANA SFB",
                sender = "JM-JANABK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "merchant",
                    reference = "103475395202",
                    accountLast4 = "005"
                )
            )
        )

        val handleChecks = listOf(
            "JM-JANABK-S" to true,
            "JANABK" to true,
            "AD-JANASFB-S" to true,
            "HDFC" to false,
            "NSDLPB" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "Jana Small Finance Bank Parser")
    }
}
