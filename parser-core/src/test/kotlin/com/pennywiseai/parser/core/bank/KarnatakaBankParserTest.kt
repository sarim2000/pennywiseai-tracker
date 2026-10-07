package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class KarnatakaBankParserTest {

    @TestFactory
    fun `karnataka bank upi transfers`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "UPI transfer names the payee, not \"UPI Transaction\" (#868)",
                message = "Your a/c XX1111 debited for Rs.500.00 on 06-10-26 trf to Test Distributor. UPI:6*********39.For dispute SMS BLOCK 1111 to 1800000000 -KarnatakaBank",
                sender = "VA-KBLBNK-T",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "Test Distributor",
                    accountLast4 = "1111"
                )
            ),
            ParserTestCase(
                name = "Upper-case payee",
                message = "Your a/c XX1111 debited for Rs.20.00 on 06-10-26 trf to SK FAST FOOD CORNER. UPI:6*********28.For dispute SMS BLOCK 1111 to 1800000000 -KarnatakaBank",
                sender = "BG-KBLBNK-T",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "SK FAST FOOD CORNER",
                    accountLast4 = "1111"
                )
            ),
            ParserTestCase(
                name = "Payee with initials keeps its periods",
                message = "Your a/c XX1111 debited for Rs.75.00 on 06-10-26 trf to M. K. STORES. UPI:6*********11.For dispute SMS BLOCK 1111 to 1800000000 -KarnatakaBank",
                sender = "VA-KBLBNK-T",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "M. K. STORES"
                )
            )
        )
        val handleCases = listOf("VA-KBLBNK-T" to true, "BG-KBLBNK-T" to true, "HDFCBK" to false)
        return ParserTestUtils.runTestSuite(KarnatakaBankParser(), cases, handleCases, "Karnataka Bank")
    }

    @org.junit.jupiter.api.Test
    fun `masked UPI ref is not stored as a reference`() {
        val msg = "Your a/c XX1111 debited for Rs.20.00 on 06-10-26 trf to SK FAST FOOD CORNER. UPI:6*********28.For dispute SMS BLOCK 1111 to 1800000000 -KarnatakaBank"
        org.junit.jupiter.api.Assertions.assertNull(KarnatakaBankParser().parse(msg, "VA-KBLBNK-T", 0L)?.reference)
    }
}
