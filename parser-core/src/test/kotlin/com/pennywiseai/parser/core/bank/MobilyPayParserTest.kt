package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class MobilyPayParserTest {
    @TestFactory
    fun transactions() = ParserTestUtils.runTestSuite(
        MobilyPayParser(),
        listOf(
            ParserTestCase("Purchase", "Card Purchase\nAmount: SAR 12.50\nAt: Example Store", "MOBILYPAY",
                ExpectedTransaction(BigDecimal("12.50"), "SAR", TransactionType.EXPENSE, merchant = "Example Store")),
            ParserTestCase("Top-up balance belongs to wallet",
                "Wallet Top-up\nAmount: SAR 25.00\nFrom Card: 1234*MADA\nCurrent Balance: SAR 50.00", "MOBILYPAY",
                ExpectedTransaction(BigDecimal("25.00"), "SAR", TransactionType.TRANSFER,
                    balance = BigDecimal("50.00"), isFromCard = false, isMobileWallet = true, expectNullAccountLast4 = true)),
            ParserTestCase("Reject verification code", "OTP: 000000 for Card Purchase\nAmount: SAR 12.50", "MOBILYPAY", shouldParse = false)
        )
    )
}
