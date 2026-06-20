package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class NSDLPaymentsBankParserTest {

    @TestFactory
    fun `nsdl payments bank parser handles common cases`(): List<DynamicTest> {
        val parser = NSDLPaymentsBankParser()

        val cases = listOf(
            ParserTestCase(
                name = "UPI debit",
                message = "A/c XX1234 debited Rs 1.00 on 20-Jun-26 for linked myupihandle@oksbi. UPI Ref 122345526539 - NSDLPB",
                sender = "AD-NSDLPB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "myupihandle",
                    reference = "122345526539",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "UPI credit",
                message = "A/c no XX1234 is credited for Rs.100.00 on 20-Jun-26 (UPI Ref No 617109835321) - NSDLPB",
                sender = "AD-NSDLPB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = null,
                    reference = "617109835321",
                    accountLast4 = "1234"
                )
            ),
            ParserTestCase(
                name = "UPI debit with dotted VPA handle (not truncated at the dot)",
                message = "A/c XX1234 debited Rs 50.00 on 20-Jun-26 for linked business.name@oksbi. UPI Ref 122345526540 - NSDLPB",
                sender = "VM-NSDLPB-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "business.name",
                    reference = "122345526540",
                    accountLast4 = "1234"
                )
            )
        )

        val handleChecks = listOf(
            "NSDLPB" to true,
            "AD-NSDLPB-S" to true,
            "VM-NSDLPB-S" to true,
            "JIOPBS" to false,
            "HDFC" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleChecks, "NSDL Payments Bank Parser")
    }
}
