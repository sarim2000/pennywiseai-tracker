package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class PhonePeWalletParserTest {
    @TestFactory
    fun `PhonePe wallet parser`(): List<DynamicTest> {
        val parser = PhonePeWalletParser()
        val testCases = listOf(
            ParserTestCase(
                name = "Wallet payment to a merchant (#1116)",
                message = "You've paid Rs.14.36 via PhonePe wallet for SAMPLE KIRANA STORE. Not you? Call us on 022-00000000. Remaining balance: Rs.0. To top-up click https://phone.pe/PHONPE/ws",
                sender = "JD-PHONPE-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("14.36"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "SAMPLE KIRANA STORE",
                    balance = BigDecimal("0")
                )
            ),
            ParserTestCase(
                name = "Merchant name with an income word stays an expense",
                message = "You've paid Rs.120.00 via PhonePe wallet for LOADED FRIES REFUND CORNER. Not you? Call us on 022-00000000. Remaining balance: Rs.30.00. To top-up click https://phone.pe/PHONPE/ws",
                sender = "JD-PHONPE-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("120.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = "LOADED FRIES REFUND CORNER",
                    balance = BigDecimal("30.00")
                )
            ),
            ParserTestCase(
                name = "Non-wallet PhonePe message is ignored (bank SMS records UPI)",
                message = "You've paid Rs.250.00 to SAMPLE SHOP via UPI from your bank account. Not you? Call us on 022-00000000.",
                sender = "JD-PHONPE-S",
                shouldParse = false
            )
        )
        val handleCases = listOf(
            "JD-PHONPE-S" to true,
            "VM-PHONPE" to true,
            "HDFCBK" to false
        )
        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "PhonePe Wallet Parser Tests")
    }
}
